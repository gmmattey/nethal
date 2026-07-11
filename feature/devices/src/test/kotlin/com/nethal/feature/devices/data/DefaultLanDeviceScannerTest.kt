package com.nethal.feature.devices.data

import com.nethal.core.discovery.NetworkEnvironment
import com.nethal.core.discovery.NetworkEnvironmentReader
import com.nethal.core.discovery.SsdpDiscoverer
import com.nethal.core.discovery.SsdpResponse
import com.nethal.feature.devices.domain.LanDeviceType
import com.nethal.feature.devices.domain.LanScanResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLanDeviceScannerTest {

    private val environment = NetworkEnvironment(
        localIp = "192.168.50.1",
        gatewayIp = "192.168.50.254",
        subnetPrefixLength = 30,
        dnsServers = emptyList(),
        isWifi = true,
    )

    private fun scanner(
        env: NetworkEnvironment? = environment,
        arpTable: String? = null,
        ssdpResponses: List<SsdpResponse> = emptyList(),
    ): DefaultLanDeviceScanner {
        val environmentReader = object : NetworkEnvironmentReader {
            override suspend fun read(): NetworkEnvironment? = env
        }
        val ssdpDiscoverer = object : SsdpDiscoverer {
            override suspend fun discover(): List<SsdpResponse> = ssdpResponses
        }
        val arpReader = ArpTableReader { arpTable }
        return DefaultLanDeviceScanner(
            networkEnvironmentReader = environmentReader,
            ssdpDiscoverer = ssdpDiscoverer,
            arpTableReader = arpReader,
            probeTimeoutMillis = 20,
        )
    }

    @Test
    fun `scan returns NoNetwork when wifi is not active`() = runBlocking {
        val result = scanner(env = environment.copy(isWifi = false)).scan()

        assertEquals(LanScanResult.NoNetwork, result)
    }

    @Test
    fun `scan returns NoNetwork when local ip is not RFC 1918 private`() = runBlocking {
        val result = scanner(env = environment.copy(localIp = "8.8.8.8")).scan()

        assertEquals(LanScanResult.NoNetwork, result)
    }

    @Test
    fun `scan returns NoNetwork when environment is null`() = runBlocking {
        val result = scanner(env = null).scan()

        assertEquals(LanScanResult.NoNetwork, result)
    }

    @Test
    fun `scan merges ARP and SSDP sources, excludes local ip and non-private ssdp ip`() = runBlocking {
        val arpTable = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.50.254   0x1         0x2         50:c7:bf:aa:bb:cc     *        wlan0
            192.168.50.5     0x1         0x2         11:22:33:44:55:66     *        wlan0
        """.trimIndent()
        val ssdpResponses = listOf(
            SsdpResponse(sourceIp = "192.168.50.5", location = null, server = "TestServer", searchTarget = null, usn = null),
            SsdpResponse(sourceIp = "8.8.8.8", location = null, server = "PublicShouldBeIgnored", searchTarget = null, usn = null),
        )

        val result = scanner(arpTable = arpTable, ssdpResponses = ssdpResponses).scan()

        require(result is LanScanResult.Success)
        val ips = result.devices.map { it.ipAddress }
        assertEquals(listOf("192.168.50.254", "192.168.50.5"), ips)
        assertTrue(result.devices.none { it.ipAddress == "192.168.50.1" })
        assertTrue(result.devices.none { it.ipAddress == "8.8.8.8" })

        val gateway = result.devices.first { it.ipAddress == "192.168.50.254" }
        assertEquals("TP-Link", gateway.vendor)
        assertEquals(LanDeviceType.GATEWAY, gateway.deviceType)

        val other = result.devices.first { it.ipAddress == "192.168.50.5" }
        assertEquals("11:22:33:44:55:66", other.macAddress)
    }
}
