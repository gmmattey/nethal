package com.nethal.core.driver.family.tplink.legacycgi

import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.catalog.DriverFamily
import com.nethal.core.catalog.DriverFamilyFactory
import com.nethal.core.discovery.PrivateIpRanges
import com.nethal.core.driver.NetworkFailureReason
import com.nethal.core.driver.RetryOutcome
import com.nethal.core.driver.classifyNetworkFailure
import com.nethal.core.driver.executeWithRetry
import com.nethal.core.driver.tplink.DefaultTplinkHttpTransport
import com.nethal.core.driver.tplink.TplinkHttpTransport
import com.nethal.core.model.CapabilityId
import com.nethal.core.protocol.http.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Motivo de falha da Driver Family após esgotar as tentativas — vocabulário para a UI decidir a mensagem. */
internal enum class TpLinkLegacyCgiFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    COMMUNICATION_ERROR,
}

internal sealed interface TpLinkLegacyCgiReadOutcome {
    data class Success(val snapshot: TpLinkLegacyCgiSnapshot) : TpLinkLegacyCgiReadOutcome
    data class Failure(val reason: TpLinkLegacyCgiFailureReason, val message: String) : TpLinkLegacyCgiReadOutcome
}

/**
 * Driver Family da plataforma `tplink-legacy-cgi` (`platformId`/`driverFamilyId` do catálogo —
 * ver `docs/architecture/hal-layering-model.md` §5.5), usando o protocolo real confirmado por
 * captura via DevTools contra unidade física do Luiz (2026-07-06, ver SIG-337/SIG-338).
 *
 * Movido de `driver/tplink/TplinkC20OntDriver.kt` no passo 4 do plano de refatoração HAL (§10) —
 * o caso de validação da arquitetura: primeira Driver Family real, provando que a cadeia inteira
 * (Profile → `DriverFamilyRegistry.resolve` → instância → leitura) fecha antes de qualquer
 * expansão de cobertura. Mesmo comportamento observável do driver anterior, com duas mudanças
 * estruturais (não de protocolo):
 *
 * 1. Implementa [DriverFamily] em vez de expor só um método `readSnapshot()` solto — permite
 *    resolução via `DriverFamilyRegistry` a partir de `profile.driverFamilyId`.
 * 2. Os literais de seção/campo antes hardcoded (`listOf("LAN_WLAN" to listOf("name", "SSID"))`,
 *    etc.) agora vêm de `profile.driverConfig`, desserializado como [TpLinkLegacyCgiDriverConfig]
 *    — esta classe nunca hardcoda nome de seção/campo, só orquestra protocolo/retry/parsing.
 *
 * Serve hoje o profile `tplink_archer_c20_v1`; serviria qualquer outro modelo TP-Link com o mesmo
 * dispatcher único `/cgi` + Basic Auth via cookie (ex.: Archer C50 V2) só com um Profile novo
 * apontando `driverFamilyId: "tplink-legacy-cgi-driver"` e seu próprio `driverConfig` — zero
 * Kotlin novo, conforme a regra de evolução de `hal-layering-model.md` §9.
 *
 * Continua separado da plataforma `tplink-encrypted-web` (Archer C6): mesmo fabricante,
 * protocolos totalmente diferentes (o C6 usa handshake RSA+AES "web encrypted password" via
 * `/cgi_gdpr`; esta plataforma usa dispatcher único com Basic Auth via cookie).
 *
 * Retry conservador (no máximo 2 tentativas): sem handshake de sessão para colidir, mas mantido
 * pela mesma razão de qualquer WebUI doméstica local — retentativas agressivas não ajudam contra
 * falha persistente de credencial/rede.
 *
 * A credencial passada a [readSnapshot] nunca é retida por esta classe além da chamada: apenas o
 * cookie Base64 fica em memória em [TpLinkLegacyCgiAuthenticationClient], nunca persistido, nunca
 * enviado à nuvem, nunca logado.
 */
