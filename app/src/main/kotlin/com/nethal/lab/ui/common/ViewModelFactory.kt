package com.nethal.lab.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nethal.core.consent.ConsentRepository
import com.nethal.core.discovery.DiscoveryEngine
import com.nethal.core.discovery.NetworkEnvironmentReader
import com.nethal.lab.ui.discovery.DiscoveryViewModel
import com.nethal.lab.ui.onboarding.WelcomeViewModel
import com.nethal.lab.ui.settings.SettingsViewModel

/**
 * Factory única do app. Sem DI framework nesta entrega — o grafo de dependências ainda é
 * pequeno o suficiente para não justificar Hilt/Koin.
 */
class NetHalViewModelFactory(
    private val consentRepository: ConsentRepository,
    private val discoveryEngine: DiscoveryEngine,
    private val networkEnvironmentReader: NetworkEnvironmentReader,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            WelcomeViewModel::class.java -> WelcomeViewModel(consentRepository) as T
            SettingsViewModel::class.java -> SettingsViewModel(consentRepository) as T
            DiscoveryViewModel::class.java -> DiscoveryViewModel(discoveryEngine, networkEnvironmentReader) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
