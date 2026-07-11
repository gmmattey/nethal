package com.nethal.feature.devices.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nethal.core.discovery.DefaultSsdpDiscoverer
import com.nethal.feature.devices.data.AndroidLanNetworkEnvironmentReader
import com.nethal.feature.devices.data.DefaultLanDeviceScanner

/**
 * Factory self-contida do módulo — `:feature:devices` não depende de `NetHalViewModelFactory`
 * (vive em `:app`) de propósito: mantém o módulo plugável sem exigir fiação externa além do
 * `Context` da Activity host (ver KDoc de `devicesGraph`).
 */
class DevicesViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val applicationContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == DevicesViewModel::class.java) {
            "Unknown ViewModel class: $modelClass"
        }
        val scanner = DefaultLanDeviceScanner(
            networkEnvironmentReader = AndroidLanNetworkEnvironmentReader(applicationContext),
            ssdpDiscoverer = DefaultSsdpDiscoverer(),
        )
        return DevicesViewModel(scanner) as T
    }
}
