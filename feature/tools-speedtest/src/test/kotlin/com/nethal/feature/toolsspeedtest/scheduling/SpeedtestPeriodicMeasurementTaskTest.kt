package com.nethal.feature.toolsspeedtest.scheduling

import com.nethal.core.model.SpeedtestBottleneck
import com.nethal.core.model.SpeedtestMode
import com.nethal.core.model.SpeedtestPhase
import com.nethal.core.model.SpeedtestQualityDiagnosis
import com.nethal.core.model.SpeedtestResult
import com.nethal.core.model.SpeedtestRunState
import com.nethal.core.model.SpeedtestSnapshot
import com.nethal.core.model.SpeedtestUsageVerdict
import com.nethal.core.model.BufferbloatSeverity
import com.nethal.core.scheduling.MeasurementOutcome
import com.nethal.core.scheduling.MeasurementSourceType
import com.nethal.feature.toolsspeedtest.FakeSpeedtestEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedtestPeriodicMeasurementTaskTest {

    private val diagnosis = SpeedtestQualityDiagnosis(
        streamingVerdict = SpeedtestUsageVerdict.GOOD,
        gamingVerdict = SpeedtestUsageVerdict.GOOD,
        videoCallVerdict = SpeedtestUsageVerdict.GOOD,
        primaryBottleneck = SpeedtestBottleneck.NONE,
    )

    @Test
    fun `source e SPEEDTEST e id e estavel`() {
        val task = SpeedtestPeriodicMeasurementTask(FakeSpeedtestEngine(SpeedtestSnapshot(runState = SpeedtestRunState.IDLE)))

        assertEquals(MeasurementSourceType.SPEEDTEST, task.source)
        assertEquals(SpeedtestPeriodicMeasurementTask.TASK_ID, task.id)
    }

    @Test
    fun `roda sempre no modo FAST (mais barato em dados e bateria)`() = runTest {
        val engine = FakeSpeedtestEngine(SpeedtestSnapshot(runState = SpeedtestRunState.IDLE))
        engine.snapshotToPublishOnRun = SpeedtestSnapshot(
            runState = SpeedtestRunState.DONE,
            phase = SpeedtestPhase.DONE,
            result = buildResult(),
        )
        val task = SpeedtestPeriodicMeasurementTask(engine)

        task.measure()

        assertEquals(SpeedtestMode.FAST, engine.lastModeRequested)
        assertEquals(1, engine.runCallCount)
    }

    @Test
    fun `snapshot DONE com resultado vira MeasurementOutcome Success com os campos do SpeedtestResult`() = runTest {
        val engine = FakeSpeedtestEngine(SpeedtestSnapshot(runState = SpeedtestRunState.IDLE))
        engine.snapshotToPublishOnRun = SpeedtestSnapshot(
            runState = SpeedtestRunState.DONE,
            phase = SpeedtestPhase.DONE,
            result = buildResult(downloadMbps = 150.0, uploadMbps = 40.0, latencyMs = 12.0, jitterMs = 1.5),
        )
        val task = SpeedtestPeriodicMeasurementTask(engine)

        val outcome = task.measure()

        assertTrue(outcome is MeasurementOutcome.Success)
        outcome as MeasurementOutcome.Success
        assertEquals(150.0, outcome.downloadMbps)
        assertEquals(40.0, outcome.uploadMbps)
        assertEquals(12.0, outcome.latencyMs)
        assertEquals(1.5, outcome.jitterMs)
    }

    @Test
    fun `snapshot ERROR vira MeasurementOutcome Failure com a mensagem do motor`() = runTest {
        val engine = FakeSpeedtestEngine(SpeedtestSnapshot(runState = SpeedtestRunState.IDLE))
        engine.snapshotToPublishOnRun = SpeedtestSnapshot(
            runState = SpeedtestRunState.ERROR,
            errorMessage = "Falha ao conectar em speed.cloudflare.com",
        )
        val task = SpeedtestPeriodicMeasurementTask(engine)

        val outcome = task.measure()

        assertTrue(outcome is MeasurementOutcome.Failure)
        assertEquals("Falha ao conectar em speed.cloudflare.com", (outcome as MeasurementOutcome.Failure).reason)
    }

    private fun buildResult(
        downloadMbps: Double = 100.0,
        uploadMbps: Double = 20.0,
        latencyMs: Double = 15.0,
        jitterMs: Double = 1.0,
    ) = SpeedtestResult(
        timestampEpochMs = 0L,
        mode = SpeedtestMode.FAST,
        downloadMbps = downloadMbps,
        uploadMbps = uploadMbps,
        latencyMs = latencyMs,
        jitterMs = jitterMs,
        packetLossPercent = 0.0,
        bufferbloatMs = 5.0,
        bufferbloatSeverity = BufferbloatSeverity.NONE,
        peakDownloadMbps = downloadMbps,
        peakUploadMbps = uploadMbps,
        qualityDiagnosis = diagnosis,
    )
}
