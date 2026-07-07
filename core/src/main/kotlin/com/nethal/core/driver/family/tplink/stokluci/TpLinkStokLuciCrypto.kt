package com.nethal.core.driver.family.tplink.stokluci

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitivas criptográficas do handshake de login da plataforma `tplink-stok-luci`, corrigidas a
 * partir de evidência ao vivo **definitiva** (terceira rodada, 2026-07-07): captura via Playwright,
 * com `page.on('response')` interceptando o corpo completo de request E response de cada chamada
 * `cgi-bin/luci` durante um login real bem-sucedido contra o hardware físico do Luiz (Archer C6
 * v2.0, firmware `1.1.10 Build 20230830 rel.69433(5553)`) — inclusive chamadas autenticadas
 * pós-login, com `stok` real funcionando.
 *
 * **Duas chaves RSA distintas, confirmadas por captura completa de request+response**:
 * - `form=keys` → `data.password = [modulus_hex, exponent_hex]`, módulo de 256 caracteres hex = 128
 *   bytes = **RSA 1024-bit**, usada só para cifrar a senha.
 * - `form=auth` → `data.key = [modulus_hex, exponent_hex]`, módulo de 128 caracteres hex = 64 bytes
 *   = **RSA 512-bit**, usada só para assinar o envelope `sign`. Expoente sempre `010001` (65537).
 *
 * Isso confirma exatamente o que a lib de referência `tplinkrouterc6u` sempre documentou. **A
 * rodada anterior (manifesto `catalog-2026.07.17.json`) concluiu, por engano, que só existia a
 * chamada `form=auth` e uma única chave RSA reaproveitada para senha e assinatura** — essa
 * conclusão veio de uma captura incompleta feita com a extensão Chrome, que pulou `form=keys` por
 * algum motivo de cache/estado do navegador naquela tentativa específica, não porque o protocolo
 * real só tem uma chamada. Este objeto foi corrigido para repor a chamada a `form=keys` e usar as
 * duas chaves corretamente.
 *
 * O tamanho de bloco do RSA em pedaços (chunking) do envelope `sign` é derivado diretamente do
 * tamanho real da chave de **assinatura** (`form=auth`, 512-bit = 64 bytes): PKCS1v1.5 tem 11 bytes
 * de overhead, logo `64 - 11 = 53 bytes` por bloco — o valor de [DEFAULT_RSA_CHUNK_SIZE_BYTES] já
 * usado nesta implementação bate exatamente com essa conta, confirmando que não é um valor
 * arbitrário.
 *
 * Confirmado por leitura estrutural (rodada anterior) do arquivo
 * `webpages/js/libs/tpEncrypt.1693386897767.js` real do equipamento: `CBC` presente, `GCM`/`ECB`
 * ausentes → **AES-CBC**, nunca GCM. `pkcs7` presente (case-insensitive) → padding **PKCS7**
 * (equivalente a PKCS5 para blocos de 16 bytes). `MD5` presente → hash de assinatura usa MD5.
 * `CryptoJS` presente → biblioteca de cifra client-side usada pelo firmware.
 *
 * **Confirmado byte a byte** (interceptação real da função `CryptoJS.AES.encrypt` na própria página
 * do equipamento, hook instalado via JavaScript, durante um login real bem-sucedido pelo
 * navegador): o texto plano que entra no AES para virar o campo `data` é exatamente
 * `operation=login&password=<256 caracteres hex>` — **sem** `&confirm=true` no final. Os 256
 * caracteres hex são a senha **já cifrada em RSA** com a chave de `form=keys` (1024-bit) — nunca a
 * chave de `form=auth` (512-bit), que é usada só para o envelope `sign`.
 *
 * **O que continua sem confirmação byte a byte** (melhor entendimento disponível, não confirmado):
 * - Se o hash MD5 do campo `sign` usa só a senha (`md5(password)`) ou alguma outra derivação — a
 *   evidência real confirma que não há campo de usuário em lugar nenhum do login, então a fórmula
 *   `md5(username+password)` da lib de referência não se aplica tal qual; assumimos `md5(password)`
 *   como hipótese mais simples compatível com a ausência de usuário, mas isso não foi confirmado
 *   por captura do texto plano do envelope `sign` (só os corpos JSON de request/response de
 *   `form=keys`/`form=auth` foram capturados nesta rodada, não o texto plano do `sign` antes de
 *   cifrar).
 *
 * O próximo teste real do Luiz (`gradlew :core:tplinkC6StokManualCheck`) valida ou refuta a
 * suposição restante do hash.
 */
internal object TpLinkStokLuciCrypto {

