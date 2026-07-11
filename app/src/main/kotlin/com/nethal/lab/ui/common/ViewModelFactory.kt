package com.nethal.lab.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.consent.ConsentRepository
import com.nethal.core.model.NetworkTarget
import com.nethal.core.protocol.http.DefaultHttpTransport
import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportConfig
import com.nethal.feature.settings.SettingsViewModel
import com.nethal.lab.ui.authentication.AuthenticationViewModel
import com.nethal.lab.ui.capabilities.CapabilitiesViewModel
import com.nethal.lab.ui.capabilities.CapabilityItem
import com.nethal.lab.ui.onboarding.BetaOptInViewModel
import com.nethal.lab.ui.onboarding.WelcomeViewModel
import com.nethal.lab.ui.report.ReportViewModel
import java.net.URL

/**
 * Factory única do app. Sem DI framework nesta entrega — o grafo de dependências ainda é
 * pequeno o suficiente para não justificar Hilt/Koin. `DiscoveryViewModel`/
 * `EquipmentDetectedViewModel` saíram daqui na extração de `:feature:pairing-discovery` (ADR
 * 0002) — o módulo monta essas duas por conta própria a partir de `PairingDiscoveryDependencies`
 * (ver `NetHalApplication`/`MainActivity`), nunca através desta factory.
 */
class NetHalViewModelFactory(
    private val consentRepository: ConsentRepository,
    private val driverRegistry: DriverRegistry,
    private val driverFamilyRegistry: DriverFamilyRegistry,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            WelcomeViewModel::class.java -> WelcomeViewModel(consentRepository) as T
            BetaOptInViewModel::class.java -> BetaOptInViewModel(consentRepository) as T
            SettingsViewModel::class.java -> SettingsViewModel(consentRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }

    /**
     * `AuthenticationViewModel` recebe o `NetworkTarget` da Tela 2/2c e o `matchedProfileId`
     * produzido pelo Fingerprint Engine na Tela 3 por instância (varia a cada navegação) —
     * factory dedicada em vez de sobrecarregar `create()` genérico, que não tem como receber
     * esses parâmetros por `Class<T>`.
     */
    fun forAuthentication(target: NetworkTarget, matchedProfileId: String?): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AuthenticationViewModel::class.java) {
                    "Unknown ViewModel class: $modelClass"
                }
                return AuthenticationViewModel(
                    target = target,
                    matchedProfileId = matchedProfileId,
                    driverRegistry = driverRegistry,
                    driverFamilyRegistry = driverFamilyRegistry,
                    httpTransport = buildAuthenticationHttpTransport(),
                ) as T
            }
        }

    /**
     * `CapabilitiesViewModel` recebe a sessão (`CapabilityEngine`) já autenticada, entregue pela
     * Tela 5 via `AuthenticationViewModel.captureAuthenticatedSession()` — nunca constrói uma
     * sessão nova aqui. `capabilityEngine` pode ser `null` (sessão perdida entre telas); a própria
     * `CapabilitiesViewModel` trata isso como `CapabilitiesUiState.SessionUnavailable`, não esta
     * factory.
     */
    fun forCapabilities(capabilityEngine: CapabilityEngine?, matchedProfileId: String?): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == CapabilitiesViewModel::class.java) {
                    "Unknown ViewModel class: $modelClass"
                }
                return CapabilitiesViewModel(
                    capabilityEngine = capabilityEngine,
                    matchedProfileId = matchedProfileId,
                    driverRegistry = driverRegistry,
                ) as T
            }
        }

    /**
     * `ReportViewModel` recebe os itens já lidos pela Tela 4 (Capabilities) — não lê nada do
     * equipamento nem depende de nenhuma sessão ativa (a Tela 4 já encerrou a sessão antes de
     * chegar aqui, ver `CapabilitiesViewModel.closeSession`).
     */
    fun forReport(matchedProfileId: String?, items: List<CapabilityItem>): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ReportViewModel::class.java) {
                    "Unknown ViewModel class: $modelClass"
                }
                return ReportViewModel(
                    matchedProfileId = matchedProfileId,
                    driverRegistry = driverRegistry,
                    items = items,
                ) as T
            }
        }
}

/**
 * Transporte HTTP único usado pela Tela 5 para qualquer `DriverFamily` resolvida pelo
 * `DriverFamilyRegistry`. Só `tplink-stok-luci-driver` tem `authenticate()` real hoje (as demais
 * Driver Families usam o default de `DriverFamily.authenticate()`, que nunca chama a rede — ver
 * KDoc de `AuthenticationViewModel`), então os parâmetros abaixo espelham exatamente a
 * configuração já validada contra hardware físico em
 * `core/tooling/ManualCheckRunner.kt` (`runTplinkC6Stok`) — headers, `Content-Type` e `Referer`
 * exigidos pelo firmware real do TP-Link Archer C6, plataforma stok/luci.
 *
 * Quando outra Driver Family ganhar `authenticate()` real com requisitos de transporte diferentes,
 * esta função vai precisar decidir por `driverFamilyId` (nunca por fabricante) — decisão de
 * arquitetura para revisar com Rafael quando isso acontecer, não antecipada aqui.
 */
private fun buildAuthenticationHttpTransport(): HttpTransport = DefaultHttpTransport(
    HttpTransportConfig(
        connectTimeoutMillis = 10_000,
        getReadTimeoutMillis = 20_000,
        postReadTimeoutMillis = 20_000,
        getAcceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        postAcceptHeader = "application/json, text/javascript, */*; q=0.01",
        postContentType = "application/x-www-form-urlencoded; charset=UTF-8",
        extraPostHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
        postRefererProvider = { url ->
            val base = URL(url)
            val root = "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}"
            if (url.contains("/cgi-bin/luci/;stok=/login")) {
                "$root/webpages/login.html"
            } else {
                "$root/webpages/index.html"
            }
        },
        followRedirectsManually = false,
    ),
)
