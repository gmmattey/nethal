package com.nethal.core.driver.family.tplink.stokluci

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * Primitivas criptográficas do handshake de login da plataforma `tplink-stok-luci` (ver
 * `docs/architecture/hal-layering-model.md` §9.1 e profile `tplink_archer_c6_stok_v1` no
 * catálogo): RSA com padding PKCS#1 v1.5 sobre a senha em claro, usando uma chave pública
 * (módulo/expoente em hex) obtida de um endpoint de leitura dedicado antes do login em si.
 *
 * O entendimento deste mecanismo vem de pesquisa em código aberto de terceiros — o pacote Python
 * `tplinkrouterc6u` (GPL-3.0, https://pypi.org/project/tplinkrouterc6u/), especificamente a classe
 * `TplinkEncryption` (`tplinkrouterc6u/client/c6u.py`) e `EncryptionWrapper`
 * (`tplinkrouterc6u/common/encryption.py`) — usado aqui só como referência da existência e forma
 * geral do protocolo (módulo/expoente hex, padding PKCS1v1.5, ausência de campo de usuário no
 * login). Nenhum código daquele projeto foi copiado; esta é uma reimplementação original usando
 * `javax.crypto`/`java.security` padrão do JDK, mesma abordagem já usada por `NokiaAuthCrypto` e
 * `TplinkAuthCrypto` para os respectivos mecanismos RSA+AES daqueles drivers.
 *
 * Este handshake NUNCA foi testado contra o hardware real do Luiz nesta rodada — ver limitação
 * documentada em `TpLinkStokLuciDriverFamily` e no catálogo (`stage: DISCOVERY_ONLY`, sem promoção
 * até um teste real de login bem-sucedido).
 */
internal object TpLinkStokLuciCrypto {

    /**
     * Constrói uma `RSAPublicKey` a partir do módulo (`n`) e expoente (`e`) em hex, exatamente
     * como devolvidos pelo endpoint `form=keys` (`data.password[0]` = módulo, `data.password[1]` =
     * expoente, segundo a pesquisa de terceiros citada no KDoc da classe).
     */
    fun buildRsaPublicKey(modulusHex: String, exponentHex: String): java.security.PublicKey {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    /**
     * Cifra [plaintext] com RSA/ECB/PKCS1Padding usando a chave pública construída por
     * [buildRsaPublicKey], devolvendo o resultado como string hexadecimal minúscula — mesmo
     * encoding usado pelo firmware para o campo `password` do corpo de login (`operation=login&
     * password=<hex>&confirm=true`).
     *
     * PKCS#1 v1.5 (não "sem padding"): diferente do mecanismo antigo do C6
     * (`tplink-encrypted-web`, RSA sem padding via `TplinkAuthCrypto`), a pesquisa de terceiros
     * indica que este mecanismo usa padding padrão PKCS1v1.5 — por isso não reaproveita
     * `TplinkAuthCrypto`/`NokiaAuthCrypto`, ambos com esquemas diferentes.
     */
    fun rsaEncryptPkcs1ToHex(modulusHex: String, exponentHex: String, plaintext: String): String {
        val publicKey = buildRsaPublicKey(modulusHex, exponentHex)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    /**
     * Extrai o valor de `sysauth` do header `Set-Cookie` bruto — feito via regex em vez de um
     * parser de cookie genérico porque a pesquisa de terceiros documenta o valor chegando
     * misturado com outros atributos de cookie (`Path`, `HttpOnly`, etc.) na mesma linha de
     * header, e este projeto não depende de nenhuma lib de parsing de cookie HTTP completo.
     */
    fun extractSysauthCookie(setCookieHeader: String?): String? {
        if (setCookieHeader.isNullOrBlank()) return null
        return Regex("""sysauth=([^;]+)""").find(setCookieHeader)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