    /**
     * Tamanho de bloco (bytes) usado para cifrar o envelope `sign` em pedaços com RSA PKCS1v1.5 —
     * derivado do tamanho real da chave de assinatura de `form=auth` (RSA 512-bit = 64 bytes),
     * confirmado por captura completa via Playwright: `64 - 11` bytes de overhead de padding
     * PKCS1v1.5 = 53 bytes por bloco. Não é um valor arbitrário herdado da lib de referência sem
     * relação com este firmware — é exatamente o chunk size implícito no tamanho de chave real.
     */
    const val DEFAULT_RSA_CHUNK_SIZE_BYTES = 53

    /**
     * Tamanho de chave/IV AES (bytes) gerados por sessão de login — 16 bytes = 128 bits, **AES-128**
     * confirmado pelo hook real em `CryptoJS.AES.encrypt` (`keyWords: 4`; CryptoJS usa palavras de
     * 32 bits, logo 4 palavras = 16 bytes = 128 bits), nunca AES-256.
     */
    const val AES_KEY_SIZE_BYTES = 16
    const val AES_IV_SIZE_BYTES = 16

    fun buildRsaPublicKey(modulusHex: String, exponentHex: String): java.security.PublicKey {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    /**
     * Cifra [plaintext] com RSA/ECB/PKCS1Padding em pedaços de [chunkSizeBytes], concatenando o
     * resultado de cada pedaço em hex minúsculo — necessário porque o envelope `sign` (chave/IV
     * AES + hash + seq, ver [buildSignPlaintext]) costuma exceder o limite de um único bloco
     * PKCS1v1.5 para chaves RSA 1024-bit. Tamanho de pedaço parametrizável porque o valor real
     * (53 bytes, herdado da lib de referência) não foi confirmado contra este firmware
     * especificamente.
     */
    fun rsaEncryptChunkedToHex(
        modulusHex: String,
        exponentHex: String,
        plaintext: String,
        chunkSizeBytes: Int = DEFAULT_RSA_CHUNK_SIZE_BYTES,
    ): String {
        val publicKey = buildRsaPublicKey(modulusHex, exponentHex)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val hex = StringBuilder()
        var offset = 0
        while (offset < plaintextBytes.size) {
            val end = minOf(offset + chunkSizeBytes, plaintextBytes.size)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedChunk = cipher.doFinal(plaintextBytes.copyOfRange(offset, end))
            encryptedChunk.forEach { hex.append("%02x".format(it)) }
            offset = end
        }
        return hex.toString()
    }

    /** Gera [size] bytes aleatórios seguros — usado para chave/IV AES por sessão de login. */
    fun generateRandomBytes(size: Int, random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(size).also { random.nextBytes(it) }

    /** Converte bytes para uma string hex minúscula de exatamente `bytes.size * 2` caracteres — formato usado nos campos `k=`/`i=` do envelope `sign`. */
    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }

    /** AES-CBC/PKCS7 (equivalente a PKCS5 no JCE para blocos de 16 bytes) — confirmado por leitura estrutural de `tpEncrypt.js` real do equipamento (contém `CBC` e `pkcs7`, não `GCM`/`ECB`). */
    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    fun base64Encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun base64Decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Corpo em texto plano cifrado com AES-CBC/PKCS7 para virar o campo `data` do login —
     * `operation=login&password=<rsaEncryptedPasswordHex>`, **sem** `&confirm=true`. Confirmado
     * byte a byte por interceptação real de `CryptoJS.AES.encrypt` na própria página do equipamento
     * durante um login real bem-sucedido (ver KDoc do objeto). [rsaEncryptedPasswordHex] é a senha
     * já cifrada em RSA (hex) com a chave devolvida por `form=keys` (1024-bit) — nunca a chave de
     * `form=auth` (512-bit, usada só para o envelope `sign`), e nunca a senha em texto puro.
     */
    fun buildLoginPlaintext(rsaEncryptedPasswordHex: String): String =
        "operation=login&password=$rsaEncryptedPasswordHex"

    /**
     * Texto plano do envelope `sign`: `k=<chave AES hex>&i=<IV AES hex>&h=<hash>&s=<seq>`. O hash
     * usado é `md5(password)` — hipótese mais simples compatível com a ausência de campo de usuário
     * neste firmware (não confirmado por captura do texto plano real do envelope, já que só o
     * ciphertext foi observado ao vivo).
     */
    fun buildSignPlaintext(aesKeyHex: String, aesIvHex: String, password: String, seq: Long): String {
        val hash = md5Hex(password)
        return "k=$aesKeyHex&i=$aesIvHex&h=$hash&s=$seq"
    }

    /**
     * Extrai o valor de `sysauth` do header `Set-Cookie` bruto, se presente. A evidência ao vivo
     * desta rodada não capturou headers de resposta (só corpo) — mantido por compatibilidade com o
     * mecanismo antigo, mas o login não deve depender só deste cookie (ver
     * [TpLinkStokLuciAuthenticationClient]).
     */
    fun extractSysauthCookie(setCookieHeader: String?): String? {
        if (setCookieHeader.isNullOrBlank()) return null
        return Regex("""sysauth=([^;]+)""").find(setCookieHeader)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
