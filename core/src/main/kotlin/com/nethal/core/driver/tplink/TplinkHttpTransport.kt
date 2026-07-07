package com.nethal.core.driver.tplink

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TPLINK_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

internal data class TplinkHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
)

/**
 * Transporte HTTP do driver TP-Link, isolado atrás de interface para permitir testes
 * determinísticos com fakes — não há hardware real disponível neste ambiente (ver testes em
 * `TplinkAuthenticationClientTest`). `DefaultTplinkHttpTransport` é a implementação real.
 */
internal interface TplinkHttpTransport {
    @Throws(IOException::class)
    fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): TplinkHttpResponse

    @Throws(IOException::class)
    fun post(url: String, body: String, cookies: Map<String, String> = emptyMap()): TplinkHttpResponse
}

/**
 * Timeouts conservadores para WebUI local de roteador doméstico: connect 10s, read 20s. Sem
 * evidência de campo própria ainda (diferente do Nokia) — valores de partida, a confirmar/ajustar
 * no teste real contra a unidade do Luiz.
 */
internal class DefaultTplinkHttpTransport(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000,
) : TplinkHttpTransport {

    override fun get(url: String, extraHeaders: Map<String, String>): TplinkHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", TPLINK_USER_AGENT)
            setRequestProperty("Accept", "application/json, text/html,*/*;q=0.9")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return readResponse(connection)
    }

    override fun post(url: String, body: String, cookies: Map<String, String>): TplinkHttpResponse {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            setRequestProperty("User-Agent", TPLINK_USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Referer", run {
                val base = URL(url)
                "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}/"
            })
            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            setFixedLengthStreamingMode(bodyBytes.size)
            outputStream.write(bodyBytes)
            outputStream.flush()
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): TplinkHttpResponse {
        return try {
            val statusCode = connection.responseCode
            val body = (if (statusCode >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            val headers = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, values) ->
                if (key != null) headers[key.lowercase()] = values.joinToString(", ")
            }
            TplinkHttpResponse(statusCode, body, headers, parseCookies(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCookies(connection: HttpURLConnection): Map<String, String> {
        val result = mutableMapOf<String, String>()
        connection.headerFields["Set-Cookie"]?.forEach { cookie ->
            val firstPart = cookie.split(";").first().trim()
            val eqIndex = firstPart.indexOf('=')
            if (eqIndex > 0) {
                result[firstPart.substring(0, eqIndex).trim()] = firstPart.substring(eqIndex + 1).trim()
            }
        }
        return result
    }
}
