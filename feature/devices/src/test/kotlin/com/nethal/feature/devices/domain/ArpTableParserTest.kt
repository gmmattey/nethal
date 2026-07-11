package com.nethal.feature.devices.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArpTableParserTest {

    @Test
    fun `parse extracts only complete entries with resolved MAC`() {
        val rawTable = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
            192.168.1.23     0x1         0x0         00:00:00:00:00:00     *        wlan0
            192.168.1.45     0x1         0x2         11:22:33:44:55:66     *        wlan0
        """.trimIndent()

        val entries = ArpTableParser.parse(rawTable)

        assertEquals(2, entries.size)
        assertEquals(ArpEntry("192.168.1.1", "aa:bb:cc:dd:ee:ff"), entries[0])
        assertEquals(ArpEntry("192.168.1.45", "11:22:33:44:55:66"), entries[1])
    }

    @Test
    fun `parse ignores malformed lines without exception`() {
        val rawTable = """
            IP address       HW type     Flags       HW address            Mask     Device
            garbled line
        """.trimIndent()

        val entries = ArpTableParser.parse(rawTable)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `parse returns empty list for header-only table`() {
        val entries = ArpTableParser.parse("IP address       HW type     Flags       HW address            Mask     Device")

        assertTrue(entries.isEmpty())
    }
}
