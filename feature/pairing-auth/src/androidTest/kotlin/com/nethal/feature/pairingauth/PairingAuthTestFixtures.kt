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

/** Fixtures reaproveitadas pelos testes de UI do cluster de Login/Falha (issues #76-#79). */
internal val testTarget = NetworkTarget(ip = "192.168.1.1", role = TargetRole.PRIMARY_GATEWAY, source = TargetSource.GATEWAY)

internal val fakeHttpTransport = object : HttpTransport {
    override fun get(url: String, extraHeaders: Map<String, String>) = HttpTransportResponse(404, "", emptyMap(), emptyMap())
    override fun post(url: String, body: String, cookies: Map<String, String>, extraHeaders: Map<String, String>) =
        HttpTransportResponse(404, "", emptyMap(), emptyMap())
}

internal fun fakeProfile(profileId: String, driverFamilyId: String): CompatibilityProfile = CompatibilityProfile(
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

internal class FakeDriverRegistry(private val profile: CompatibilityProfile) : DriverRegistry {
    override fun manifestVersion(): String = "fixture"
    override fun generatedAt(): String = "fixture"
    override fun profiles(): List<CompatibilityProfile> = listOf(profile)
    override fun findProfiles(vendor: String, model: String): List<CompatibilityProfile> =
        profiles().filter { it.vendor == vendor && it.model == model }
    override fun findProfile(vendor: String, model: String): CompatibilityProfile? = findProfiles(vendor, model).firstOrNull()
    override fun profilesForVendor(vendor: String): List<CompatibilityProfile> = profiles().filter { it.vendor == vendor }
}

/** Sempre devolve [result] imediatamente — suficiente para testes de UI que nunca chamam `submit()`. */
internal class FakeDriverFamily(private val result: DriverFamilyAuthResult = DriverFamilyAuthResult.Success) : DriverFamily {
    override suspend fun readCapability(id: CapabilityId): CapabilityReadResult =
        CapabilityReadResult.Unavailable(reason = "não usado neste teste")

    override suspend fun authenticate(username: String, password: String): DriverFamilyAuthResult = result
}

internal class FakeDriverFamilyFactory(
    override val familyId: String,
    private val driverFamily: DriverFamily,
) : DriverFamilyFactory {
    override fun create(profile: CompatibilityProfile, host: String, transport: HttpTransport): DriverFamily = driverFamily
}

/** ViewModel real, resolvido de forma síncrona (sem I/O) contra um profile/driver fixture — pronto em `PairingAuthUiState.Ready` assim que construído. */
internal fun readyViewModel(driverFamilyId: String = "fixture-working-driver"): PairingAuthViewModel {
    val profile = fakeProfile("fixture_working_v1", driverFamilyId)
    val driverRegistry = FakeDriverRegistry(profile)
    val driverFamilyRegistry = DriverFamilyRegistry(listOf(FakeDriverFamilyFactory(driverFamilyId, FakeDriverFamily())))
    return PairingAuthViewModel(
        target = testTarget,
        matchedProfileId = profile.profileId,
        driverRegistry = driverRegistry,
        driverFamilyRegistry = driverFamilyRegistry,
        httpTransport = fakeHttpTransport,
    )
}
