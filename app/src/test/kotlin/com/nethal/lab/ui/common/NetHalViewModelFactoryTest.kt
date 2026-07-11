package com.nethal.lab.ui.common

import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.feature.settings.SettingsViewModel
import com.nethal.lab.FakeConsentRepository
import com.nethal.lab.ui.onboarding.BetaOptInViewModel
import com.nethal.lab.ui.onboarding.WelcomeViewModel
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão da issue #21 (crash ao clicar em "Iniciar diagnóstico"): a Tela 1 navega direto
 * para `BetaOptInScreen`, que resolve `BetaOptInViewModel` via `NetHalViewModelFactory`. O
 * `when` de `create()` não tinha esse case e caía no `else`, lançando
 * `IllegalArgumentException` na primeira composição da tela — crash imediato após o clique.
 */
class NetHalViewModelFactoryTest {

    private val factory = NetHalViewModelFactory(
        consentRepository = FakeConsentRepository(),
        driverRegistry = object : DriverRegistry {
            override fun manifestVersion(): String = "test"
            override fun generatedAt(): String = "test"
            override fun profiles(): List<CompatibilityProfile> = emptyList()
            override fun findProfiles(vendor: String, model: String): List<CompatibilityProfile> = emptyList()
            override fun findProfile(vendor: String, model: String): CompatibilityProfile? = null
            override fun profilesForVendor(vendor: String): List<CompatibilityProfile> = emptyList()
        },
        driverFamilyRegistry = DriverFamilyRegistry(emptyList()),
    )

    @Test
    fun `create resolves BetaOptInViewModel without throwing`() {
        val viewModel = factory.create(BetaOptInViewModel::class.java)

        assertTrue(viewModel is BetaOptInViewModel)
    }

    @Test
    fun `create still resolves WelcomeViewModel`() {
        assertTrue(factory.create(WelcomeViewModel::class.java) is WelcomeViewModel)
    }

    @Test
    fun `create still resolves SettingsViewModel`() {
        assertTrue(factory.create(SettingsViewModel::class.java) is SettingsViewModel)
    }

    @Test
    fun `create throws for truly unknown ViewModel class`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownProbeViewModel::class.java)
        }
    }

    private class UnknownProbeViewModel : androidx.lifecycle.ViewModel()
}
