package com.nethal.feature.devices.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OuiVendorResolverTest {

    @Test
    fun `vendorFor resolves known OUI prefix regardless of separator style`() {
        assertEquals("TP-Link", OuiVendorResolver.vendorFor("50:C7:BF:11:22:33"))
        assertEquals("TP-Link", OuiVendorResolver.vendorFor("50-c7-bf-11-22-33"))
        assertEquals("TP-Link", OuiVendorResolver.vendorFor("50c7bf112233"))
    }

    @Test
    fun `vendorFor returns null for unknown prefix`() {
        assertNull(OuiVendorResolver.vendorFor("00:00:00:00:00:01"))
    }

    @Test
    fun `vendorFor returns null for malformed mac`() {
        assertNull(OuiVendorResolver.vendorFor("not-a-mac"))
    }
}
