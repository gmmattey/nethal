package com.nethal.feature.devices.domain

/** Uma entrada resolvida da tabela ARP do kernel — IP e MAC já associados. */
data class ArpEntry(val ipAddress: String, val macAddress: String)

/**
 * Parser puro do formato de `/proc/net/arp` (kernel Linux/Android) — sem dependência de Android,
 * testável em JVM puro. Formato (colunas separadas por espaço, uma linha de cabeçalho):
 *
 * ```
 * IP address       HW type     Flags       HW address            Mask     Device
 * 192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
 * 192.168.1.23     0x1         0x0         00:00:00:00:00:00     *        wlan0
 * ```
 *
 * Só devolve entradas com `flags == 0x2` (endereço já resolvido pelo kernel) e MAC diferente de
 * `00:00:00:00:00:00` — uma entrada `0x0` é uma tentativa de resolução pendente/expirada, não um
 * dispositivo confirmado.
 */
object ArpTableParser {

    fun parse(rawTable: String): List<ArpEntry> {
        return rawTable.lineSequence()
            .drop(1)
            .mapNotNull(::parseLine)
            .toList()
    }

    private fun parseLine(line: String): ArpEntry? {
        val columns = line.trim().split(Regex("\\s+"))
        if (columns.size < 4) return null

        val ipAddress = columns[0]
        val flags = columns[2]
        val macAddress = columns[3]

        if (flags != "0x2") return null
        if (macAddress.equals("00:00:00:00:00:00", ignoreCase = true)) return null

        return ArpEntry(ipAddress = ipAddress, macAddress = macAddress)
    }
}