internal class TpLinkLegacyCgiDriverFamily(
    private val host: String,
    private val config: TpLinkLegacyCgiDriverConfig,
    private val transport: TplinkHttpTransport = DefaultTplinkHttpTransport(),
    private val maxAttempts: Int = 2,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) : DriverFamily {

    /**
     * Guarda de SSRF obrigatória, mesma classe de risco documentada em `TplinkOntDriver`/
     * `NokiaOntDriver`: falha rápido, sem tentar login, quando o host não é RFC 1918.
     */
    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "TpLinkLegacyCgiDriverFamily só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    /**
     * Lê o snapshot completo (device info, Wi-Fi, clientes conectados) numa única sessão
     * autenticada — mesma orquestração de sempre, só com seções/campos vindos de [config] em vez
     * de hardcoded. Método interno preservado (não faz parte de [DriverFamily]) porque o payload
     * rico continua sendo o formato consumido por `ManualCheckRunnerC20` e pelos testes; a
     * granularidade por-capability exigida por [DriverFamily.readCapability] delega para este
     * mesmo snapshot.
     */
    suspend fun readSnapshot(username: String, password: String): TpLinkLegacyCgiReadOutcome = withContext(Dispatchers.IO) {
        val outcome = executeWithRetry(
            maxAttempts = maxAttempts,
            backoffMillis = backoffMillis,
            loginExceptionType = TpLinkLegacyCgiLoginException::class.java,
            onLoginFailure = { e ->
                when (e.reason) {
                    TpLinkLegacyCgiLoginFailureReason.INVALID_CREDENTIALS -> TpLinkLegacyCgiFailureReason.INVALID_CREDENTIALS
                    TpLinkLegacyCgiLoginFailureReason.UNEXPECTED_RESPONSE, TpLinkLegacyCgiLoginFailureReason.UNKNOWN -> null
                }
            },
            classifyFinalFailure = ::classifyFailure,
        ) {
            val client = TpLinkLegacyCgiAuthenticationClient(host, transport, config.loginValidationSections())
            client.login(username, password)

            // Mesmo bundle exato usado por login() (config.loginValidationSections()) — evita
            // divergir do único bundle com prova real de sucesso.
            val deviceInfoBody = client.fetchAuthenticated(
                TpLinkLegacyCgiResponseParser.buildRequestBody(config.loginValidationSections()),
            )
            val wifiBody = client.fetchAuthenticated(
                TpLinkLegacyCgiResponseParser.buildRequestBody(config.wifiStatusSections()),
            )
            val clientsBody = client.fetchAuthenticated(
                TpLinkLegacyCgiResponseParser.buildRequestBody(config.connectedClientsSections()),
            )

            TpLinkLegacyCgiSnapshot(
                deviceInfo = TpLinkLegacyCgiResponseParser.parseDeviceInfo(
                    deviceInfoBody,
                    deviceInfoIndex = config.deviceInfoIndex,
                    ethSwitchIndex = config.ethSwitchIndex,
                    sysModeIndex = config.sysModeIndex,
                ),
                wifi = TpLinkLegacyCgiResponseParser.parseWifiStatus(wifiBody, lanWlanIndex = config.wifiStatusIndex),
                connectedClients = TpLinkLegacyCgiResponseParser.parseConnectedClients(
                    clientsBody,
                    lanHostEntryIndex = config.connectedClientsIndex,
                ),
            )
        }

        when (outcome) {
            is RetryOutcome.Success -> TpLinkLegacyCgiReadOutcome.Success(outcome.value)
            is RetryOutcome.Failure -> TpLinkLegacyCgiReadOutcome.Failure(outcome.reason, outcome.error.message ?: outcome.error.toString())
        }
    }

    /**
     * Implementação de [DriverFamily.readCapability] — prova que a resolução via
     * `DriverFamilyRegistry` produz uma instância funcional (passo 4 do plano de refatoração).
     *
     * Esta rodada não tem credencial de sessão disponível no momento da chamada (o Capability
     * Engine que vai gerenciar isso ainda não existe, ver `hal-layering-model.md` §8 passo 5) — por
     * isso, sem uma sessão já autenticada, a única resposta honesta é [CapabilityReadResult.Unavailable].
     * Quem precisa do snapshot real hoje (`ManualCheckRunnerC20`, testes) chama [readSnapshot]
     * diretamente com a credencial da sessão local, exatamente como antes.
     */
    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult {
        if (id !in SUPPORTED_CAPABILITIES) {
            return CapabilityReadResult.Unavailable(
                reason = "TpLinkLegacyCgiDriverFamily não implementa leitura para $id nesta rodada.",
            )
        }
        return CapabilityReadResult.Unavailable(
            reason = "Leitura de $id exige uma sessão autenticada (usuário/senha informados pelo usuário na sessão " +
                "local) — ainda não há Capability Engine gerenciando essa sessão neste passo. Use readSnapshot() " +
                "diretamente com a credencial da sessão local.",
        )
    }

    private fun classifyFailure(error: Throwable): TpLinkLegacyCgiFailureReason = when (classifyNetworkFailure(error)) {
        NetworkFailureReason.DEVICE_UNREACHABLE -> TpLinkLegacyCgiFailureReason.DEVICE_UNREACHABLE
        NetworkFailureReason.TIMEOUT -> TpLinkLegacyCgiFailureReason.TIMEOUT
        NetworkFailureReason.UNEXPECTED_RESPONSE -> TpLinkLegacyCgiFailureReason.UNEXPECTED_RESPONSE
        NetworkFailureReason.COMMUNICATION_ERROR -> TpLinkLegacyCgiFailureReason.COMMUNICATION_ERROR
    }

    companion object {
        val SUPPORTED_CAPABILITIES: Set<CapabilityId> = setOf(
            CapabilityId.READ_DEVICE_INFO,
            CapabilityId.READ_WIFI_STATUS,
            CapabilityId.READ_CONNECTED_CLIENTS,
        )
    }
}

