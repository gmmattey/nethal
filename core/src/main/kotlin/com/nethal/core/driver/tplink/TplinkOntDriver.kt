package com.nethal.core.driver.tplink

import com.nethal.core.discovery.PrivateIpRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Motivo de falha do driver após esgotar as tentativas — vocabulário para a UI decidir a mensagem. */
internal enum class TplinkDriverFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    SESSION_IN_USE,
    COMMUNICATION_ERROR,
}

internal sealed interface TplinkDriverResult {
    data class Success(val snapshot: TplinkDriverSnapshot) : TplinkDriverResult
    data class Failure(val reason: TplinkDriverFailureReason, val message: String) : TplinkDriverResult
}

/**
 * Driver de leitura do TP-Link Archer C6 (profile `tplink_archer_c6_v1`). Orquestra login +
 * endpoints somente-leitura. Retry deliberadamente mais conservador que o Nokia (no máximo 2
 * tentativas, não 3): a WebUI TP-Link aceita só uma sessão simultânea (ver `session_behavior` no
 * catálogo), então retentativas agressivas tendem a colidir com a própria sessão anterior ainda
 * não expirada, piorando a situação em vez de resolver.
 *
 * A credencial passada a [readSnapshot] nunca é retida por esta classe além da chamada de login:
 * [TplinkAuthenticationClient] guarda só os cookies de sessão resultantes, em memória, pelo tempo
 * de vida da instância — nunca persistidos em disco, nunca enviados à nuvem, nunca logados.
 */
internal class TplinkOntDriver(
    private val host: String,
    private val transport: TplinkHttpTransport = DefaultTplinkHttpTransport(),
    private val cipherVariant: TplinkCipherVariant = TplinkCipherVariant.AES_CBC,
    private val maxAttempts: Int = 2,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) {
    /**
     * Guarda de SSRF/credencial obrigatória, mesma classe de risco documentada em
     * `NokiaOntDriver`: o handshake "web encrypted password" cifra a credencial do usuário com a
     * chave pública que o próprio host devolve. Sem esta checagem, um host malicioso/público
     * poderia devolver sua própria chave RSA e coletar a credencial do usuário cifrada para si —
     * phishing de credencial, não só requisição indevida. Falha rápido, sem tentar login, quando
     * o host não é RFC 1918.
     */
    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "TplinkOntDriver só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    suspend fun readSnapshot(username: String, password: String): TplinkDriverResult = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        repeat(maxAttempts) { attemptIndex ->
            if (attemptIndex > 0) delay(backoffMillis(attemptIndex))
            try {
                val client = TplinkAuthenticationClient(host, transport, cipherVariant)
                client.login(username, password)

                val deviceInfoJson = client.fetchAuthenticated("/cgi/getDeviceInfo")
                val wanJson = client.fetchAuthenticated("/cgi/getWanStatus")
                val wifiJson = client.fetchAuthenticated("/cgi/getWifiStatus")
                val clientsJson = client.fetchAuthenticated("/cgi/getConnectedClients")

                return@withContext TplinkDriverResult.Success(
                    TplinkDriverSnapshot(
                        deviceInfo = TplinkResponseParser.parseDeviceInfo(deviceInfoJson),
                        wan = TplinkResponseParser.parseWanStatus(wanJson),
                        wifi = TplinkResponseParser.parseWifiStatus(wifiJson),
                        connectedClients = TplinkResponseParser.parseConnectedClients(clientsJson),
                    ),
                )
            } catch (e: TplinkLoginException) {
                // Falha de credencial ou sessão em uso não se resolve por retry — falha rápido,
                // preservando a única retentativa disponível para erro de comunicação transitório.
                when (e.reason) {
                    TplinkLoginFailureReason.INVALID_CREDENTIALS ->
                        return@withContext TplinkDriverResult.Failure(TplinkDriverFailureReason.INVALID_CREDENTIALS, e.message.orEmpty())
                    TplinkLoginFailureReason.SESSION_IN_USE ->
                        return@withContext TplinkDriverResult.Failure(TplinkDriverFailureReason.SESSION_IN_USE, e.message.orEmpty())
                    TplinkLoginFailureReason.UNEXPECTED_RESPONSE, TplinkLoginFailureReason.UNKNOWN ->
                        lastError = e
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val error = lastError ?: return@withContext TplinkDriverResult.Failure(
            TplinkDriverFailureReason.COMMUNICATION_ERROR,
            "falha desconhecida apos $maxAttempts tentativas",
        )
        TplinkDriverResult.Failure(classifyFailure(error), error.message ?: error.toString())
    }

    private fun classifyFailure(error: Throwable): TplinkDriverFailureReason = when {
        error is ConnectException -> TplinkDriverFailureReason.DEVICE_UNREACHABLE
        error is SocketTimeoutException -> TplinkDriverFailureReason.TIMEOUT
        error.message?.contains("timed out", ignoreCase = true) == true -> TplinkDriverFailureReason.TIMEOUT
        error.message?.contains("refused", ignoreCase = true) == true -> TplinkDriverFailureReason.DEVICE_UNREACHABLE
        error.message?.contains("getParm") == true ||
            error.message?.contains("RSA") == true -> TplinkDriverFailureReason.UNEXPECTED_RESPONSE
        else -> TplinkDriverFailureReason.COMMUNICATION_ERROR
    }
}
