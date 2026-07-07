package com.nethal.core.driver.family.tplink.stokluci

import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportResponse

/**
 * Fake determinístico de [HttpTransport] para a plataforma `tplink-stok-luci`. Roteia por
 * substring da URL (`form=keys`, `form=auth`, `form=login`, ou qualquer outro path autenticado)
 * porque o protocolo é baseado em caminho/query, diferente do dispatcher único do
 * `tplink-legacy-cgi` (que roteava só pelo corpo do request).
 */
internal class FakeTpLinkStokLuciHttpTransport(
    private val keysResponse: HttpTransportResponse? = null,
    private val authResponse: HttpTransportResponse? = null,
    private val loginResponse: HttpTransportResponse? = null,
    private val statusResponse: HttpTransportResponse? = null,
) : HttpTransport {

    var postCallCount = 0
        private set
    val postedUrls = mutableListOf<String>()
    var lastLoginBody: String? = null
        private set

    override fun get(url: String, extraHeaders: Map<String, String>): HttpTransportResponse =
        HttpTransportResponse(404, "", emptyMap(), emptyMap())

    override fun post(url: String, body: String, cookies: Map<String, String>): HttpTransportResponse {
        postCallCount++
        postedUrls.add(url)
        return when {
            url.contains("form=keys") -> keysResponse ?: HttpTransportResponse(404, "", emptyMap(), emptyMap())
            url.contains("form=auth") -> authResponse ?: HttpTransportResponse(404, "", emptyMap(), emptyMap())
            url.contains("form=login") -> {
                lastLoginBody = body
                loginResponse ?: HttpTransportResponse(404, "", emptyMap(), emptyMap())
            }
            else -> statusResponse ?: HttpTransportResponse(200, "{}", emptyMap(), emptyMap())
        }
    }
}

/** Chave RSA de teste, matematicamente válida (1024 bits), gerada uma única vez para os fixtures. */
internal object TestRsaKeyFixture {
    // Módulo/expoente de um par RSA de teste gerado localmente (KeyPairGenerator, RSA 1024 bits)
    // só para os testes deste pacote — não é chave de produção nem foi extraída de nenhum
    // equipamento real. Precisa ser uma chave RSA válida (não hex arbitrário) porque
    // Cipher.init/doFinal falham com uma RSAPublicKeySpec matematicamente inconsistente.
    const val MODULUS_HEX =
        "9292def832383b84a87aa966466fda19707ffa72e38014369aaeacf00f9b948" +
            "dbeed653c8311a9d1bf89dd7bfdceef8181dedba14747a2ae5fc7c8ad6d10ee" +
            "1edb0f3f819142cf50d88fe678123b780d27d8f6ccda921f88384c9a2427d94" +
            "070e4baf40c3147e9dabdffc865784d7cb9f30c02979b0926bf8f75230e2960" +
            "9bd7"
    const val EXPONENT_HEX = "10001"
}

internal fun keysSuccessResponse(): HttpTransportResponse = HttpTransportResponse(
    statusCode = 200,
    body = """{"data":{"password":["${TestRsaKeyFixture.MODULUS_HEX}","${TestRsaKeyFixture.EXPONENT_HEX}"]}}""",
    headers = emptyMap(),
    cookies = emptyMap(),
)

internal fun authSuccessResponse(): HttpTransportResponse = HttpTransportResponse(
    statusCode = 200,
    body = """{"data":{"seq":12345,"key":["${TestRsaKeyFixture.MODULUS_HEX}","${TestRsaKeyFixture.EXPONENT_HEX}"]}}""",
    headers = emptyMap(),
    cookies = emptyMap(),
)

internal fun loginSuccessResponse(stok: String = "abc123stoktoken"): HttpTransportResponse = HttpTransportResponse(
    statusCode = 200,
    body = """{"data":{"stok":"$stok"}}""",
    headers = mapOf("set-cookie" to "sysauth=deadbeef1234; Path=/; HttpOnly"),
    cookies = emptyMap(),
)

internal fun loginFailureResponse(): HttpTransportResponse = HttpTransportResponse(
    statusCode = 200,
    body = """{"data":{}}""",
    headers = emptyMap(),
    cookies = emptyMap(),
)
