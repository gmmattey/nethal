package com.nethal.core.driver.tplink

/**
 * Modelos de dados do driver TP-Link Archer C20 (profile `tplink_archer_c20_v1` do catálogo),
 * protocolo real confirmado por captura via DevTools contra unidade física do Luiz (2026-07-06,
 * ver SIG-337/SIG-338) — substitui o modelo especulativo anterior (MD5+POST/JSON, REFUTED).
 *
 * O protocolo real é um dispatcher único `/cgi` com blocos de texto por seção
 * (`[NOME_SECAO#...]indice,qtd`), não JSON por endpoint — ver `TplinkC20ResponseParser` para o
 * formato completo. Capabilities candidatas confirmadas nesta rodada: READ_DEVICE_INFO,
 * READ_WIFI_STATUS (parcial: só name/SSID), READ_CONNECTED_CLIENTS. READ_WAN_STATUS e
 * READ_FIRMWARE permanecem UNKNOWN — seção não capturada ainda.
 */

/** Identificação do equipamento, seções IGD_DEV_INFO + ETH_SWITCH + SYS_MODE. */
data class TplinkC20DeviceInfo(
    val modelName: String,
    val description: String,
    val isFactoryDefault: Boolean?,
    val numberOfVirtualPorts: Int?,
    val mode: String?,
)

/** Uma linha de rádio Wi-Fi, seção LAN_WLAN. Só `name`/`SSID` confirmados até agora — capability parcial. */
data class TplinkC20WifiStatus(
    val name: String,
    val ssid: String,
)

/** Um cliente DHCP conectado, seção LAN_HOST_ENTRY. MAC sempre mascarado antes de sair do parser. */
data class TplinkC20ConnectedClient(
    val hostname: String,
    val ipAddress: String,
    val macAddressMasked: String,
    val leaseTimeRemainingSeconds: Long?,
)

/** Snapshot agregado das capabilities confirmadas, retornado pelo orquestrador do driver. */
data class TplinkC20DriverSnapshot(
    val deviceInfo: TplinkC20DeviceInfo?,
    val wifi: List<TplinkC20WifiStatus>,
    val connectedClients: List<TplinkC20ConnectedClient>,
)
