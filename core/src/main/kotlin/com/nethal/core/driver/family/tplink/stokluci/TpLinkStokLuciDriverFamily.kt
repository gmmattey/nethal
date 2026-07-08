package com.nethal.core.driver.family.tplink.stokluci

import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.catalog.DriverFamily
import com.nethal.core.catalog.DriverFamilyFactory
import com.nethal.core.discovery.PrivateIpRanges
import com.nethal.core.driver.NetworkFailureReason
import com.nethal.core.driver.RetryOutcome
import com.nethal.core.driver.classifyNetworkFailure
import com.nethal.core.driver.executeWithRetry
import com.nethal.core.model.CapabilityId
import com.nethal.core.protocol.http.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Motivo de falha da Driver Family após esgotar as tentativas — mesmo vocabulário genérico de `TpLinkLegacyCgiFailureReason`. */
internal enum class TpLinkStokLuciFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    COMMUNICATION_ERROR,
}

internal sealed interface TpLinkStokLuciLoginOutcome {
    data class Success(val session: TpLinkStokLuciSession) : TpLinkStokLuciLoginOutcome
    data class Failure(val reason: TpLinkStokLuciFailureReason, val message: String) : TpLinkStokLuciLoginOutcome
}

internal sealed interface TpLinkStokLuciStatusOutcome {
    data class Success(val rawBody: String) : TpLinkStokLuciStatusOutcome
    data class Failure(val reason: TpLinkStokLuciFailureReason, val message: String) : TpLinkStokLuciStatusOutcome
}

internal sealed interface TpLinkStokLuciSnapshotOutcome {
    data class Success(val snapshot: TpLinkStokLuciSnapshot) : TpLinkStokLuciSnapshotOutcome
    data class Failure(val reason: TpLinkStokLuciFailureReason, val message: String) : TpLinkStokLuciSnapshotOutcome
}

/**
 * Driver Family da plataforma `tplink-stok-luci` (`platformId`/`driverFamilyId` do catálogo —
 * profile `tplink_archer_c6_stok_v1`, ver `docs/architecture/hal-layering-model.md` §9.1).
 *
 * Implementa o login (envelope `sign`/`data`, ver [TpLinkStokLuciAuthenticationClient] para o
 * protocolo real confirmado por evidência ao vivo), uma leitura autenticada bruta de status geral
 * ([readStatusRaw]) e, desde esta rodada, o parsing estruturado desse payload para o vocabulário de
 * capabilities do NetHAL ([readSnapshot], via [TpLinkStokLuciStatusParser]) — cobre
 * `READ_WIFI_STATUS`, `READ_LAN_STATUS`, `READ_WAN_STATUS` e `READ_CONNECTED_CLIENTS`
 * ([SUPPORTED_CAPABILITIES]). `READ_DEVICE_INFO`/`READ_FIRMWARE` continuam fora de escopo: o
 * payload de `admin/status?form=all` capturado até aqui não trouxe evidência de campo de
 * modelo/firmware.
 *
 * O ciclo de correções desta Driver Family passou por várias rodadas de `INVALID_CREDENTIALS`
 * (HTTP 403) até convergir com a captura real do hardware do Luiz (Archer C6 v2.0, firmware
 * `1.1.10 Build 20230830 rel.69433(5553)`). O estado atual já foi validado ao vivo para:
 * login bem-sucedido + leitura autenticada bruta de `admin/status?form=all`. O que AINDA não
 * existe é o gerenciamento de sessão do Capability Engine — [readCapability] (a implementação de
 * [DriverFamily], sem parâmetro de credencial) continua honestamente indisponível nesta etapa,
 * mesmo com o parser estruturado já existindo; quem precisa do dado real hoje chama [readSnapshot]
 * diretamente, mesmo padrão de [login]/[readStatusRaw]. Ver `ManualCheckRunner` para o comando de
 * teste manual e `docs/drivers/live-evidence/tplink-archer-c6-stok-v1.json` para a evidência de
 * hardware.
 *
 * Guarda de SSRF obrigatória (RFC 1918), mesma classe de risco de toda Driver Family do NetHAL —
 * falha rápido, sem tentar login, quando o host não é IP privado.
 *
 * Retry conservador (no máximo 2 tentativas), mesmo raciocínio do `tplink-legacy-cgi`: sem
 * handshake de sessão persistente confiável para colidir entre tentativas, mas retentativa
 * agressiva não ajuda contra falha persistente de credencial/rede.
 */
