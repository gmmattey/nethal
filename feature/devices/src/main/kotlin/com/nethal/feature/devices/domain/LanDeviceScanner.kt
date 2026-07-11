package com.nethal.feature.devices.domain

/** Fonte de dados para a tela "Dispositivos" (issue #105). Implementação real é Android-específica
 * (`DefaultLanDeviceScanner`, em `:feature:devices` mesmo — ver decisão de arquitetura no PR);
 * este contrato existe para a `DevicesViewModel` ficar testável com um fake, sem Android. */
interface LanDeviceScanner {
    suspend fun scan(): LanScanResult
}

sealed interface LanScanResult {
    data class Success(val devices: List<LanDevice>) : LanScanResult

    /** Sem Wi-Fi ativo, sem sub-rede legível, ou sub-rede fora de RFC 1918 — nada para escanear
     * com segurança. Tratado como estado vazio explícito pela UI (critério de aceite da #86). */
    data object NoNetwork : LanScanResult
}
