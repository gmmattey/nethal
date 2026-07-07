package com.nethal.core.driver.family.tplink.stokluci

import com.nethal.core.auth.AuthenticationStrategy
import com.nethal.core.protocol.http.HttpTransport
import java.io.IOException

/**
 * Motivo de falha de login da plataforma `tplink-stok-luci`. Vocabulário fechado com base no
 * entendimento de protocolo obtido por pesquisa em código aberto de terceiros (pacote
 * `tplinkrouterc6u`, GPL-3.0) — ver [TpLinkStokLuciCrypto] para a citação completa. Este mecanismo
 * NUNCA foi testado contra hardware real ainda (profile `tplink_archer_c6_stok_v1` continua
 * `DISCOVERY_ONLY`) — o vocabulário abaixo é a melhor hipótese de tratamento de erro disponível,
 * sujeito a correção assim que o primeiro teste real acontecer.
 */
internal enum class TpLinkStokLuciLoginFailureReason {
    KEYS_ENDPOINT_UNAVAILABLE,
    INVALID_CREDENTIALS,
    UNEXPECTED_RESPONSE,
}

internal class TpLinkStokLuciLoginException(
    val reason: TpLinkStokLuciLoginFailureReason,
    message: String,
) : IOException(message)

/**
 * Sessão autenticada contra a WebUI de equipamentos da plataforma `tplink-stok-luci` (hoje só o
 * profile `tplink_archer_c6_stok_v1`, `DISCOVERY_ONLY` — sem teste real de login ainda).
 *
 * Protocolo entendido a partir da leitura do código-fonte real do pacote Python `tplinkrouterc6u`
 * (GPL-3.0, https://pypi.org/project/tplinkrouterc6u/), classe `TplinkEncryption`
 * (`tplinkrouterc6u/client/c6u.py`) e `EncryptionWrapper` (`tplinkrouterc6u/common/encryption.py`)
 * — citado aqui só como referência de existência/forma do protocolo (mesma convenção de citação já
 * usada por `NokiaAuthCrypto`/`NokiaAuthenticationClient` ao citar o driver de produção do
 * SignallQ). Esta implementação é original, escrita do zero para o vocabulário/arquitetura do
 * NetHAL — nenhum código daquele pacote foi copiado literalmente.
 *
 * Passos do login (etapa 6 — chamadas autenticadas via AES/assinatura RSA — deliberadamente fora
 * de escopo, só documentada em KDoc, ver nota ao final):
 *
 * 1. `POST {host}/cgi-bin/luci/;stok=/login?form=keys` (`operation=read`, sem corpo) → chave RSA
 *    (módulo/expoente hex) usada só para cifrar a senha no login.
 * 2. `POST {host}/cgi-bin/luci/;stok=/login?form=auth` (`operation=read`, sem corpo) → sequência +
 *    chave RSA de assinatura (diferente da do passo 1), usada só para assinar chamadas
 *    autenticadas pós-login — não usada nesta implementação (etapa 6 fora de escopo), mas
 *    consultada aqui para ficar disponível caso a etapa 6 seja implementada depois sem precisar
 *    reabrir este client.
 * 3. Senha cifrada com RSA/PKCS1v1.5 (não "sem padding") usando a chave do passo 1, resultado em
 *    hex minúsculo.
 * 4. `POST {host}/cgi-bin/luci/;stok=/login?form=login`,
 *    `Content-Type: application/x-www-form-urlencoded`, header `Referer: {host}/webpages/index.html`,
 *    corpo `operation=login&password=<hex_cifrado>&confirm=true` — **sem campo de usuário**, bate
 *    com a evidência real capturada contra a unidade física do Luiz (formulários de login só com
 *    campo `password`).
 * 5. Sucesso: corpo JSON com `stok` em `data.stok`; header `Set-Cookie` contém `sysauth=<valor>;`.
 *
 * A credencial nunca é retida além da chamada de login: `password` só existe como parâmetro local
 * de [login], nunca vira campo desta classe nem é logada. Só o [TpLinkStokLuciSession] resultante
 * (token `stok` + cookie `sysauth`, não segredo por si só) fica em memória.
 */