internal class TpLinkStokLuciDriverFamily(
    private val host: String,
    private val config: TpLinkStokLuciDriverConfig,
    private val transport: HttpTransport,
    private val maxAttempts: Int = 2,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) : DriverFamily {

    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "TpLinkStokLuciDriverFamily só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    /**
     * Executa só o login (passos 1-5 do handshake) e devolve a sessão resultante. Não persiste a
     * sessão entre chamadas — cada chamada a este método faz um login novo, mesmo desenho de
     * `readSnapshot` do `tplink-legacy-cgi` (sem Capability Engine gerenciando sessão ainda).
     */
    suspend fun login(username: String, password: String): TpLinkStokLuciLoginOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkStokLuciLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS -> TpLinkStokLuciFailureReason.INVALID_CREDENTIALS
                    TpLinkStokLuciLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            val client = TpLinkStokLuciAuthenticationClient(host, transport)
            client.login(username, password)
        }

        when (outcome) {
            is RetryOutcome.Success -> TpLinkStokLuciLoginOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkStokLuciLoginOutcome.Failure(outcome.reason, outcome.error.message ?: outcome.error.toString())
        }
    }

    /**
     * Login seguido de uma única leitura autenticada real de `config.statusReadPath`, usando o
     * mesmo envelope AES + `sign` confirmado no hardware. Devolve o corpo bruto já decifrado
     * (JSON), sem parsing estruturado: a coleta ponta a ponta deste endpoint já foi validada contra
     * o equipamento real, mas ainda não existe mapeamento de campos para capabilities.
     */
    suspend fun readStatusRaw(username: String, password: String): TpLinkStokLuciStatusOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkStokLuciLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS -> TpLinkStokLuciFailureReason.INVALID_CREDENTIALS
                    TpLinkStokLuciLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                    TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                    -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            val client = TpLinkStokLuciAuthenticationClient(host, transport)
            client.login(username, password)
            client.fetchAuthenticated(config.statusReadPath, config.statusReadQuery)
        }

        when (outcome) {
            is RetryOutcome.Success -> TpLinkStokLuciStatusOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkStokLuciStatusOutcome.Failure(outcome.reason, outcome.error.message ?: outcome.error.toString())
        }
    }

    /**
     * [readStatusRaw] seguido do parsing estruturado ([TpLinkStokLuciStatusParser.parseSnapshot])
     * do corpo bruto de `admin/status?form=all` para o vocabulário de capabilities do NetHAL —
     * cobre [SUPPORTED_CAPABILITIES]. Mesma orquestração (login novo a cada chamada, sem sessão
     * persistida) e mesmo motivo: sem Capability Engine gerenciando sessão ainda, este é o ponto de
     * entrada real usado por `ManualCheckRunner`/testes até essa peça existir.
     */
    suspend fun readSnapshot(username: String, password: String): TpLinkStokLuciSnapshotOutcome =
        when (val outcome = readStatusRaw(username, password)) {
            is TpLinkStokLuciStatusOutcome.Success ->
                TpLinkStokLuciSnapshotOutcome.Success(TpLinkStokLuciStatusParser.parseSnapshot(outcome.rawBody))
            is TpLinkStokLuciStatusOutcome.Failure ->
                TpLinkStokLuciSnapshotOutcome.Failure(outcome.reason, outcome.message)
        }

    /**
     * Implementação de [DriverFamily.readCapability] — mesmo desenho honesto do
     * `TpLinkLegacyCgiDriverFamily` (que já introduziu [SUPPORTED_CAPABILITIES] para distinguir
     * "esta Driver Family nunca vai suportar $id" de "suporta, mas exige sessão que esta assinatura
     * não recebe"). O parser estruturado ([TpLinkStokLuciStatusParser]) já existe e cobre
     * [SUPPORTED_CAPABILITIES] de verdade — só não há, nesta rodada, Capability Engine gerenciando
     * sessão para alimentar esta chamada sem parâmetro de credencial (mesma lacuna documentada em
     * `docs/architecture/hal-layering-model.md` §8 passo 5). Quem precisa do dado real hoje
     * (`ManualCheckRunner`, testes) chama [readSnapshot] diretamente com a credencial da sessão
     * local.
     */
    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult {
        if (id !in SUPPORTED_CAPABILITIES) {
            return CapabilityReadResult.Unavailable(
                reason = "TpLinkStokLuciDriverFamily não implementa parsing para $id nesta rodada.",
            )
        }
        return CapabilityReadResult.Unavailable(
            reason = "Leitura de $id já tem parser estruturado (TpLinkStokLuciStatusParser, a partir de " +
                "admin/status?form=all), mas exige uma sessão autenticada (usuário/senha informados pelo " +
                "usuário na sessão local) que esta assinatura de readCapability(id) não recebe — ainda não " +
                "há Capability Engine gerenciando essa sessão. Use readSnapshot(username, password) " +
                "diretamente com a credencial da sessão local.",
        )
    }

    private fun classifyFailure(error: Throwable): TpLinkStokLuciFailureReason = when (classifyNetworkFailure(error)) {
        NetworkFailureReason.DEVICE_UNREACHABLE -> TpLinkStokLuciFailureReason.DEVICE_UNREACHABLE
        NetworkFailureReason.TIMEOUT -> TpLinkStokLuciFailureReason.TIMEOUT
        NetworkFailureReason.UNEXPECTED_RESPONSE -> TpLinkStokLuciFailureReason.UNEXPECTED_RESPONSE
        NetworkFailureReason.COMMUNICATION_ERROR -> TpLinkStokLuciFailureReason.COMMUNICATION_ERROR
    }

    companion object {
        /**
         * Capabilities com parser estruturado real a partir de `admin/status?form=all`
         * ([TpLinkStokLuciStatusParser]) — `READ_DEVICE_INFO`/`READ_FIRMWARE` ficam de fora porque
         * nenhum campo de modelo/firmware foi confirmado nesse payload até aqui.
         */
        val SUPPORTED_CAPABILITIES: Set<CapabilityId> = setOf(
            CapabilityId.READ_WIFI_STATUS,
            CapabilityId.READ_LAN_STATUS,
            CapabilityId.READ_WAN_STATUS,
            CapabilityId.READ_CONNECTED_CLIENTS,
        )
    }
}

/**
 * Fábrica de [TpLinkStokLuciDriverFamily], registrada no
 * [com.nethal.core.catalog.DriverFamilyRegistry] sob a chave `"tplink-stok-luci-driver"` (mesmo
 * valor de `profile.driverFamilyId` para o profile `tplink_archer_c6_stok_v1` no catálogo).
 *
 * Diferente de [com.nethal.core.driver.family.tplink.legacycgi.TpLinkLegacyCgiDriverFamilyFactory],
 * usa o [HttpTransport] compartilhado diretamente, sem adapter — não existe assinatura legada
 * (`TplinkHttpTransport`) para preservar aqui, já que esta é uma Driver Family nova.
 */
internal class TpLinkStokLuciDriverFamilyFactory : DriverFamilyFactory {
    override val familyId: String = "tplink-stok-luci-driver"

    override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily {
        val config = TpLinkStokLuciDriverConfig.fromJsonElement(profile.driverConfig)
        return TpLinkStokLuciDriverFamily(host = host, config = config, transport = transport)
    }
}
