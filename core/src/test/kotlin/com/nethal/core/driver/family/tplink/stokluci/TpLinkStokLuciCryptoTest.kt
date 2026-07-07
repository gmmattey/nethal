package com.nethal.core.driver.family.tplink.stokluci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TpLinkStokLuciCryptoTest {

    @Test
    fun `rsaEncryptChunkedToHex produces a hex string, never the plaintext`() {
        val encrypted = TpLinkStokLuciCrypto.rsaEncryptChunkedToHex(
            TestRsaKeyFixture.MODULUS_HEX,
            TestRsaKeyFixture.EXPONENT_HEX,
            "k=aabb&i=ccdd&h=deadbeef&s=1",
        )

        assertTrue(encrypted.matches(Regex("^[0-9a-f]+$")))
        assertNotEquals("k=aabb&i=ccdd&h=deadbeef&s=1", encrypted)
    }

    @Test
    fun `rsaEncryptChunkedToHex chunks plaintext larger than one RSA block and round-trips via RSA decryption`() {
        // sign plaintext tipico (k=32 hex + i=32 hex + h=32 hex + s=numero) excede facilmente um
        // unico bloco PKCS1v1.5 de uma chave RSA 1024-bit (~117 bytes utilizaveis) quando o
        // chunkSizeBytes configurado for pequeno o suficiente para forcar mais de um pedaco.
        val plaintext = "k=" + "a".repeat(32) + "&i=" + "b".repeat(32) + "&h=" + "c".repeat(32) + "&s=999999"
        val encryptedHex = TpLinkStokLuciCrypto.rsaEncryptChunkedToHex(
            TestRsaKeyFixture.MODULUS_HEX,
            TestRsaKeyFixture.EXPONENT_HEX,
            plaintext,
            chunkSizeBytes = 53,
        )

        val blockHexSize = ((java.math.BigInteger(TestRsaKeyFixture.MODULUS_HEX, 16).bitLength() + 7) / 8) * 2
        val expectedChunks = (plaintext.toByteArray(Charsets.UTF_8).size + 52) / 53
        assertEquals(expectedChunks * blockHexSize, encryptedHex.length)
    }

    @Test
    fun `aesCbcEncrypt then aesCbcDecrypt round-trips the original plaintext`() {
        val key = TpLinkStokLuciCrypto.generateRandomBytes(TpLinkStokLuciCrypto.AES_KEY_SIZE_BYTES)
        val iv = TpLinkStokLuciCrypto.generateRandomBytes(TpLinkStokLuciCrypto.AES_IV_SIZE_BYTES)
        val plaintext = """{"stok":"abc123"}"""

        val ciphertext = TpLinkStokLuciCrypto.aesCbcEncrypt(key, iv, plaintext.toByteArray(Charsets.UTF_8))
        val decrypted = TpLinkStokLuciCrypto.aesCbcDecrypt(key, iv, ciphertext)

        assertEquals(plaintext, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `buildLoginPlaintext embeds the rsa-encrypted password hex, never confirm=true, never a username field`() {
        val rsaEncryptedPasswordHex = "ab".repeat(128)
        val plaintext = TpLinkStokLuciCrypto.buildLoginPlaintext(rsaEncryptedPasswordHex)

        assertEquals("operation=login&password=$rsaEncryptedPasswordHex", plaintext)
        assertTrue(plaintext.contains("password=$rsaEncryptedPasswordHex"))
        assertTrue(plaintext.contains("operation=login"))
        assertTrue("nao deve mais conter confirm=true, confirmado por captura real do texto plano", !plaintext.contains("confirm=true"))
        assertTrue("nao deve conter campo de usuario, firmware autentica so por senha", !plaintext.contains("username"))
    }

    @Test
    fun `buildSignPlaintext embeds aes key, iv, md5 hash of password and seq`() {
        val plaintext = TpLinkStokLuciCrypto.buildSignPlaintext("aeskeyhex", "aesivhex", "minhasenha", 42L)

        assertTrue(plaintext.startsWith("k=aeskeyhex&i=aesivhex&h="))
        assertTrue(plaintext.endsWith("&s=42"))
        assertTrue(plaintext.contains(TpLinkStokLuciCrypto.md5Hex("minhasenha")))
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
