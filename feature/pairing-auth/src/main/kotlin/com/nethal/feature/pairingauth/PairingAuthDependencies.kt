package com.nethal.feature.pairingauth

import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry

/**
 * Dependências que o composition root (`:app`) injeta no grafo (composição manual, sem framework
 * de DI — mesmo padrão de `PairingDiscoveryDependencies`/`NetHalViewModelFactory`, ADR 0002). O
 * `HttpTransport` usado para autenticar **não** entra aqui: é construído internamente por este
 * módulo (`buildPairingAuthHttpTransport()`, `PairingAuthGraph.kt`) porque sua configuração
 * (headers/Referer/Content-Type do handshake stok/luci do TP-Link) é conhecimento de autenticação,
 * não algo que o composition root precise decidir.
 */
data class PairingAuthDependencies(
    val driverRegistry: DriverRegistry,
    val driverFamilyRegistry: DriverFamilyRegistry,
)