/**
 * Adapta o [HttpTransport] compartilhado (passo 1 do plano de refatoração) para a interface
 * [TplinkHttpTransport] ainda consumida por [TpLinkLegacyCgiAuthenticationClient] — permite que a
 * factory realmente use o transporte recebido via [DriverFamilyFactory.create] em vez de
 * silenciosamente construir um [DefaultTplinkHttpTransport] próprio, o que provaria a resolução da
 * cadeia (`hal-layering-model.md` §8) sem provar que o transporte resolvido é o que de fato é
 * usado para falar com o equipamento.
 */
private class HttpTransportToTplinkAdapter(private val delegate: HttpTransport) : TplinkHttpTransport {
    override fun get(url: String, extraHeaders: Map<String, String>) = delegate.get(url, extraHeaders)
    override fun post(url: String, body: String, cookies: Map<String, String>) =
        delegate.post(url, body, cookies = cookies)
}

/**
 * Fábrica de [TpLinkLegacyCgiDriverFamily], registrada no [com.nethal.core.catalog.DriverFamilyRegistry]
 * sob a chave `"tplink-legacy-cgi-driver"` (mesmo valor de `profile.driverFamilyId` no catálogo).
 *
 * Constrói a Driver Family a partir de [CompatibilityProfile.driverConfig], desserializado como
 * [TpLinkLegacyCgiDriverConfig] — se o profile resolvido para este `driverFamilyId` não tiver um
 * `driverConfig` no formato esperado, a construção falha alta e cedo (catálogo publicado
 * incorretamente), em vez de a Driver Family descobrir isso só na primeira leitura.
 *
 * O [HttpTransport] recebido via [create] (o compartilhado do passo 1) é adaptado para
 * [TplinkHttpTransport] via [HttpTransportToTplinkAdapter] — a Driver Family em si continua
 * programada contra [TplinkHttpTransport] (mesma assinatura usada pelos testes e por
 * `ManualCheckRunnerC20`), mas o transporte de fato usado é sempre o recebido pela factory, nunca
 * um `DefaultTplinkHttpTransport()` construído à parte. Migrar
 * `TpLinkLegacyCgiAuthenticationClient` para depender diretamente de [HttpTransport] (eliminando o
 * adapter) é um passo futuro, sem mudança de comportamento — fora de escopo deste passo 4.
 */
internal class TpLinkLegacyCgiDriverFamilyFactory : DriverFamilyFactory {
    override val familyId: String = "tplink-legacy-cgi-driver"

    override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily {
        val config = TpLinkLegacyCgiDriverConfig.fromJsonElement(profile.driverConfig)
        return TpLinkLegacyCgiDriverFamily(host = host, config = config, transport = HttpTransportToTplinkAdapter(transport))
    }
}
