package com.nethal.core.driver.family.tplink.gdprcgi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class TpLinkGdprCgiDriverConfig(
    val rsaKeyPath: String,
    val loginPath: String,
    val loginStyle: TpLinkGdprCgiLoginStyle,
    val cryptoMode: TpLinkGdprCgiCryptoMode,
    val rsaPaddingMode: TpLinkGdprCgiRsaPaddingMode,
    val tokenPath: String = "/",
    val authenticatedReadPath: String,
    val authenticatedReadPlaintext: String,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJsonElement(element: JsonElement): TpLinkGdprCgiDriverConfig =
            json.decodeFromJsonElement(serializer(), element)
    }
}
