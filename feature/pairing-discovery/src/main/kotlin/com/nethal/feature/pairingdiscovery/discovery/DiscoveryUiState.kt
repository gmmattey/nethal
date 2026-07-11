package com.nethal.feature.pairingdiscovery.discovery

import com.nethal.core.model.NetworkTarget

/**
 * Estado da Tela 2/2b/2c (spec §11). `Scanning` é a Tela 2 propriamente dita; `Failed` é a
 * Tela 2b; `MultipleCandidates` é a Tela 2c; `SingleCandidateReady` segue direto ao próximo
 * passo (fingerprint, fora do escopo desta entrega) sem tela intermediária.
 */
sealed interface DiscoveryUiState {

    data object AwaitingLocationPermission : DiscoveryUiState

    data object Scanning : DiscoveryUiState

    data class Failed(
        val reason: FailureReason,
    ) : DiscoveryUiState

    data class MultipleCandidates(
        val devices: List<NetworkTarget>,
        val possibleDoubleNat: Boolean,
        val networkInfo: DiscoveredNetworkInfo,
    ) : DiscoveryUiState

    data class SingleCandidateReady(
        val device: NetworkTarget,
        val networkInfo: DiscoveredNetworkInfo,
    ) : DiscoveryUiState
}

/**
 * Dados de rede do próprio aparelho exibidos na Tela 2 (spec §11): IP local, gateway, DNS.
 * Populado a partir do `NetworkEnvironment` lido pela plataforma — nunca hardcoded.
 */
data class DiscoveredNetworkInfo(
    val localIp: String?,
    val gatewayIp: String?,
    val dnsServers: List<String>,
)

/**
 * Motivo provável de falha (Tela 2b). `LOCATION_PERMISSION_DENIED` não está na spec original
 * de motivos de falha de rede, mas é uma causa real e distinta neste app — sem a permissão,
 * o Android nem devolve os dados de rede necessários para tentar descobrir o gateway.
 */
enum class FailureReason {
    NO_GATEWAY_FOUND,
    NOT_ON_WIFI,
    LOCATION_PERMISSION_DENIED,
}
