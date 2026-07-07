package com.nethal.core.driver.tplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Testes do mecanismo real de autenticação do Archer C20 (cookie `Authorization: Basic
 * <base64(user:pass)>`, sem endpoint de login dedicado), confirmado por captura via DevTools
 * contra unidade física do Luiz (2026-07-06, ver SIG-337/SIG-338). Substitui os testes do
 * mecanismo especulativo anterior (MD5+POST/JSON, REFUTED).
 */
class TplinkC20AuthenticationClientTest {

    private fun basicCookie(user: String, pass: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))

    @Test
    fun `login succeeds when first read returns 200 and error code zero`() {
        val expectedCookie = basicCookie("admin", "secret")
        val transport = FakeTplinkC20HttpTransport(
            expectedAuthorizationCookie = expectedCookie,
            defaultResponse = deviceInfoOnlyResponse(),
        )
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        client.login("admin", "secret")

        assertTrue(client.isAuthenticated)
        assertEquals(1, transport.postCallCount)
    }

    @Test
    fun `login sends the Authorization cookie with base64(user colon pass), never the plaintext password in the request body`() {
        val transport = FakeTplinkC20HttpTransport(
            expectedAuthorizationCookie = basicCookie("admin", "S3nh4-Distintiva-C20"),
            defaultResponse = deviceInfoOnlyResponse(),
        )
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)
        val distinctivePassword = "S3nh4-Distintiva-C20"

        client.login("admin", distinctivePassword)

        val sentBody = transport.lastRequestBody.orEmpty()
        assertFalse("corpo da requisicao vazou a senha em claro", sentBody.contains(distinctivePassword))
        assertEquals(basicCookie("admin", distinctivePassword), transport.lastCookieHeaderSent)
    }

    @Test
    fun `login maps 401 status to INVALID_CREDENTIALS`() {
        val transport = FakeTplinkC20HttpTransport(
            expectedAuthorizationCookie = basicCookie("admin", "correct-password"),
            defaultResponse = deviceInfoOnlyResponse(),
        )
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", "wrong-password")
        }
        assertEquals(TplinkC20LoginFailureReason.INVALID_CREDENTIALS, exception.reason)
    }

    @Test
    fun `login maps error code different from zero to INVALID_CREDENTIALS`() {
        val transport = FakeTplinkC20HttpTransport(defaultResponse = globalErrorResponse(code = 1))
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", "secret")
        }
        assertEquals(TplinkC20LoginFailureReason.INVALID_CREDENTIALS, exception.reason)
    }

    @Test
    fun `login maps error code zero without modelName field to UNEXPECTED_RESPONSE, not INVALID_CREDENTIALS`() {
        val transport = FakeTplinkC20HttpTransport(defaultResponse = globalErrorResponse(code = 0))
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", "secret")
        }
        assertEquals(TplinkC20LoginFailureReason.UNEXPECTED_RESPONSE, exception.reason)
    }

    @Test
    fun `login maps response without recognizable error marker to UNEXPECTED_RESPONSE`() {
        val transport = FakeTplinkC20HttpTransport(
            defaultResponse = TplinkHttpResponse(200, "", emptyMap(), emptyMap()),
        )
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", "secret")
        }
        assertEquals(TplinkC20LoginFailureReason.UNEXPECTED_RESPONSE, exception.reason)
    }

    @Test
    fun `no exception message ever contains the plaintext password`() {
        val distinctivePassword = "S3nh4-Muito-Distintiva-C20-Para-Achar-Em-Qualquer-Lugar"
        val transport = FakeTplinkC20HttpTransport(defaultResponse = globalErrorResponse(code = 1))
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", distinctivePassword)
        }

        assertTrue(
            "vazou a senha na mensagem de excecao: ${exception.message}",
            exception.message?.contains(distinctivePassword) != true,
        )
        assertFalse("vazou a senha no toString() da excecao", exception.toString().contains(distinctivePassword))
    }

    @Test
    fun `no exception message ever contains the Authorization cookie value (base64 secret)`() {
        val distinctivePassword = "Outra-Senha-Bem-Distintiva"
        val transport = FakeTplinkC20HttpTransport(defaultResponse = globalErrorResponse(code = 1))
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)
        val cookieValue = basicCookie("admin", distinctivePassword)

        val exception = assertThrows(TplinkC20LoginException::class.java) {
            client.login("admin", distinctivePassword)
        }

        assertFalse("vazou o cookie Authorization na mensagem de excecao", exception.message.orEmpty().contains(cookieValue))
        assertFalse("vazou o cookie Authorization no toString() da excecao", exception.toString().contains(cookieValue))
    }

    @Test
    fun `fetchAuthenticated fails fast when called before a successful login`() {
        val transport = FakeTplinkC20HttpTransport()
        val client = TplinkC20AuthenticationClient("192.168.0.1", transport)

        assertThrows(IllegalStateException::class.java) {
            client.fetchAuthenticated(TplinkC20ResponseParser.buildRequestBody(listOf("IGD_DEV_INFO" to listOf("modelName"))))
        }
    }
}
