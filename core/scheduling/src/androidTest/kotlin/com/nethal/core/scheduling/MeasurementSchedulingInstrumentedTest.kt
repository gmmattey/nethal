package com.nethal.core.scheduling

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.nethal.core.scheduling.db.SqliteMeasurementSampleRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Teste instrumentado — cobre os 3 critérios de aceite de #112 fim a fim usando a infra de teste
 * oficial do WorkManager (`work-testing`): o agendamento dispara a cada intervalo configurado
 * ([testDriver.setPeriodDelayMet] simula ticks sem esperar 30min de verdade), o resultado persiste
 * (via [SqliteMeasurementSampleRepository] real, banco físico do dispositivo/emulador de teste), e
 * uma falha de rede numa rodada não quebra a próxima (task fake alterna falha/sucesso).
 *
 * Não roda em `./gradlew :core:scheduling:test` (JVM) — precisa de instrumentação real
 * (device/emulador), mesma limitação de qualquer `androidTest` deste repo.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementSchedulingInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repository: MeasurementSampleRepository
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        repository = SqliteMeasurementSampleRepository(context)

        val callCount = AtomicInteger(0)
        val fakeTask = object : PeriodicMeasurementTask {
            override val id = FAKE_TASK_ID
            override val source = MeasurementSourceType.SPEEDTEST
            override suspend fun measure(): MeasurementOutcome {
                val call = callCount.incrementAndGet()
                // 1ª rodada falha (simula rede indisponível), 2ª e 3ª têm sucesso — cobre
                // "falha numa rodada não quebra a próxima".
                return if (call == 1) {
                    MeasurementOutcome.Failure("rede indisponível (simulada)")
                } else {
                    MeasurementOutcome.Success(downloadMbps = 80.0 + call, latencyMs = 20.0)
                }
            }
        }
        val registry = MeasurementTaskRegistry(listOf(fakeTask))

        val configuration = Configuration.Builder()
            .setWorkerFactory(MeasurementWorkerFactory(repository, registry))
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun agendamentoDisparaACadaIntervaloEPersisteResultadoSemQuebrarNaFalha() {
        val scheduler = PeriodicMeasurementScheduler(workManager)
        scheduler.schedule(FAKE_TASK_ID, intervalMinutes = 30, requireUnmeteredNetwork = false)

        val workInfo = awaitUniqueWorkInfo()
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!

        // Simula 3 intervalos de 30min se passando, sem esperar de verdade.
        testDriver.setPeriodDelayMet(workInfo.id)
        testDriver.setPeriodDelayMet(workInfo.id)
        testDriver.setPeriodDelayMet(workInfo.id)

        val samples = kotlinx.coroutines.runBlocking { repository.recent(MeasurementSourceType.SPEEDTEST, limit = 10) }

        assertEquals(3, samples.size)
        assertTrue("a rodada com falha simulada deve estar persistida", samples.any { !it.success })
        assertTrue("as rodadas com sucesso simulado devem estar persistidas", samples.count { it.success } == 2)

        // O trabalho periódico continua registrado (não foi cancelado pela falha da 1ª rodada).
        val stillScheduled = awaitUniqueWorkInfo()
        assertTrue(stillScheduled.state == WorkInfo.State.ENQUEUED || stillScheduled.state == WorkInfo.State.RUNNING)
    }

    private fun awaitUniqueWorkInfo(): WorkInfo {
        val future = workManager.getWorkInfosForUniqueWork(PeriodicMeasurementScheduler.uniqueWorkName(FAKE_TASK_ID))
        return future.get(5, TimeUnit.SECONDS).first()
    }

    private companion object {
        const val FAKE_TASK_ID = "fake_speedtest_periodic"
    }
}
