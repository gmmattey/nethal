package com.nethal.core.driver.family.tplink.gdprcgi

internal object TpLinkGdprCgiResponseParser {

    fun parseRsaParams(body: String): TpLinkGdprCgiRsaParams? {
        val exponent = Regex("""var\s+ee="([0-9a-fA-F]+)";""").find(body)?.groupValues?.get(1)
        val modulus = Regex("""var\s+nn="([0-9a-fA-F]+)";""").find(body)?.groupValues?.get(1)
        val sequence = Regex("""var\s+seq="(\d+)";""").find(body)?.groupValues?.get(1)?.toLongOrNull()
        if (exponent.isNullOrBlank() || modulus.isNullOrBlank() || sequence == null) return null
        return TpLinkGdprCgiRsaParams(modulusHex = modulus, exponentHex = exponent, sequence = sequence)
    }

    fun parseTokenId(body: String): String? =
        Regex("""var\s+token="([^"]+)";""").find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun parseReturnCode(body: String): Int? =
        Regex("""\$\.ret=(\d+);""").find(body)?.groupValues?.get(1)?.toIntOrNull()
}
