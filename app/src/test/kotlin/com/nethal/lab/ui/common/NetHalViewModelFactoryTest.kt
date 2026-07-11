package com.nethal.lab.ui.common

import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.catalog.ManualIdentificationCandidate
import com.nethal.core.catalog.ManualIdentificationRepository
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.discovery.DiscoveryEngine
import com.nethal.core.discovery.NetworkEnvironment
import com.nethal.core.discovery.NetworkEnvironmentReader
import com.nethal.core.fingerprint.FingerprintEngine
import com.nethal.core.fingerprint.FingerprintResult
import com.nethal.core.model.DiscoveryResult
import com.nethal.core.model.NetworkTarget
import com.nethal.lab.FakeConsentRepository
import com.nethal.lab.ui.discovery.DiscoveryViewModel
import com.nethal.lab.ui.onboarding.BetaOptInViewModel
import com.nethal.lab.ui.onboarding.WelcomeViewModel
import com.nethal.lab.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        discoveryEngine = object : DiscoveryEngine {
            override suspend fun discover(): DiscoveryResult =
                DiscoveryResult(devices = emptyList(), possibleDoubleNat = false)
        },
        networkEnvironmentReader = object : NetworkEnvironmentReader {
            override suspend fun read(): NetworkEnvironment? = null
        },
        fingerprintEngine = object : FingerprintEngine {
            override suspend fun identify(target: NetworkTarget): FingerprintResult =
                throw UnsupportedOperationException("não usado neste teste")
        },
        manualIdentificationRepository = object : ManualIdentificationRepository {
            override fun observeCandidates(): Flow<List<ManualIdentificationCandidate>> = flowOf(emptyList())
            override suspend fun submit(candidate: ManualIdentificationCandidate) = Unit
        },
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
    fun `create still resolves DiscoveryViewModel`() {
        assertTrue(factory.create(DiscoveryViewModel::class.java) is DiscoveryViewModel)
    }

    @Test
    fun `create throws for truly unknown ViewModel class`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownProbeViewModel::class.java)
        }
    }

    private class UnknownProbeViewModel : androidx.lifecycle.ViewModel()
}
