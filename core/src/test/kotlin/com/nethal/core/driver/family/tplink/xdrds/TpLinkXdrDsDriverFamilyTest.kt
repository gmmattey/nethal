package com.nethal.core.driver.family.tplink.xdrds

import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TpLinkXdrDsDriverFamilyTest {

    @Test
    fun `rejects public host for XDR family`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TpLinkXdrDsDriverFamily(
                "8.8.8.8",
                config(),
                FakeTpLinkXdrDsHttpTransport(expectNonceFlow = true),
            )
        }
        assertTrue(exception.message!!.contains("8.8.8.8"))
    }

    @Test
    fun `nonce based login and authenticated read succeed`() = runTest {
        val transport = FakeTpLinkXdrDsHttpTransport(expectNonceFlow = true)
        val driver = TpLinkXdrDsDriverFamily("192.168.0.1", config(), transport, backoffMillis = { 0L })

        val result = driver.readRaw("admin", "secret")

        assertTrue(result is TpLinkXdrDsReadOutcome.Success)
        assertEquals("""{"error_code":0}""", (result as TpLinkXdrDsReadOutcome.Success).rawBody)
        assertEquals("3", transport.lastEncryptType)
    }

    @Test
    fun `legacy encoded password login still succeeds when encrypt info probe is unsupported`() = runTest {
        val transport = FakeTpLinkXdrDsHttpTransport(expectNonceFlow = false)
        val driver = TpLinkXdrDsDriverFamily("192.168.0.1", config(), transport, backoffMillis = { 0L })

        val result = driver.login("admin", "secret")

        assertTrue(result is TpLinkXdrDsLoginOutcome.Success)
        assertEquals("legacy", transport.lastLoginMode)
    }

    private fun config() = TpLinkXdrDsDriverConfig(
        encryptInfoPath = "/",
        loginPath = "/",
        authenticatedPathTemplate = "/stok={stok}/ds",
        authenticatedReadPayloadJson = """{"method":"get","device_info":{"name":"info"}}""",
    )
}

private class FakeTpLinkXdrDsHttpTransport(
    private val expectNonceFlow: Boolean,
) : HttpTransport {
    var lastEncryptType: String? = null
        private set
    var lastLoginMode: String? = null
        private set

    override fun get(url: String, extraHeaders: Map<String, String>): HttpTransportResponse =
        HttpTransportResponse(404, "", emptyMap(), emptyMap())

    override fun post(
        url: String,
        body: String,
        cookies: Map<String, String>,
        extraHeaders: Map<String, String>,
    ): HttpTransportResponse = when {
        body.contains("get_encrypt_info") && expectNonceFlow -> HttpTransportResponse(
            200,
            """{"error_code":0,"encrypt_type":["3"],"nonce":"nonce-123"}""",
            emptyMap(),
            emptyMap(),
        )
        body.contains("get_encrypt_info") -> HttpTransportResponse(
            200,
            """{"error_code":1}""",
            emptyMap(),
            emptyMap(),
        )
        body.contains(""""login"""") -> {
            lastEncryptType = Regex(""""encrypt_type":"?([^",}]+)""").find(body)?.groupValues?.get(1)
            lastLoginMode = if (body.contains("encrypt_type")) "nonce" else "legacy"
            HttpTransportResponse(200, """{"stok":"stok-123"}""", emptyMap(), emptyMap())
        }
        url.contains("/stok=stok-123/ds") -> HttpTransportResponse(200, """{"error_code":0}""", emptyMap(), emptyMap())
        else -> HttpTransportResponse(404, "", emptyMap(), emptyMap())
    }
}
