package com.nethal.core.driver.family.tplink.stokluci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TpLinkStokLuciCryptoTest {

    @Test
    fun `rsaEncryptPkcs1ToHex produces a hex string, never the plaintext password`() {
        val encrypted = TpLinkStokLuciCrypto.rsaEncryptPkcs1ToHex(
            TestRsaKeyFixture.MODULUS_HEX,
            TestRsaKeyFixture.EXPONENT_HEX,
            "S3nh4-De-Teste",
        )

        assertTrue(encrypted.matches(Regex("^[0-9a-f]+$")))
        assertNotEquals("S3nh4-De-Teste", encrypted)
        assertTrue("hex encriptado com PKCS1v1.5 deve ter tamanho compativel com a chave (128 bytes = 256 hex chars para 1024 bits)", encrypted.length in 240..260)
    }

    @Test
    fun `rsaEncryptPkcs1ToHex is non-deterministic due to PKCS1v1_5 random padding`() {
        val first = TpLinkStokLuciCrypto.rsaEncryptPkcs1ToHex(TestRsaKeyFixture.MODULUS_HEX, TestRsaKeyFixture.EXPONENT_HEX, "mesma-senha")
        val second = TpLinkStokLuciCrypto.rsaEncryptPkcs1ToHex(TestRsaKeyFixture.MODULUS_HEX, TestRsaKeyFixture.EXPONENT_HEX, "mesma-senha")

        assertNotEquals(first, second)
    }

    @Test
    fun `extractSysauthCookie parses value from a raw Set-Cookie header with other attributes`() {
        val value = TpLinkStokLuciCrypto.extractSysauthCookie("sysauth=deadbeef1234; Path=/; HttpOnly")
        assertEquals("deadbeef1234", value)
    }

    @Test
    fun `extractSysauthCookie returns null when the header has no sysauth value`() {
        assertNull(TpLinkStokLuciCrypto.extractSysauthCookie("otherCookie=abc; Path=/"))
        assertNull(TpLinkStokLuciCrypto.extractSysauthCookie(null))
        assertNull(TpLinkStokLuciCrypto.extractSysauthCookie(""))
    }
}
