package com.nethal.core.driver.nokia

import com.nethal.core.discovery.PrivateIpRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Motivo de falha do driver após esgotar as tentativas de retry — vocabulário para a UI decidir a mensagem. */
internal enum class NokiaDriverFailureReason {
    DEVICE_UNREACHABLE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
    INVALID_CREDENTIALS,
    SESSION_IN_USE,
    COMMUNICATION_ERROR,
}

internal sealed interface NokiaDriverResult {
    data class Success(val snapshot: NokiaDriverSnapshot) : NokiaDriverResult
    data class Failure(val reason: NokiaDriverFailureReason, val message: String) : NokiaDriverResult
}

/**
 * Driver de leitura do Nokia G-1425G-B (profile `nokia_g1425gb_v1`). Orquestra login + os 4
 * endpoints somente-leitura, com o mesmo retry/backoff do driver de produção do SignallQ (3
 * tentativas, backoff 1s/2s). Sem nenhuma ação de escrita — mesma regra do produto irmão e do
 * princípio "read-only primeiro" do NetHAL.
 *
 * A credencial passada a [readSnapshot] nunca é retida por esta classe além da chamada de login:
 * o [NokiaAuthenticationClient] guarda só o `sid` resultante, em memória, pelo tempo de vida da
 * instância — nunca persistida em disco, nunca enviada à nuvem, nunca logada.
 */
internal class NokiaOntDriver(
    private val host: String,
    private val transport: NokiaHttpTransport = DefaultNokiaHttpTransport(),
    private val maxAttempts: Int = 3,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 1_000L * attempt },
) {
    /**
     * Guarda de SSRF/credencial (mesma classe de risco que a Marisa já apontou em
     * `UpnpIgdProbe` e `HttpFingerprintProbe`, mas aqui o risco é maior: este driver envia a
     * credencial real do usuário, cifrada com a chave pública que o *próprio host* devolve. Se
     * `host` não for validado, um host malicioso/público poderia devolver sua própria chave RSA
     * e receber a credencial do usuário cifrada para si mesmo — phishing de credencial, não só
     * requisição indevida. Falha rápido, sem tentar login, quando o host não é RFC 1918.
     */
    init {
        require(PrivateIpRanges.isPrivate(host)) {
            "NokiaOntDriver só opera contra IP privado (RFC 1918) da LAN; host recebido: $host"
        }
    }

    suspend fun readSnapshot(username: String, password: String): NokiaDriverResult = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        repeat(maxAttempts) { attemptIndex ->
            if (attemptIndex > 0) delay(backoffMillis(attemptIndex))
            try {
                val client = NokiaAuthenticationClient(host, transport)
                client.login(username, password)

                val gponHtml = client.fetchAuthenticated("/wan_status.cgi?gpon")
                val wanHtml = client.fetchAuthenticated("/show_wan_status.cgi?ipv4")
                val pppJson = client.fetchAuthenticated("/index.cgi?getppp")
                val deviceJson = client.fetchAuthenticated("/device_status.cgi")

                return@withContext NokiaDriverResult.Success(
                    NokiaDriverSnapshot(
                        gpon = NokiaResponseParser.parseGponStatus(gponHtml),
                        wan = NokiaResponseParser.parseWanStatus(wanHtml),
                        ppp = NokiaResponseParser.parsePppStatus(pppJson),
                        deviceInfo = NokiaResponseParser.parseDeviceInfo(deviceJson),
                        loginPageEvidence = client.loginPageEvidence,
                    ),
                )
            } catch (e: NokiaLoginException) {
                // Falha de credencial ou sessão em uso não se resolve por retry — falha rápido,
                // sem gastar as 3 tentativas à toa (token expirado é a única exceção que se
                // beneficia de retry, porque a próxima tentativa recaptura nonce/csrf do zero).
                when (e.reason) {
                    NokiaLoginFailureReason.INVALID_CREDENTIALS ->
                        return@withContext NokiaDriverResult.Failure(NokiaDriverFailureReason.INVALID_CREDENTIALS, e.message.orEmpty())
                    NokiaLoginFailureReason.SESSION_IN_USE ->
                        return@withContext NokiaDriverResult.Failure(NokiaDriverFailureReason.SESSION_IN_USE, e.message.orEmpty())
                    NokiaLoginFailureReason.TOKEN_EXPIRED, NokiaLoginFailureReason.UNKNOWN ->
                        lastError = e
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val error = lastError ?: return@withContext NokiaDriverResult.Failure(
            NokiaDriverFailureReason.COMMUNICATION_ERROR,
            "falha desconhecida apos $maxAttempts tentativas",
        )
        NokiaDriverResult.Failure(classifyFailure(error), error.message ?: error.toString())
    }

    private fun classifyFailure(error: Throwable): NokiaDriverFailureReason = when {
        error is ConnectException -> NokiaDriverFailureReason.DEVICE_UNREACHABLE
        error is SocketTimeoutException -> NokiaDriverFailureReason.TIMEOUT
        error.message?.contains("timed out", ignoreCase = true) == true -> NokiaDriverFailureReason.TIMEOUT
        error.message?.contains("refused", ignoreCase = true) == true -> NokiaDriverFailureReason.DEVICE_UNREACHABLE
        error.message?.contains("pubkey") == true ||
            error.message?.contains("nonce") == true ||
            error.message?.contains("csrf") == true -> NokiaDriverFailureReason.UNEXPECTED_RESPONSE
        else -> NokiaDriverFailureReason.COMMUNICATION_ERROR
    }
}
