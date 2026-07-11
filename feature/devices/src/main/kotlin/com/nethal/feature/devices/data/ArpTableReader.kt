package com.nethal.feature.devices.data

import java.io.File

/**
 * Lê o conteúdo bruto de `/proc/net/arp` — abstraído em interface para permitir teste sem
 * depender do filesystem real do device. Comportamento incerto por OEM (alguns restringem
 * leitura desse arquivo em builds mais novos) — ver `/regras-android-nethal`; falha vira `null`,
 * nunca exceção propagada, e o scan segue com as outras fontes (SSDP, varredura ativa).
 */
fun interface ArpTableReader {
    fun readRawTable(): String?
}

class ProcNetArpTableReader : ArpTableReader {
    override fun readRawTable(): String? = runCatching {
        File("/proc/net/arp").readText()
    }.getOrNull()
}
