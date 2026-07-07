package com.nethal.core.driver.family.tplink.stokluci

import com.nethal.core.auth.AuthenticationStrategy
import com.nethal.core.protocol.http.HttpTransport
import java.io.IOException

/**
 * Motivo de falha de login da plataforma `tplink-stok-luci`. Corrigido a partir de evidência ao
 * vivo real (ver KDoc de [TpLinkStokLuciAuthenticationClient]) — o vocabulário em si (nomes de
 * motivo) não mudou desde a implementação anterior, só o mecanismo que dispara cada um.
 */
internal enum class TpLinkStokLuciLoginFailureReason {
    AUTH_ENDPOINT_UNAVAILABLE,
    INVALID_CREDENTIALS,
    UNEXPECTED_RESPONSE,
}

internal class TpLinkStokLuciLoginException(
    val reason: TpLinkStokLuciLoginFailureReason,
    message: String,
) : IOException(message)

/**
 * Sessão autenticada contra a WebUI de equipamentos da plataforma `tplink-stok-luci` (hoje só o
 * profile `tplink_archer_c6_stok_v1`).
 *
 * **Protocolo real confirmado por evidência ao vivo definitiva** (terceira rodada, 2026-07-07)
 * contra o hardware físico do Luiz (Archer C6 v2.0, firmware
 * `1.1.10 Build 20230830 rel.69433(5553)`): captura via Playwright, com `page.on('response')`
 * interceptando o corpo completo de request E response de cada chamada `cgi-bin/luci` durante um
 * login real bem-sucedido — inclusive chamadas autenticadas pós-login, com `stok` real
 * funcionando.
 *
 * **Correção sobre a rodada anterior** (manifesto `catalog-2026.07.17.json`): aquela rodada tinha
 * concluído, por engano, que existia **uma única** chamada de preparação (`form=auth`) com uma
 * única chave RSA reaproveitada para cifrar a senha e assinar o envelope `sign`. Essa conclusão
 * veio de uma captura **incompleta** feita com a extensão Chrome, que pulou a chamada `form=keys`
 * por algum motivo de cache/estado do navegador naquela tentativa específica — não porque o
 * protocolo real só tem uma chamada. A captura completa via Playwright desta rodada confirma que
 * **existem sim duas chamadas de preparação com duas chaves RSA distintas**, exatamente como a lib
 * de referência `tplinkrouterc6u` sempre documentou.
 *
 * Passos do login real confirmado:
 *
 * 1. `POST {host}/cgi-bin/luci/;stok=/login?form=keys`, corpo `operation=read`. Resposta real:
 *    `{"success":true,"data":{"password":["<256 caracteres hex>","010001"],"mode":"router",
 *    "username":""}}` — `data.password` é a chave RSA (módulo 256 caracteres hex = 128 bytes = RSA
 *    1024-bit) usada **só para cifrar a senha**.
 * 2. `POST {host}/cgi-bin/luci/;stok=/login?form=auth`, corpo `operation=read`. Resposta real:
 *    `{"success":true,"data":{"key":["<128 caracteres hex>","010001"],"seq":<número>}}` —
 *    `data.key` é uma chave RSA **diferente** da do passo 1 (módulo 128 caracteres hex = 64 bytes =
 *    RSA 512-bit), usada **só para assinar o envelope `sign`**. `data.seq` é o número de sequência.
 * 3. Gera aleatoriamente por sessão de login: chave AES-128 e IV AES-128 ([TpLinkStokLuciCrypto]).
 * 4. Cifra a senha em RSA (chave do passo 1, PKCS1v1.5) e converte o resultado para hex.
 *    `data` = [TpLinkStokLuciCrypto.buildLoginPlaintext] (`operation=login&password=<senha cifrada
 *    em RSA, em hex>`, sem `&confirm=true` — confirmado byte a byte por captura real do texto plano
 *    via hook em `CryptoJS.AES.encrypt`; ver KDoc de [TpLinkStokLuciCrypto]), cifrado com
 *    AES-CBC/PKCS7 usando a chave/IV gerados no passo 3, resultado em base64.
 * 5. `sign` = [TpLinkStokLuciCrypto.buildSignPlaintext] (`k=<chave AES hex>&i=<IV AES hex>&h=<hash>
 *    &s=<seq>`, hash = `md5(password)` — hipótese não confirmada byte a byte, ver KDoc do crypto),
 *    cifrado em pedaços com a chave RSA do passo 2 (512-bit, PKCS1v1.5, chunk de 53 bytes = 64 - 11
 *    bytes de overhead), resultado em hex.
 * 6. `POST {host}/cgi-bin/luci/;stok=/login?form=login`, corpo `sign=<hex>&data=<base64
 *    URL-encoded>` — confirmado batendo com um HAR real de outra sessão de login também
 *    bem-sucedida (mesmo par de campos `sign`/`data`, nunca `operation=login&password=...`).
 * 7. Resposta real: `{"data": "<base64>"}` — sem campo `success` visível. O corpo é decifrado com a
 *    mesma chave/IV AES da sessão (passo 3); o texto plano resultante é um JSON contendo `stok`.
 * 8. Cookie `sysauth`: não capturamos headers de resposta reais nesta rodada (só corpo) — mantemos
 *    compatibilidade lendo o cookie se vier (`Set-Cookie`), mas a sessão NÃO depende só dele: o
 *    `stok` extraído do corpo decifrado é suficiente para autenticar chamadas subsequentes.
 *
 * (Existem outras chamadas reais no meio do fluxo capturado — `form=check_factory_default`,
 * `form=password` (`{"enable_rec":false}`), `locale?form=multilang`, `domain_login?form=dlogin` —
 * todas irrelevantes para o handshake de cripto, são checks de UI/cloud/idioma. Não precisam ser
 * replicadas por este client.)
 *
 * A credencial nunca é retida além da chamada de login: `password` só existe como parâmetro local
 * de [login], nunca vira campo desta classe nem é logada. Só o [TpLinkStokLuciSession] resultante
 * (token `stok` + cookie `sysauth` opcional, nenhum segredo por si só) fica em memória.
 *
 * **Suposições que ainda restam sem confirmação byte a byte** (ver KDoc de [TpLinkStokLuciCrypto]
 * para o detalhe completo): se o hash do `sign` usa só a senha ou alguma outra derivação. O próximo
 * teste real do Luiz (`gradlew :core:tplinkC6StokManualCheck`) valida ou refuta.
 */
