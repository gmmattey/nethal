package com.nethal.feature.devices.domain

/**
 * Um dispositivo detectado na LAN local pelo scan multi-fonte (issue #105). Dado bruto (MAC
 * completo, IP), sem mascaramento — mesma decisão de ADR 0001
 * (`docs/architecture/adr/0001-fronteira-sanitizacao-telemetria.md`): sanitização é
 * responsabilidade exclusiva de um futuro Telemetry Collector, aplicada só na fronteira de
 * exportação, nunca no modelo local consumido pela tela.
 *
 * Diferente de `ConnectedClientList`/`ConnectedClient` (`core/model/ConnectedClient.kt`,
 * capability `READ_CONNECTED_CLIENTS`): aquele é reportado pelo próprio gateway autenticado —
 * uma Driver Family específica lendo a tabela DHCP/ARP de UM equipamento-alvo já pareado. Este
 * é produzido por um scan ativo da LAN inteira, feito pelo próprio aparelho Android, sem
 * autenticação em nenhum equipamento — por isso vive em `:feature:devices`, não no vocabulário
 * de `CapabilityId` nem passa pelo Capability Engine/Driver Family (ver decisão de arquitetura
 * no PR desta issue).
 */
data class LanDevice(
    val ipAddress: String,
    val macAddress: String?,
    val hostname: String?,
    val vendor: String?,
    val deviceType: LanDeviceType,
)

/**
 * Classificação básica por heurística (`LanDeviceClassifier`) — pista visual, não afirmação
 * precisa. Nenhum dispositivo garante 100% de acerto sem consultar o próprio fabricante/SO.
 */
enum class LanDeviceType {
    GATEWAY,
    COMPUTER,
    MOBILE,
    TV_MEDIA,
    IOT,
    PRINTER,
    UNKNOWN,
}
