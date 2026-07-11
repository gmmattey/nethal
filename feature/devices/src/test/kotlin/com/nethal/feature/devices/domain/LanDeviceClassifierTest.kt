package com.nethal.feature.devices.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LanDeviceClassifierTest {

    @Test
    fun `classify returns GATEWAY regardless of signal when isGateway is true`() {
        val result = LanDeviceClassifier.classify(hostname = "iphone-de-ana", vendor = "Apple", isGateway = true)

        assertEquals(LanDeviceType.GATEWAY, result)
    }

    @Test
    fun `classify infers MOBILE from hostname keyword`() {
        val result = LanDeviceClassifier.classify(hostname = "iPhone-de-Ana", vendor = null, isGateway = false)

        assertEquals(LanDeviceType.MOBILE, result)
    }

    @Test
    fun `classify infers IOT from vendor keyword`() {
        val result = LanDeviceClassifier.classify(hostname = null, vendor = "Espressif (IoT)", isGateway = false)

        assertEquals(LanDeviceType.IOT, result)
    }

    @Test
    fun `classify returns UNKNOWN when there is no signal`() {
        val result = LanDeviceClassifier.classify(hostname = null, vendor = null, isGateway = false)

        assertEquals(LanDeviceType.UNKNOWN, result)
    }
}
