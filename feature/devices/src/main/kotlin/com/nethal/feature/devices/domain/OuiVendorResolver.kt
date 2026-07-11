package com.nethal.feature.devices.domain

/**
 * Tabela de fabricante por prefixo OUI (3 primeiros octetos do MAC) — recorte pequeno e
 * deliberadamente NÃO exaustivo dos fabricantes mais comuns em redes residenciais (celulares,
 * notebooks, smart TVs, roteadores, IoT genérico). Não é a base IEEE completa
 * (dezenas de milhares de entradas) — decisão de reuso vs. build: construir uma tabela própria
 * pequena em vez de embutir uma base OUI completa de terceiros (ex.: `nmap-mac-prefixes`) evita
 * inflar o APK com dado majoritariamente irrelevante para o produto; expandir sob demanda
 * conforme evidência real de dispositivo não reconhecido, mesmo raciocínio de priorização usado
 * em `docs/architecture/driver-adoption-strategy.md` para drivers.
 */
object OuiVendorResolver {

    fun vendorFor(macAddress: String): String? {
        val prefix = normalizedPrefix(macAddress) ?: return null
        return VENDORS_BY_OUI_PREFIX[prefix]
    }

    private fun normalizedPrefix(macAddress: String): String? {
        val hex = macAddress.replace(":", "").replace("-", "").uppercase()
        if (hex.length < 6) return null
        return hex.substring(0, 6)
    }

    private val VENDORS_BY_OUI_PREFIX: Map<String, String> = mapOf(
        // Apple
        "3C0754" to "Apple", "F0DBF8" to "Apple", "A45E60" to "Apple", "DC2B2A" to "Apple",
        "F4F951" to "Apple", "8863DF" to "Apple",
        // Samsung
        "5C0A5B" to "Samsung", "8425DB" to "Samsung", "E8508B" to "Samsung", "34145F" to "Samsung",
        // Xiaomi
        "F8A45F" to "Xiaomi", "34CE00" to "Xiaomi", "9C994D" to "Xiaomi", "781D55" to "Xiaomi",
        // TP-Link / Mercusys (mesmo fabricante)
        "50C7BF" to "TP-Link", "F4EC38" to "TP-Link", "AC84C6" to "TP-Link", "148FC6" to "TP-Link",
        // Huawei
        "F8B7E2" to "Huawei", "00E0FC" to "Huawei", "6055F9" to "Huawei",
        // Google
        "F4F5D8" to "Google", "3C5AB4" to "Google", "F4F5E8" to "Google",
        // Amazon (Echo/Fire TV)
        "68371D" to "Amazon", "FCA183" to "Amazon", "F0272D" to "Amazon",
        // Espressif — chip Wi-Fi genérico usado em boa parte de IoT barato (plugues, lâmpadas)
        "246F28" to "Espressif (IoT)", "A020A6" to "Espressif (IoT)", "84F3EB" to "Espressif (IoT)",
        // Intel (placas Wi-Fi de notebook)
        "3497F6" to "Intel", "A4C3F0" to "Intel",
        // Ubiquiti (APs/roteadores prosumer)
        "04185A" to "Ubiquiti", "F09FC2" to "Ubiquiti",
        // Nokia (ONT GPON, ver drivers/nokia-gpon)
        "8446C1" to "Nokia",
        // ASUS
        "1C872C" to "ASUS", "704D7B" to "ASUS",
    )
}
