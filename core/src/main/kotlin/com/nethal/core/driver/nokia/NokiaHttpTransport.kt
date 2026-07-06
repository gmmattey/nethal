package com.nethal.core.driver.nokia

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val NOKIA_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

internal data class NokiaHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
)

/**
 * Transporte HTTP do driver Nokia, isolado atrás de interface para permitir testes determinísticos
 * com fakes (o `core` é JVM puro, sem hardware real disponível neste ambiente — ver testes em
 * `NokiaAuthenticationClientTest`). `DefaultNokiaHttpTransport` é a implementação real, equivalente
 * em timeouts/retries/redirects ao driver de produção do SignallQ.
 */
internal interface NokiaHttpTransport {
    @Throws(IOException::class)
    fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): NokiaHttpResponse

    @Throws(IOException::class)
    fun post(url: String, body: String, initCookies: Map<String, String> = emptyMap()): NokiaHttpResponse
}

/**
 * Timeouts equivalentes ao driver de produção do SignallQ (evidência real de campo, ver
 * fingerprintEvidence do profile `nokia_g1425gb_v1`): connect 15s, read 30s em GET / 60s em POST.
 */
internal class DefaultNokiaHttpTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val getReadTimeoutMillis: Int = 30_000,
    private val postReadTimeoutMillis: Int = 60_000,
) : NokiaHttpTransport {

    override fun get(url: String, extraHeaders: Map<String, String>): NokiaHttpResponse {
        val accumulatedCookies = mutableMapOf<String, String>()
        var currentUrl = url
        var hops = 0

        while (hops < 5) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = getReadTimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", NOKIA_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                if (accumulatedCookies.isNotEmpty()) {
                    setRequestProperty("Cookie", accumulatedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val response = readResponse(connection)
            accumulatedCookies.putAll(response.cookies)

            if (response.statusCode in 301..303 || response.statusCode == 307 || response.statusCode == 308) {
                val location = response.headers["location"] ?: break
                currentUrl = if (location.startsWith("http")) location else {
                    val base = URL(currentUrl)
                    "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}$location"
                }
                hops++
                continue
            }
            return response.copy(cookies = accumulatedCookies)
        }
        throw IOException("demasiados redirecionamentos ao acessar $url")
    }

    override fun post(url: String, body: String, initCookies: Map<String, String>): NokiaHttpResponse {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMillis
            readTimeout = postReadTimeoutMillis
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", NOKIA_USER_AGENT)
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Connection", "close")
            setFixedLengthStreamingMode(bodyBytes.size)
            if (initCookies.isNotEmpty()) {
                setRequestProperty("Cookie", initCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            outputStream.write(bodyBytes)
            outputStream.flush()
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): NokiaHttpResponse {
        return try {
            val statusCode = connection.responseCode
            val body = (if (statusCode >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            val headers = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, values) ->
                if (key != null) headers[key.lowercase()] = values.joinToString(", ")
            }
            NokiaHttpResponse(statusCode, body, headers, parseCookies(connection))
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
