package com.nethal.core.driver.tplink

/**
 * Fake determinístico de [TplinkHttpTransport] para o Archer C20, protocolo real confirmado por
 * captura via DevTools contra unidade física do Luiz (2026-07-06, ver SIG-337/SIG-338).
 *
 * Diferente do mecanismo anterior (POST de login separado + GET de páginas), o protocolo real usa
 * um único dispatcher `POST /cgi?...`, sempre com o cookie `Authorization` (via `cookies`).
 * Este fake responde com base na credencial recebida: se bater com [expectedAuthorizationCookie],
 * devolve a resposta configurada para o corpo de request recebido (via [responsesByRequestBody]);
 * caso contrário, simula uma falha HTTP 401 (comportamento padrão de HTTP Basic Auth, não
 * capturado literalmente mas é o padrão do mecanismo).
 */
internal class FakeTplinkC20HttpTransport(
    private val expectedAuthorizationCookie: String? = null,
    private val responsesByRequestBody: Map<String, TplinkHttpResponse> = emptyMap(),
    private val defaultResponse: TplinkHttpResponse? = null,
) : TplinkHttpTransport {

    var getCallCount = 0
        private set
    var postCallCount = 0
        private set
    var lastRequestBody: String? = null
        private set
    var lastCookieHeaderSent: String? = null
        private set

    override fun get(url: String, extraHeaders: Map<String, String>): TplinkHttpResponse {
        getCallCount++
        return TplinkHttpResponse(404, "", emptyMap(), emptyMap())
    }

    override fun post(url: String, body: String, cookies: Map<String, String>): TplinkHttpResponse {
        postCallCount++
        lastRequestBody = body
        val cookieValue = cookies["Authorization"]
        lastCookieHeaderSent = cookieValue

        if (expectedAuthorizationCookie != null && cookieValue != expectedAuthorizationCookie) {
            return TplinkHttpResponse(401, "", emptyMap(), emptyMap())
        }

        return responsesByRequestBody[body]
            ?: defaultResponse
            ?: TplinkHttpResponse(200, "[error]0", emptyMap(), emptyMap())
    }
}

/** Response sintética de IGD_DEV_INFO — formato real capturado, dados fictícios (fixture de sucesso de login/leitura). */
internal fun deviceInfoOnlyResponse(): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 200,
    body = """
        [1,1,0,0,0,0]0
        modelName=Archer C20
        description=Roteador Wireless Dual Band AC750
        X_TP_isFD=1
        [cgi]1
        var userType="Admin";
        var bSecured=0;
        var clientLocal=1;
        var clientIp="192.168.0.100";
        var clientMac="AA:BB:CC:DD:EE:FF";
        ${'$'}.ret=0;
        [error]0
    """.trimIndent(),
    headers = emptyMap(),
    cookies = emptyMap(),
)

/** Response sintética do bundle IGD_DEV_INFO+ETH_SWITCH+SYS_MODE (índices 0,1,2) — formato real capturado, dados fictícios. */
internal fun deviceInfoBundleResponse(): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 200,
    body = """
        [1,1,0,0,0,0]0
        modelName=Archer C20
        description=Roteador Wireless Dual Band AC750
        X_TP_isFD=1
        [1,1,0,0,0,0]1
        numberOfVirtualPorts=4
        [1,1,0,0,0,0]2
        mode=ETH
        [cgi]3
        var userType="Admin";
        var bSecured=0;
        ${'$'}.ret=0;
        [error]0
    """.trimIndent(),
    headers = emptyMap(),
    cookies = emptyMap(),
)

/** Response sintética de LAN_WLAN com duas linhas (dois rádios) — formato real capturado, dados fictícios. */
internal fun lanWlanResponse(): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 200,
    body = """
        [1,1,0,0,0,0]0
        name=wlan0
        SSID=Casa-2.4G
        [1,2,0,0,0,0]0
        name=wlan5
        SSID=Casa-5G
        [error]0
    """.trimIndent(),
    headers = emptyMap(),
    cookies = emptyMap(),
)

/** Response sintética de LAN_HOST_ENTRY com um cliente conectado — formato real capturado, dados fictícios. */
internal fun lanHostEntryResponse(): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 200,
    body = """
        [1,0,0,0,0,0]0
        leaseTimeRemaining=6231
        MACAddress=AA:BB:CC:DD:EE:FF
        hostName=Notebook-Teste
        IPAddress=192.168.0.100
        [error]0
    """.trimIndent(),
    headers = emptyMap(),
    cookies = emptyMap(),
)

internal fun globalErrorResponse(code: Int = 1): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 200,
    body = "[error]$code",
    headers = emptyMap(),
    cookies = emptyMap(),
)

internal fun httpUnauthorizedResponse(): TplinkHttpResponse = TplinkHttpResponse(
    statusCode = 401,
    body = "",
    headers = emptyMap(),
    cookies = emptyMap(),
)
