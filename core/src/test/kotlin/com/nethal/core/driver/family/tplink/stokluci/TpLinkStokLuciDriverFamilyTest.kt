package com.nethal.core.driver.family.tplink.stokluci

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de [TpLinkStokLuciDriverFamily] — orquestração (retry, guarda RFC 1918, classificação de
 * falha), com fake de transporte. Não confirmam o protocolo contra hardware real (ver KDoc da
 * classe e do profile `tplink_archer_c6_stok_v1` no catálogo, `DISCOVERY_ONLY`).
 */
class TpLinkStokLuciDriverFamilyTest {

    private fun realProfileConfig(): TpLinkStokLuciDriverConfig = TpLinkStokLuciDriverConfig(
        statusReadPath = "admin/status",
        statusReadQuery = "form=all&operation=read",
    )

    @Test
    fun `rejects public host at construction - never sends credentials outside the LAN`() {
        val transport = FakeTpLinkStokLuciHttpTransport()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            TpLinkStokLuciDriverFamily("8.8.8.8", realProfileConfig(), transport)
        }
        assertTrue(exception.message!!.contains("8.8.8.8"))
    }

    @Test
    fun `accepts RFC1918 private host at construction`() {
        val transport = FakeTpLinkStokLuciHttpTransport()

        listOf("192.168.0.1", "10.0.0.1", "172.16.5.5").forEach { privateHost ->
            TpLinkStokLuciDriverFamily(privateHost, realProfileConfig(), transport) // não deve lançar
        }
    }

    @Test
    fun `login succeeds on first attempt against a well-formed fake response`() = runTest {
        val transport = FakeTpLinkStokLuciHttpTransport(
            simulateRealServerStok = "tokABC",
        )
        val driver = TpLinkStokLuciDriverFamily("192.168.0.1", realProfileConfig(), transport, backoffMillis = { 0L })

        val result = driver.login("admin", "secret")

        assertTrue(result is TpLinkStokLuciLoginOutcome.Success)
        assertEquals("tokABC", (result as TpLinkStokLuciLoginOutcome.Success).session.stok)
    }

    @Test
    fun `login fails fast on invalid credentials without exhausting retries`() = runTest {
        val transport = FakeTpLinkStokLuciHttpTransport(
            keysResponse = passwordKeySuccessResponse(),
            authResponse = authSuccessResponse(),
            loginResponse = loginFailureResponse(),
        )
        val driver = TpLinkStokLuciDriverFamily("192.168.0.1", realProfileConfig(), transport, maxAttempts = 2, backoffMillis = { 0L })

        val result = driver.login("admin", "wrong")

        assertTrue(result is TpLinkStokLuciLoginOutcome.Failure)
        assertEquals(TpLinkStokLuciFailureReason.INVALID_CREDENTIALS, (result as TpLinkStokLuciLoginOutcome.Failure).reason)
        // sem retry para credencial invalida: so uma rodada completa de keys+auth+login = 3 chamadas
        assertEquals(3, transport.postCallCount)
    }

    @Test
    fun `login respects conservative max attempts default of two on persistent network failure`() = runTest {
        val transport = FakeTpLinkStokLuciHttpTransport(keysResponse = null)
        var backoffCalls = 0
        val driver = TpLinkStokLuciDriverFamily(
            "192.168.0.1",
            realProfileConfig(),
            transport,
            backoffMillis = { backoffCalls++; 0L },
        )

        val result = driver.login("admin", "secret")

        assertTrue(result is TpLinkStokLuciLoginOutcome.Failure)
        assertEquals(1, backoffCalls) // só 1 backoff entre as 2 tentativas (default maxAttempts=2)
    }

    @Test
    fun `readStatusRaw returns the raw body after a successful login`() = runTest {
        val transport = FakeTpLinkStokLuciHttpTransport(
            simulateRealServerStok = "tokXYZ",
            statusResponse = com.nethal.core.protocol.http.HttpTransportResponse(200, """{"status":"ok"}""", emptyMap(), emptyMap()),
        )
        val driver = TpLinkStokLuciDriverFamily("192.168.0.1", realProfileConfig(), transport, backoffMillis = { 0L })

        val result = driver.readStatusRaw("admin", "secret")

        assertTrue(result is TpLinkStokLuciStatusOutcome.Success)
        assertEquals("""{"status":"ok"}""", (result as TpLinkStokLuciStatusOutcome.Success).rawBody)
    }

    @Test
    fun `readCapability always returns Unavailable - no Capability Engine session management yet`() = runTest {
        val transport = FakeTpLinkStokLuciHttpTransport()
        val driver = TpLinkStokLuciDriverFamily("192.168.0.1", realProfileConfig(), transport)

        val result = driver.readCapability(com.nethal.core.model.CapabilityId.READ_DEVICE_INFO)

        assertTrue(result is com.nethal.core.catalog.CapabilityReadResult.Unavailable)
    }
}
