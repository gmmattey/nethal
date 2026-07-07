package com.nethal.core.driver.family.tplink.stokluci

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Codec do protocolo JSON real (não texto plano, diferente do `tplink-legacy-cgi`) da plataforma
 * `tplink-stok-luci`. Entendimento do formato vem de pesquisa em código aberto de terceiros
 * (pacote `tplinkrouterc6u`, GPL-3.0) — ver KDoc de [TpLinkStokLuciCrypto] para a citação completa
 * e a ressalva de que isto é reimplementação original, nunca cópia literal.
 *
 * Formato de resposta de `form=keys` (`operation=read`):
 * ```json
 * {"data": {"password": ["<modulus_hex>", "<exponent_hex>"]}}
 * ```
 *
 * Formato de resposta de `form=auth` (`operation=read`):
 * ```json
 * {"data": {"seq": 123, "key": ["<modulus_hex>", "<exponent_hex>"]}}
 * ```
 *
 * Formato de resposta de `form=login` bem-sucedido:
 * ```json
 * {"data": {"stok": "<token>"}}
 * ```
 * (o `stok` de sucesso vem acompanhado de header `Set-Cookie` contendo `sysauth=<valor>;`, extraído
 * separadamente por [TpLinkStokLuciCrypto.extractSysauthCookie] — não faz parte deste payload
 * JSON.)
 */
internal object TpLinkStokLuciResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class KeysResponseData(val password: List<String> = emptyList())

    @Serializable
    private data class KeysResponse(val data: KeysResponseData? = null)

    @Serializable
    private data class AuthResponseData(val seq: Long = 0L, val key: List<String> = emptyList())

    @Serializable
    private data class AuthResponse(val data: AuthResponseData? = null)

    @Serializable
    private data class LoginResponseData(val stok: String? = null)

    @Serializable
    private data class LoginResponse(val data: LoginResponseData? = null)

    /**
     * Extrai a chave RSA de cifra de senha da resposta de `form=keys`. Retorna `null` se a
     * resposta não tiver o formato esperado (JSON malformado, `data`/`password` ausente, ou lista
     * com menos de 2 elementos) — nunca lança, tratamento defensivo igual ao parser do
     * `tplink-legacy-cgi`.
     */
    fun parsePasswordEncryptionKey(body: String): TpLinkStokLuciRsaKey? {
        val parsed = runCatching { json.decodeFromString(KeysResponse.serializer(), body) }.getOrNull()
        val password = parsed?.data?.password ?: return null
        if (password.size < 2) return null
        return TpLinkStokLuciRsaKey(modulusHex = password[0], exponentHex = password[1])
    }

    /**
     * Extrai a sequência + chave RSA de assinatura da resposta de `form=auth`. Usada só para
     * preparar a etapa 6 (chamadas autenticadas, fora de escopo desta entrega) — não consumida
     * pelo fluxo de login em si.
     */
    fun parseAuthKeys(body: String): TpLinkStokLuciAuthKeys? {
        val parsed = runCatching { json.decodeFromString(AuthResponse.serializer(), body) }.getOrNull()
        val data = parsed?.data ?: return null
        if (data.key.size < 2) return null
        return TpLinkStokLuciAuthKeys(
            seq = data.seq,
            signingKey = TpLinkStokLuciRsaKey(modulusHex = data.key[0], exponentHex = data.key[1]),
        )
    }

    /** Extrai o `stok` da resposta de `form=login` bem-sucedido. `null` se ausente/malformado. */
    fun parseLoginStok(body: String): String? {
        val parsed = runCatching { json.decodeFromString(LoginResponse.serializer(), body) }.getOrNull()
        return parsed?.data?.stok?.takeIf { it.isNotBlank() }
    }
}

/** Vocabulário `err_code` observado em respostas de erro deste protocolo, quando presente no corpo (campo top-level `error_code`). */
@Serializable
internal data class TpLinkStokLuciErrorEnvelope(
    @SerialName("error_code") val errorCode: Int? = null,
)
