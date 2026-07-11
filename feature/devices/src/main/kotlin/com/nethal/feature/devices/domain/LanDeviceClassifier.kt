package com.nethal.feature.devices.domain

/**
 * Heurística simples de classificação por palavra-chave em hostname/fabricante — não pretende
 * ser precisa, só dar uma pista melhor que "desconhecido" quando há sinal suficiente. Pura, sem
 * dependência de rede/Android; testável isoladamente.
 */
object LanDeviceClassifier {

    fun classify(hostname: String?, vendor: String?, isGateway: Boolean): LanDeviceType {
        if (isGateway) return LanDeviceType.GATEWAY

        val signal = listOfNotNull(hostname, vendor).joinToString(" ").lowercase()
        if (signal.isBlank()) return LanDeviceType.UNKNOWN

        return when {
            signal.containsAny("iphone", "android", "galaxy", "redmi", "xiaomi", "pixel") ->
                LanDeviceType.MOBILE
            signal.containsAny("macbook", "notebook", "laptop", "desktop", "intel", "pc-") ->
                LanDeviceType.COMPUTER
            signal.containsAny("tv", "roku", "chromecast", "firetv", "shield", "bravia") ->
                LanDeviceType.TV_MEDIA
            signal.containsAny("printer", "impressora", "epson", "canon", "hp-print") ->
                LanDeviceType.PRINTER
            signal.containsAny("espressif", "iot", "echo", "alexa", "smart", "plug", "cam", "sonoff") ->
                LanDeviceType.IOT
            else -> LanDeviceType.UNKNOWN
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any { this.contains(it) }
}
