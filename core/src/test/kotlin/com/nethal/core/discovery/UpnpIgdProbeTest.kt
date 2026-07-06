package com.nethal.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpnpIgdProbeTest {

    @Test
    fun `resolves service type and control url for WANIPConnection relative to descriptor`() {
        val descriptor = """
            <root>
              <device>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType>
                    <controlURL>/upnp/control/wancic</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
                    <controlURL>/upnp/control/wanipc</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val wanService = resolveWanIpConnectionService(
            descriptor,
            "http://192.168.1.1:1900/rootDesc.xml",
        )

        assertEquals("urn:schemas-upnp-org:service:WANIPConnection:1", wanService?.serviceType)
        assertEquals("http://192.168.1.1:1900/upnp/control/wanipc", wanService?.controlUrl)
    }

    @Test
    fun `returns null when no WAN service is present`() {
        val descriptor = """
            <root>
              <device>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:Layer3Forwarding:1</serviceType>
                    <controlURL>/upnp/control/l3f</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val wanService = resolveWanIpConnectionService(
            descriptor,
            "http://192.168.1.1:1900/rootDesc.xml",
        )

        assertNull(wanService)
    }
}

class PrivateIpRangesTest {

    @Test
    fun `rfc1918 ranges are private`() {
        assertEquals(true, PrivateIpRanges.isPrivate("10.0.0.1"))
        assertEquals(true, PrivateIpRanges.isPrivate("172.16.0.1"))
        assertEquals(true, PrivateIpRanges.isPrivate("172.31.255.255"))
        assertEquals(true, PrivateIpRanges.isPrivate("192.168.0.1"))
    }

    @Test
    fun `public ip ranges are not private`() {
        assertEquals(false, PrivateIpRanges.isPrivate("201.17.45.90"))
        assertEquals(false, PrivateIpRanges.isPrivate("172.32.0.1"))
        assertEquals(false, PrivateIpRanges.isPrivate("8.8.8.8"))
    }

    @Test
    fun `malformed ip is not private`() {
        assertEquals(false, PrivateIpRanges.isPrivate("not-an-ip"))
        assertEquals(false, PrivateIpRanges.isPrivate("999.1.1.1"))
    }
}