internal class TpLinkStokLuciAuthenticationClient(
    private val host: String,
    private val transport: HttpTransport,
) : AuthenticationStrategy<TpLinkStokLuciSession> {

    private val baseUrl = "http://$host"
    private val loginBaseUrl = "$baseUrl/cgi-bin/luci/;stok=/login"

    private var session: TpLinkStokLuciSession? = null

    val isAuthenticated: Boolean get() = session != null

    /**
     * Chave RSA de assinatura + sequência, obtida no passo 2 do handshake. Guardada para uso
     * futuro por chamadas autenticadas (etapa 6, fora de escopo desta entrega) — não usada por
     * [login] em si.
     */
    var authKeys: TpLinkStokLuciAuthKeys? = null
        private set

    @Throws(IOException::class)
    override fun login(username: String, password: String): TpLinkStokLuciSession {
        val keysResponse = transport.post("$loginBaseUrl?form=keys&operation=read", "")
        if (keysResponse.statusCode != 200) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.KEYS_ENDPOINT_UNAVAILABLE,
                "endpoint form=keys indisponivel: status=${keysResponse.statusCode}",
            )
        }
        val passwordKey = TpLinkStokLuciResponseParser.parsePasswordEncryptionKey(keysResponse.body)
            ?: throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.UNEXPECTED_RESPONSE,
                "resposta de form=keys sem chave RSA reconhecivel",
            )

        // Passo 2 — chave de assinatura, não usada no login em si (etapa 6, fora de escopo). Uma
        // falha aqui não deve impedir o login de prosseguir: é dado preparatório, não requisito do
        // handshake de autenticação.
        val authResponse = runCatching { transport.post("$loginBaseUrl?form=auth&operation=read", "") }.getOrNull()
        authKeys = authResponse?.body?.let { TpLinkStokLuciResponseParser.parseAuthKeys(it) }

        val encryptedPasswordHex = TpLinkStokLuciCrypto.rsaEncryptPkcs1ToHex(
            modulusHex = passwordKey.modulusHex,
            exponentHex = passwordKey.exponentHex,
            plaintext = password,
        )

        val loginBody = "operation=login&password=$encryptedPasswordHex&confirm=true"
        val loginResponse = transport.post(
            "$loginBaseUrl?form=login",
            loginBody,
        )

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

        val stok = TpLinkStokLuciResponseParser.parseLoginStok(loginResponse.body)
        val sysauthCookie = TpLinkStokLuciCrypto.extractSysauthCookie(loginResponse.headers["set-cookie"])
            ?: loginResponse.cookies["sysauth"]

        if (stok.isNullOrBlank() || sysauthCookie.isNullOrBlank()) {
            throw TpLinkStokLuciLoginException(
                TpLinkStokLuciLoginFailureReason.INVALID_CREDENTIALS,
                "login falhou: resposta sem stok/sysauth (credencial provavelmente invalida)",
            )
        }

        val newSession = TpLinkStokLuciSession(stok = stok, sysauthCookie = sysauthCookie)
        session = newSession
        return newSession
    }

    override fun authenticatedHeaders(session: TpLinkStokLuciSession): Map<String, String> =
        mapOf("Cookie" to "sysauth=${session.sysauthCookie}")

    /**
     * Faz uma chamada de dados autenticada contra `{host}/cgi-bin/luci/;stok=<token>/<path>`,
     * reenviando o cookie `sysauth` validado por [login]. Cobre só leitura simples (sem o envelope
     * AES/assinatura da etapa 6, fora de escopo) — suficiente para o endpoint de status geral
     * (`admin/status?form=all&operation=read`), que a pesquisa de terceiros documenta como
     * aceitando leitura sem envelope adicional para alguns `form`s.
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
