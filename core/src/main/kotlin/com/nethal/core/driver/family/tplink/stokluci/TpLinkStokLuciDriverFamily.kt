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

/**
 * Driver Family da plataforma `tplink-stok-luci` (`platformId`/`driverFamilyId` do catálogo —
 * profile `tplink_archer_c6_stok_v1`, ver `docs/architecture/hal-layering-model.md` §9.1).
 *
 * Implementa só o login (passos 1-5 do handshake, ver [TpLinkStokLuciAuthenticationClient]) mais
 * uma leitura autenticada simples de status geral — a etapa de chamadas autenticadas com envelope
 * AES/assinatura RSA completo (necessária para a maioria dos endpoints de leitura estruturada)
 * fica fora de escopo desta entrega, documentada como próximo passo.
 *
 * **Nunca testado contra hardware real.** Profile permanece `DISCOVERY_ONLY` até um teste real de
 * login bem-sucedido contra a unidade física do Luiz — este código existe, mas não tem prova de
 * funcionamento além dos testes com fake de transporte. Ver `ManualCheckRunner` para o comando de
 * teste manual quando isso acontecer.
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
                    TpLinkStokLuciLoginFailureReason.KEYS_ENDPOINT_UNAVAILABLE,
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
     * Login seguido de uma única leitura autenticada simples (`config.statusReadPath`), sem
     * envelope AES/assinatura — cobre só o que a pesquisa de terceiros documenta como aceito sem
     * esse envelope para alguns `form`s de leitura. Devolve o corpo bruto (JSON), sem parsing
     * estruturado: nenhum modelo de dados de status foi definido nesta rodada porque o formato de
     * resposta real deste endpoint nunca foi observado contra hardware de verdade.
     */
    suspend fun readStatusRaw(username: String, password: String): TpLinkStokLuciStatusOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkStokLuciLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS -> TpLinkStokLuciFailureReason.INVALID_CREDENTIALS
                    TpLinkStokLuciLoginFailureReason.KEYS_ENDPOINT_UNAVAILABLE,
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
     * Implementação de [DriverFamily.readCapability] — mesmo desenho honesto do
     * `TpLinkLegacyCgiDriverFamily`: sem Capability Engine gerenciando sessão ainda, a única
     * resposta correta é [CapabilityReadResult.Unavailable]. Quem precisa da leitura real hoje
     * (`ManualCheckRunner`, testes) chama [login]/[readStatusRaw] diretamente com a credencial da
     * sessão local.
     */
    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult {
        return CapabilityReadResult.Unavailable(
            reason = "TpLinkStokLuciDriverFamily ainda não implementa leitura estruturada por capability " +
                "(protocolo nunca testado contra hardware real, profile em DISCOVERY_ONLY). Use login()/" +
                "readStatusRaw() diretamente com a credencial da sessão local.",
        )
    }

    private fun classifyFailure(error: Throwable): TpLinkStokLuciFailureReason = when (classifyNetworkFailure(error)) {
        NetworkFailureReason.DEVICE_UNREACHABLE -> TpLinkStokLuciFailureReason.DEVICE_UNREACHABLE
        NetworkFailureReason.TIMEOUT -> TpLinkStokLuciFailureReason.TIMEOUT
        NetworkFailureReason.UNEXPECTED_RESPONSE -> TpLinkStokLuciFailureReason.UNEXPECTED_RESPONSE
        NetworkFailureReason.COMMUNICATION_ERROR -> TpLinkStokLuciFailureReason.COMMUNICATION_ERROR
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