internal class TpLinkStokLuciAuthenticationClient(
    private val host: String,
    private val transport: HttpTransport,
    private val rsaChunkSizeBytes: Int = TpLinkStokLuciCrypto.DEFAULT_RSA_CHUNK_SIZE_BYTES,
) : AuthenticationStrategy<TpLinkStokLuciSession> {

    private val baseUrl = "http://$host"
    private val loginBaseUrl = "$baseUrl/cgi-bin/luci/;stok=/login"

    private var session: TpLinkStokLuciSession? = null

    val isAuthenticated: Boolean get() = session != null

    /** Chave RSA de cifra de senha (1024-bit), obtida em `form=keys`. Guardada para uso futuro por chamadas autenticadas (etapa fora de escopo desta entrega). */
    var passwordKey: TpLinkStokLuciPasswordKey? = null
        private set

    /** Chave RSA de assinatura (512-bit) + sequência, obtidas em `form=auth`. Guardadas para uso futuro por chamadas autenticadas (etapa fora de escopo desta entrega). */
    var authKeys: TpLinkStokLuciAuthKeys? = null
        private set

    @Throws(IOException::class)
    override fun login(username: String, password: String): TpLinkStokLuciSession {
        val keysResponse = transport.post("$loginBaseUrl?form=keys", "operation=read")
        if (keysResponse.statusCode != 200) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                "endpoint form=keys indisponivel: status=${keysResponse.statusCode}",
            )
        }
        val parsedPasswordKey = TpLinkStokLuciResponseParser.parsePasswordKey(keysResponse.body)
            ?: throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                "resposta de form=keys sem chave RSA de senha reconhecivel",
            )
        passwordKey = parsedPasswordKey

        val authResponse = transport.post("$loginBaseUrl?form=auth", "operation=read")
        if (authResponse.statusCode != 200) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.AUTH_ENDPOINT_UNAVAILABLE,
                "endpoint form=auth indisponivel: status=${authResponse.statusCode}",
            )
        }
        val parsedAuthKeys = TpLinkStokLuciResponseParser.parseAuthKeys(authResponse.body)
            ?: throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                "resposta de form=auth sem chave RSA/seq reconheciveis",
            )
        authKeys = parsedAuthKeys

        val aesKey = TpLinkStokLuciCrypto.generateRandomBytes(TpLinkStokLuciCrypto.AES_KEY_SIZE_BYTES)
        val aesIv = TpLinkStokLuciCrypto.generateRandomBytes(TpLinkStokLuciCrypto.AES_IV_SIZE_BYTES)
        val aesKeyHex = TpLinkStokLuciCrypto.bytesToHex(aesKey)
        val aesIvHex = TpLinkStokLuciCrypto.bytesToHex(aesIv)

        val rsaEncryptedPasswordHex = TpLinkStokLuciCrypto.rsaEncryptChunkedToHex(
            modulusHex = parsedPasswordKey.key.modulusHex,
            exponentHex = parsedPasswordKey.key.exponentHex,
            plaintext = password,
            chunkSizeBytes = rsaChunkSizeBytes,
        )
        val loginPlaintext = TpLinkStokLuciCrypto.buildLoginPlaintext(rsaEncryptedPasswordHex)
        val encryptedData = TpLinkStokLuciCrypto.aesCbcEncrypt(aesKey, aesIv, loginPlaintext.toByteArray(Charsets.UTF_8))
        val dataBase64 = TpLinkStokLuciCrypto.base64Encode(encryptedData)

        val signPlaintext = TpLinkStokLuciCrypto.buildSignPlaintext(aesKeyHex, aesIvHex, password, parsedAuthKeys.seq)
        val signHex = TpLinkStokLuciCrypto.rsaEncryptChunkedToHex(
            modulusHex = parsedAuthKeys.key.modulusHex,
            exponentHex = parsedAuthKeys.key.exponentHex,
            plaintext = signPlaintext,
            chunkSizeBytes = rsaChunkSizeBytes,
        )

        val encodedData = java.net.URLEncoder.encode(dataBase64, "UTF-8")
        val loginBody = "sign=$signHex&data=$encodedData"
        val loginResponse = transport.post("$loginBaseUrl?form=login", loginBody)

        if (loginResponse.statusCode == 401 || loginResponse.statusCode == 403) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS,
                "login falhou: status=${loginResponse.statusCode}",
            )
        }
        if (loginResponse.statusCode != 200) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                "login falhou: status=${loginResponse.statusCode}",
            )
        }

        val ciphertextBase64 = TpLinkStokLuciResponseParser.parseLoginCiphertextBase64(loginResponse.body)
            ?: throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS,
                "login falhou: resposta sem campo data (credencial provavelmente invalida)",
            )

        val stok = runCatching {
            val decryptedBytes = TpLinkStokLuciCrypto.aesCbcDecrypt(aesKey, aesIv, TpLinkStokLuciCrypto.base64Decode(ciphertextBase64))
            TpLinkStokLuciResponseParser.parseDecryptedStok(String(decryptedBytes, Charsets.UTF_8))
        }.getOrNull()

        val sysauthCookie = TpLinkStokLuciCrypto.extractSysauthCookie(loginResponse.headers["set-cookie"])
            ?: loginResponse.cookies["sysauth"]

        if (stok.isNullOrBlank()) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS,
                "login falhou: nao foi possivel decifrar stok da resposta (credencial provavelmente invalida)",
            )
        }

        val newSession = TpLinkStokLuciSession(stok = stok, sysauthCookie = sysauthCookie)
        session = newSession
        return newSession
    }

    override fun authenticatedHeaders(session: TpLinkStokLuciSession): Map<String, String> =
        session.sysauthCookie?.let { mapOf("Cookie" to "sysauth=$it") } ?: emptyMap()

    /**
     * Faz uma chamada de dados autenticada contra `{host}/cgi-bin/luci/;stok=<token>/<path>`,
     * reenviando o cookie `sysauth` se presente. Cobre só leitura simples (sem o envelope
     * AES/assinatura completo de chamadas autenticadas, fora de escopo desta entrega) — mantido
     * inalterado desde a rodada anterior, ainda não validado contra hardware real.
     */
    @Throws(IOException::class)
    fun fetchAuthenticated(path: String, query: String): String {
        val currentSession = session
        check(currentSession != null) { "fetchAuthenticated chamado antes de login() bem-sucedido" }
        val url = "$baseUrl/cgi-bin/luci/;stok=${currentSession.stok}/$path?$query"
        val response = transport.post(url, "operation=read", authenticatedHeaders(currentSession))
        return response.body
    }
}
