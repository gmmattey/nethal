package com.nethal.core.driver.family.tplink.gdprcgi

import com.nethal.core.driver.family.tplink.stokluci.TestSignKeyFixture
import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.spec.RSAPrivateKeySpec
import javax.crypto.Cipher

class TpLinkGdprCgiDriverFamilyTest {

    @Test
    fun `rejects public host for GDPR family`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TpLinkGdprCgiDriverFamily(
                "8.8.8.8",
                c50Config(),
                FakeTpLinkGdprCgiHttpTransport(c50Config(), TpLinkGdprCgiCryptoMode.AES_CBC),
            )
        }
        assertTrue(exception.message!!.contains("8.8.8.8"))
    }

    @Test
    fun `cbc login and raw read succeed for GDPR CGI family`() = runTest {
        val config = c50Config()
        val transport = FakeTpLinkGdprCgiHttpTransport(config, TpLinkGdprCgiCryptoMode.AES_CBC)
        val driver = TpLinkGdprCgiDriverFamily("192.168.1.1", config, transport, backoffMillis = { 0L })

        val result = driver.readRaw("admin", "secret")

        assertTrue(result is TpLinkGdprCgiReadOutcome.Success)
        assertEquals("""$.ret=0;""", (result as TpLinkGdprCgiReadOutcome.Success).rawBody)
        assertEquals("token-cbc", transport.lastTokenId)
    }

    @Test
    fun `gcm login and raw read succeed for GDPR CGI family`() = runTest {
        val config = exGcmConfig()
        val transport = FakeTpLinkGdprCgiHttpTransport(config, TpLinkGdprCgiCryptoMode.AES_GCM)
        val driver = TpLinkGdprCgiDriverFamily("192.168.1.1", config, transport, backoffMillis = { 0L })

        val result = driver.readRaw("user", "secret")

        assertTrue(result is TpLinkGdprCgiReadOutcome.Success)
        assertEquals("""$.ret=0;""", (result as TpLinkGdprCgiReadOutcome.Success).rawBody)
        assertEquals("token-gcm", transport.lastTokenId)
    }

    private fun c50Config() = TpLinkGdprCgiDriverConfig(
        rsaKeyPath = "cgi/getParm",
        loginPath = "cgi_gdpr",
        loginStyle = TpLinkGdprCgiLoginStyle.C50_GDPR_BODY_LOGIN,
        cryptoMode = TpLinkGdprCgiCryptoMode.AES_CBC,
        rsaPaddingMode = TpLinkGdprCgiRsaPaddingMode.PKCS1_V1_5,
        tokenPath = "/",
        authenticatedReadPath = "cgi_gdpr",
        authenticatedReadPlaintext = "1\r\n[/cgi/info#0,0,0,0,0,0#0,0,0,0,0,0]0,0\r\n",
    )

    private fun exGcmConfig() = TpLinkGdprCgiDriverConfig(
        rsaKeyPath = "cgi/getGDPRParm",
        loginPath = "cgi_gdpr?9",
        loginStyle = TpLinkGdprCgiLoginStyle.EX_JSON_GDPR_BODY_LOGIN,
        cryptoMode = TpLinkGdprCgiCryptoMode.AES_GCM,
        rsaPaddingMode = TpLinkGdprCgiRsaPaddingMode.PKCS1_V1_5,
        tokenPath = "/",
        authenticatedReadPath = "cgi_gdpr?9",
        authenticatedReadPlaintext = """{"operation":"go","oid":"DEV2_DEV_INFO","data":{"stack":"0,0,0,0,0,0","pstack":"0,0,0,0,0,0"}}""",
    )
}

