package com.nethal.lab.telemetry

import com.nethal.core.telemetry.ProductEvent
import com.nethal.core.telemetry.TelemetryProductEventName
import com.nethal.lab.BuildConfig

/**
 * Fábrica dos 4 eventos de produto da issue #97 Lane B — único ponto do módulo `app` que sabe montar
 * um [ProductEvent] completo (contexto de `BuildConfig`), para nenhum call site (observer de tela,
 * `NetHalApplication`, handler de crash) duplicar a leitura de `BuildConfig.VERSION_NAME`/
 * `VERSION_CODE`/`DEBUG`.
 *
 * [environment] segue a mesma convenção documentada em `HttpTelemetryCollectorTest` (Lane A):
 * `"production"` para build de release, `"development"` para build de debug — não existe build type
 * de staging hoje (`app/build.gradle.kts` só declara `release`; debug é o variant implícito do AGP).
 */
private val environment: String get() = if (BuildConfig.DEBUG) "development" else "production"

fun screenViewEvent(screenName: String): ProductEvent = ProductEvent(
    name = TelemetryProductEventName.SCREEN_VIEW,
    screenName = screenName,
    appVersion = BuildConfig.VERSION_NAME,
    environment = environment,
    versionCode = BuildConfig.VERSION_CODE,
)

fun sessionStartEvent(): ProductEvent = ProductEvent(
    name = TelemetryProductEventName.SESSION_START,
    appVersion = BuildConfig.VERSION_NAME,
    environment = environment,
    versionCode = BuildConfig.VERSION_CODE,
)

fun sessionEndEvent(): ProductEvent = ProductEvent(
    name = TelemetryProductEventName.SESSION_END,
    appVersion = BuildConfig.VERSION_NAME,
    environment = environment,
    versionCode = BuildConfig.VERSION_CODE,
)

/**
 * [exceptionClassName] deve ser sempre `throwable::class.simpleName` (nunca `.message`, que pode
 * carregar dado do equipamento/rede — ex.: uma `IOException` com o IP do roteador no texto). Ver
 * `TelemetryUncaughtExceptionHandler`, único chamador.
 */
fun featureCrashEvent(exceptionClassName: String): ProductEvent = ProductEvent(
    name = TelemetryProductEventName.FEATURE_CRASH,
    errorType = exceptionClassName,
    appVersion = BuildConfig.VERSION_NAME,
    environment = environment,
    versionCode = BuildConfig.VERSION_CODE,
)
