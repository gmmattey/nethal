package com.nethal.feature.pairingauth

import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.catalog.CatalogDeviceType
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.catalog.CredentialConvention
import com.nethal.core.catalog.DriverFamily
import com.nethal.core.catalog.DriverFamilyAuthResult
import com.nethal.core.catalog.DriverFamilyFactory
import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.catalog.DriverStage
import com.nethal.core.catalog.ManagementDefaults
import com.nethal.core.model.CapabilityId
import com.nethal.core.model.NetworkTarget
import com.nethal.core.model.TargetRole
import com.nethal.core.model.TargetSource
import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sucessor de `AuthenticationViewModelTest` (`:app`, extraído para este módulo, ADR 0002) — mesmos
 * cenários (credencial válida, credencial inválida, driver sem sessão real, handoff de sessão),
 * mais os cenários novos deste cluster: [PairingAuthViewModel.resetAfterFailure],
 * [PairingAuthViewModel.markSessionLostAfterSuccess], e a garantia de que a senha nunca sobrevive
 * em nenhum estado observável de fora (issue #79's critério de teste explícito do Bruno).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingAuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val target = NetworkTarget(ip = "192.168.1.1", role = TargetRole.PRIMARY_GATEWAY, source = TargetSource.GATEWAY)

    private val fakeTransport = object : HttpTransport {
        override fun get(url: String, extraHeaders: Map<String, String>) = HttpTransportResponse(404, "", emptyMap(), emptyMap())
        override fun post(url: String, body: String, cookies: Map<String, String>, extraHeaders: Map<String, String>) =
            HttpTransportResponse(404, "", emptyMap(), emptyMap())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeProfile(profileId: String, driverFamilyId: String): CompatibilityProfile = CompatibilityProfile(
        profileId = profileId,
        vendor = "TP-Link",
        model = "Archer C6",
        deviceType = CatalogDeviceType.ROUTER,
        productLine = "Archer",
        platformId = driverFamilyId,
        driverFamilyId = driverFamilyId,
        stage = DriverStage.DISCOVERY_ONLY,
        stageReason = "fixture de teste",
        physicalTestAccess = false,
        managementDefaults = ManagementDefaults(
            candidateIps = listOf("192.168.1.1"),
            ipConfidence = 1.0,
            ipConfidenceNote = "fixture",
            managementPort = 80,
            managementPortNote = "fixture",
        ),
        credentialConvention = CredentialConvention(
            confidence = 1.0,
            confidenceNote = "fixture",
            policyNote = "fixture",
        ),
        confidenceScoreOverall = 1.0,
        confidenceScoreOverallNote = "fixture",
    )

    private class FakeDriverRegistry(private val profile: CompatibilityProfile) : DriverRegistry {
        override fun manifestVersion(): String = "fixture"
        override fun generatedAt(): String = "fixture"
        override fun profiles(): List<CompatibilityProfile> = listOf(profile)
        override fun findProfiles(vendor: String, model: String): List<CompatibilityProfile> =
            profiles().filter { it.vendor == vendor && it.model == model }
        override fun findProfile(vendor: String, model: String): CompatibilityProfile? = findProfiles(vendor, model).firstOrNull()
        override fun profilesForVendor(vendor: String): List<CompatibilityProfile> = profiles().filter { it.vendor == vendor }
    }

    /** Sempre devolve [result] imediatamente — cobre sucesso e credencial inválida. */
    private class FakeDriverFamilyWithRealAuth(private val result: DriverFamilyAuthResult) : DriverFamily {
        override suspend fun readCapability(id: CapabilityId): CapabilityReadResult =
            CapabilityReadResult.Unavailable(reason = "não usado neste teste")

        override suspend fun authenticate(username: String, password: String): DriverFamilyAuthResult = result
    }

    /** Não sobrescreve `authenticate()` — exercita o default honesto de `DriverFamily` (driver sem sessão real). */
    private class FakeDriverFamilyWithoutRealAuth : DriverFamily {
        override suspend fun readCapability(id: CapabilityId): CapabilityReadResult =
            CapabilityReadResult.Unavailable(reason = "não usado neste teste")
    }

    private class FakeDriverFamilyFactory(
        override val familyId: String,
        private val driverFamily: DriverFamily,
    ) : DriverFamilyFactory {
        override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily = driverFamily
    }

    private fun viewModelFor(profile: CompatibilityProfile, driverFamily: DriverFamily): PairingAuthViewModel {
        val driverRegistry = FakeDriverRegistry(profile)
        val driverFamilyRegistry = DriverFamilyRegistry(listOf(FakeDriverFamilyFactory(profile.driverFamilyId, driverFamily)))
        return PairingAuthViewModel(
            target = target,
            matchedProfileId = profile.profileId,
            driverRegistry = driverRegistry,
            driverFamilyRegistry = driverFamilyRegistry,
            httpTransport = fakeTransport,
        )
    }

    @Test
    fun `submit with valid credentials moves to Success and activates the session`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.Success))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("secret")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PairingAuthUiState.Ready)
        assertEquals(CredentialTestState.Success, (state as PairingAuthUiState.Ready).credentialTestState)
        assertTrue(viewModel.isSessionActive)
    }

    @Test
    fun `submit with invalid credentials surfaces the reason and never activates a session`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(
            profile,
            FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.InvalidCredentials("senha incorreta")),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("senha-errada")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PairingAuthUiState.Ready)
        val testState = (state as PairingAuthUiState.Ready).credentialTestState
        assertTrue(testState is CredentialTestState.InvalidCredentials)
        assertEquals("senha incorreta", (testState as CredentialTestState.InvalidCredentials).reason)
        assertFalse(viewModel.isSessionActive)
    }

    @Test
    fun `submit against a driver without real session support shows the honest unsupported message`() = runTest {
        val profile = fakeProfile("fixture_unsupported_v1", "fixture-unsupported-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithoutRealAuth())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("secret")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PairingAuthUiState.Ready)
        val testState = (state as PairingAuthUiState.Ready).credentialTestState
        assertTrue(testState is CredentialTestState.Failure)
        assertTrue(
            "mensagem deveria explicar que o driver não implementa sessão real, foi: ${(testState as CredentialTestState.Failure).reason}",
            testState.reason.contains("não implementa gerenciamento de sessão real"),
        )
        assertFalse(viewModel.isSessionActive)
    }

    @Test
    fun `resetAfterFailure clears the password but preserves the username`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(
            profile,
            FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.InvalidCredentials("senha incorreta")),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("senha-errada")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.resetAfterFailure()

        assertEquals("admin", viewModel.username)
        assertEquals("", viewModel.password)
        val state = viewModel.uiState.value
        assertTrue(state is PairingAuthUiState.Ready)
        assertEquals(CredentialTestState.Idle, (state as PairingAuthUiState.Ready).credentialTestState)
    }

    @Test
    fun `markSessionLostAfterSuccess turns a Success state into an honest Failure`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.Success))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("secret")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.markSessionLostAfterSuccess()

        val state = viewModel.uiState.value
        assertTrue(state is PairingAuthUiState.Ready)
        assertTrue((state as PairingAuthUiState.Ready).credentialTestState is CredentialTestState.Failure)
    }

    @Test
    fun `captureAuthenticatedSession hands off the active engine and onCleared becomes a no-op afterwards`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.Success))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("secret")
        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.isSessionActive)

        val handedOff = viewModel.captureAuthenticatedSession()
        assertTrue(handedOff != null && handedOff.isSessionActive)

        // Simula o ViewModel sendo descartado (grafo removido da pilha) depois do handoff — não
        // pode derrubar a sessão que a próxima tela (Capabilities) acabou de assumir.
        callOnCleared(viewModel)
        assertTrue(handedOff!!.isSessionActive)
    }

    @Test
    fun `captureAuthenticatedSession returns null when no session was ever activated`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.Failure("indisponível")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.captureAuthenticatedSession())
    }

    @Test
    fun `missing matchedProfileId from Tela 3 never crashes and surfaces DriverUnavailable`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = PairingAuthViewModel(
            target = target,
            matchedProfileId = null,
            driverRegistry = FakeDriverRegistry(profile),
            driverFamilyRegistry = DriverFamilyRegistry(emptyList()),
            httpTransport = fakeTransport,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PairingAuthUiState.DriverUnavailable)
    }

    @Test
    fun `onCleared scrubs the in-memory password`() = runTest {
        val profile = fakeProfile("fixture_working_v1", "fixture-working-driver")
        val viewModel = viewModelFor(profile, FakeDriverFamilyWithRealAuth(DriverFamilyAuthResult.Success))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("uma-senha-bem-secreta")
        assertEquals("uma-senha-bem-secreta", viewModel.password)

        callOnCleared(viewModel)

        assertEquals("", viewModel.password)
    }

    /**
     * `ViewModel.onCleared()` é `protected` — chamado via reflexão só em teste, mesmo espírito de
     * simular o framework descartando o ViewModel (`ViewModelStore.clear()`) sem precisar de um
     * host Android real neste módulo `test/` (JVM puro, sem Robolectric).
     */
    private fun callOnCleared(viewModel: PairingAuthViewModel) {
        val method = PairingAuthViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