private class FakeTpLinkGdprCgiHttpTransport(
    private val config: TpLinkGdprCgiDriverConfig,
    private val cryptoMode: TpLinkGdprCgiCryptoMode,
) : HttpTransport {

    var lastTokenId: String? = null
        private set

    private var aesKeyAscii: String? = null
    private var aesIvOrNonceAscii: String? = null

    override fun get(url: String, extraHeaders: Map<String, String>): HttpTransportResponse =
        if (url.endsWith("/") || url == "http://192.168.1.1/") {
            val token = if (cryptoMode == TpLinkGdprCgiCryptoMode.AES_CBC) "token-cbc" else "token-gcm"
            lastTokenId = token
            HttpTransportResponse(200, """<script>var token="$token";</script>""", emptyMap(), emptyMap())
        } else {
            HttpTransportResponse(404, "", emptyMap(), emptyMap())
        }

    override fun post(
        url: String,
        body: String,
        cookies: Map<String, String>,
        extraHeaders: Map<String, String>,
    ): HttpTransportResponse = when {
        url.contains(config.rsaKeyPath) -> HttpTransportResponse(
            200,
            """var ee="10001";var nn="${TestSignKeyFixture.MODULUS_HEX}";var seq="12345";""",
            emptyMap(),
            emptyMap(),
        )
        extraHeaders["TokenID"] != null && url.contains(config.authenticatedReadPath) ->
            simulateAuthenticatedRead(body, extraHeaders)
        url.contains(config.loginPath) -> simulateEncryptedLogin(body)
        else -> HttpTransportResponse(404, "", emptyMap(), emptyMap())
    }

    private fun simulateEncryptedLogin(body: String): HttpTransportResponse {
        captureAesContextFromBody(body)
        val responseBody = encryptedResponse("""$.ret=0;""")
        return HttpTransportResponse(
            200,
            responseBody,
            emptyMap(),
            mapOf("JSESSIONID" to "session-ok"),
        )
    }

    private fun simulateAuthenticatedRead(body: String, headers: Map<String, String>): HttpTransportResponse {
        require(headers["TokenID"] == lastTokenId) { "TokenID ausente ou incorreto" }
        captureAesContextFromBody(body)
        return HttpTransportResponse(200, encryptedResponse("""$.ret=0;"""), emptyMap(), emptyMap())
    }

    private fun captureAesContextFromBody(body: String) {
        val signHex = Regex("""sign=([0-9a-f]+)""").find(body)?.groupValues?.get(1)
            ?: error("sign ausente")
        val signPlaintext = decryptPkcs1Chunked(signHex)
        when (cryptoMode) {
            TpLinkGdprCgiCryptoMode.AES_CBC -> {
                Regex("""key=(\d{16})""").find(signPlaintext)?.groupValues?.get(1)?.let { aesKeyAscii = it }
                Regex("""iv=(\d{16})""").find(signPlaintext)?.groupValues?.get(1)?.let { aesIvOrNonceAscii = it }
            }
            TpLinkGdprCgiCryptoMode.AES_GCM -> {
                Regex("""key=([^&]+)""").find(signPlaintext)?.groupValues?.get(1)?.let {
                    aesKeyAscii = String(TpLinkGdprCgiCrypto.base64Decode(it), Charsets.UTF_8)
                }
                Regex("""iv=([^&]+)""").find(signPlaintext)?.groupValues?.get(1)?.let {
                    aesIvOrNonceAscii = String(TpLinkGdprCgiCrypto.base64Decode(it), Charsets.UTF_8)
                }
            }
        }
        if (body.contains("data=")) {
            val dataValue = Regex("""data=([^\r\n]+)""").find(body)?.groupValues?.get(1)
            if (dataValue != null && !body.contains("?data=")) {
                URLDecoder.decode(dataValue, Charsets.UTF_8)
            }
        }
    }

    private fun encryptedResponse(plaintext: String): String {
        val key = requireNotNull(aesKeyAscii)
        val ivOrNonce = requireNotNull(aesIvOrNonceAscii)
        return when (cryptoMode) {
            TpLinkGdprCgiCryptoMode.AES_CBC -> TpLinkGdprCgiCrypto.aesCbcEncrypt(key, ivOrNonce, plaintext)
            TpLinkGdprCgiCryptoMode.AES_GCM -> {
                val (ciphertext, tag) = TpLinkGdprCgiCrypto.aesGcmEncrypt(key, ivOrNonce, plaintext)
                ciphertext + tag
            }
        }
    }

    private fun decryptPkcs1Chunked(signHex: String): String {
        val modulus = BigInteger(TestSignKeyFixture.MODULUS_HEX, 16)
        val privateExponent = BigInteger(TestSignKeyFixture.PRIVATE_EXPONENT_HEX, 16)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(modulus, privateExponent))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val blockHexSize = ((modulus.bitLength() + 7) / 8) * 2
        val plaintext = StringBuilder()
        var offset = 0
        while (offset < signHex.length) {
            val end = minOf(offset + blockHexSize, signHex.length)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decryptedChunk = cipher.doFinal(hexToBytes(signHex.substring(offset, end)))
            plaintext.append(String(decryptedChunk, Charsets.UTF_8))
            offset = end
        }
        return plaintext.toString()
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
