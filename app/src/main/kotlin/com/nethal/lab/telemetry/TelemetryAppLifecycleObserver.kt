package com.nethal.lab.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nethal.core.telemetry.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `session_start`/`session_end` (issue #97, Lane B) — decisão técnica: "sessão de uso" é o app **em
 * primeiro plano**, não a vida do processo. `Application.onCreate`/`onTerminate` foi descartado de
 * propósito: `onTerminate()` só existe no emulador, nunca é chamado num device real (documentado pela
 * própria API do Android), então ligar `session_end` nele deixaria o evento praticamente nunca sendo
 * enviado. Contador de `Activity` iniciada/parada (mesma técnica clássica de detecção de
 * foreground/background, sem depender de `androidx.lifecycle:lifecycle-process`, dependência nova que
 * este módulo não tinha): 0→1 dispara `session_start`, 1→0 dispara `session_end`. Cobre tanto o
 * primeiro launch quanto voltar de background (ex.: usuário troca de app e volta) — mais correto que
 * "um session_start por processo", já que o processo pode sobreviver a vários ciclos de
 * foreground/background.
 *
 * Registrado uma única vez em `NetHalApplication.onCreate`. [scope] é o mesmo `applicationScope` do
 * `NetHalApplication` (vida do processo, não de uma Activity) — fire-and-forget, nunca bloqueia a
 * transição de tela.
 */
class TelemetryAppLifecycleObserver(
    private val telemetryCollector: TelemetryCollector,
    private val scope: CoroutineScope,
) : Application.ActivityLifecycleCallbacks {

    private var startedActivityCount = 0

    override fun onActivityStarted(activity: Activity) {
        if (startedActivityCount == 0) {
            scope.launch { telemetryCollector.sendProductEvent(sessionStartEvent()) }
        }
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0) {
            scope.launch { telemetryCollector.sendProductEvent(sessionEndEvent()) }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * `feature_crash` (issue #97, Lane B) — envolve o `Thread.UncaughtExceptionHandler` default sem
 * substituí-lo: dispara o evento de telemetria (best-effort, fire-and-forget — o processo pode morrer
 * antes da requisição HTTP terminar, limitação conhecida e aceitável para MVP, mesmo trade-off de
 * qualquer coletor fire-and-forget diante de crash) e **sempre** repassa para o handler anterior, para
 * o crash continuar se propagando normalmente (nunca mascarar um crash real, inclusive o comportamento
 * padrão do Android de encerrar o processo).
 *
 * [exceptionClassName] nunca é `.message`/stacktrace — só `Throwable::class.simpleName`, ver
 * [featureCrashEvent].
 */
fun installTelemetryUncaughtExceptionHandler(
    telemetryCollector: TelemetryCollector,
    scope: CoroutineScope,
) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        scope.launch {
            telemetryCollector.sendProductEvent(
                featureCrashEvent(exceptionClassName = throwable::class.simpleName ?: "Unknown"),
            )
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}
