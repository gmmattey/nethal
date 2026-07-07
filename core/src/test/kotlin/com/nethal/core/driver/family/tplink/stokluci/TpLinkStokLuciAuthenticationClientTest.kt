package com.nethal.core.driver.family.tplink.stokluci

import com.nethal.core.protocol.http.HttpTransportResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do mecanismo de autenticação da plataforma `tplink-stok-luci`, entendido por pesquisa
 * em código aberto de terceiros (pacote `tplinkrouterc6u`, GPL-3.0) — ver KDoc de
 * [TpLinkStokLuciAuthenticationClient] para a citação completa. **Nunca testado contra hardware
 * real** — estes testes validam só o comportamento desta implementação contra fakes de
 * transporte, não confirmam o protocolo real do equipamento.
 */
class TpLinkStokLuciAuthenticationClientTest {

    @Test
    fun `login succeeds and extracts stok and sysauth cookie from a well-formed response`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginSuccessResponse(stok = "mytoken123"),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val session = client.login("admin", "secret")

        assertTrue(client.isAuthenticated)
        assertEquals("mytoken123", session.stok)
        assertEquals("deadbeef1234", session.sysauthCookie)
    }

    @Test
    fun `login calls keys, auth and login endpoints in that order`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginSuccessResponse(),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        client.login("admin", "secret")

        assertEquals(3, transport.postCallCount)
        assertTrue(transport.postedUrls[0].contains("form=keys"))
        assertTrue(transport.postedUrls[1].contains("form=auth"))
        assertTrue(transport.postedUrls[2].contains("form=login"))
    }

    @Test
    fun `login body never contains the plaintext password, only the RSA-encrypted hex`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginSuccessResponse(),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)
        val distinctivePassword = "S3nh4-Distintiva-StokLuci"

        client.login("admin", distinctivePassword)

        val sentBody = transport.lastLoginBody.orEmpty()
        assertFalse("corpo do login vazou a senha em claro", sentBody.contains(distinctivePassword))
        assertTrue("corpo do login nao segue o formato esperado", sentBody.startsWith("operation=login&password="))
        assertTrue(sentBody.contains("&confirm=true"))
        assertFalse("corpo do login nao deve conter campo de usuario", sentBody.contains("username"))
    }

    @Test
    fun `login populates authKeys from form=auth response, used only for future authenticated calls`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginSuccessResponse(),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        client.login("admin", "secret")

        assertEquals(12345L, client.authKeys?.seq)
    }

    @Test
    fun `login fails fast when keys endpoint is unavailable`() {
        val transport = FakeTpLinkStokLuciHttpTransport(keysResponse = null)
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TpLinkStokLuciLoginException::class.java) {
            client.login("admin", "secret")
        }
        assertEquals(TpLinkStokLuciLoginFailureReason.KEYS_ENDPOINT_UNAVAILABLE, exception.reason)
    }

    @Test
    fun `login succeeds even when form=auth endpoint fails - signing key is not required for login itself`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = HttpTransportResponse(500, "", emptyMap(), emptyMap()),
            loginResponse = loginSuccessResponse(),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        client.login("admin", "secret")

        assertTrue(client.isAuthenticated)
    }

    @Test
    fun `login maps response without stok or sysauth to INVALID_CREDENTIALS`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginFailureResponse(),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TpLinkStokLuciLoginException::class.java) {
            client.login("admin", "wrong-password")
        }
        assertEquals(TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS, exception.reason)
    }

    @Test
    fun `login maps HTTP 401 on the login endpoint to INVALID_CREDENTIALS`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = HttpTransportResponse(401, "", emptyMap(), emptyMap()),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TpLinkStokLuciLoginException::class.java) {
            client.login("admin", "wrong-password")
        }
        assertEquals(TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS, exception.reason)
    }

    @Test
    fun `login maps unexpected HTTP status on login endpoint to UNEXPECTED_RESPONSE`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = HttpTransportResponse(500, "", emptyMap(), emptyMap()),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TpLinkStokLuciLoginException::class.java) {
            client.login("admin", "secret")
        }
        assertEquals(TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE, exception.reason)
    }

    @Test
    fun `no exception message ever contains the plaintext password`() {
        val distinctivePassword = "S3nh4-Muito-Distintiva-StokLuci-Para-Achar-Em-Qualquer-Lugar"
        val transport = FakeTpLinkStokLuciHttpTransport(keysResponse = null)
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        val exception = assertThrows(TpLinkStokLuciLoginException::class.java) {
            client.login("admin", distinctivePassword)
        }

        assertFalse(
            "vazou a senha na mensagem de excecao: ${exception.message}",
            exception.message?.contains(distinctivePassword) == true,
        )
        assertFalse("vazou a senha no toString() da excecao", exception.toString().contains(distinctivePassword))
    }

    @Test
    fun `fetchAuthenticated fails fast when called before a successful login`() {
        val transport = FakeTpLinkStokLuciHttpTransport()
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)

        assertThrows(IllegalStateException::class.java) {
            client.fetchAuthenticated("admin/status", "form=all&operation=read")
        }
    }

    @Test
    fun `fetchAuthenticated sends the sysauth cookie and uses the stok in the path`() {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = keysSuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginSuccessResponse(stok = "tok999"),
            statusResponse = HttpTransportResponse(200, """{"ok":true}""", emptyMap(), emptyMap()),
        )
        val client = TpLinkStokLuciAuthenticationClient("192.168.0.1", transport)
        client.login("admin", "secret")

        client.fetchAuthenticated("admin/status", "form=all&operation=read")

        val statusUrl = transport.postedUrls.last()
        assertTrue(statusUrl.contains(";stok=tok999/admin/status"))
        assertTrue(statusUrl.contains("form=all&operation=read"))
    }
}
