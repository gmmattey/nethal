package com.nethal.feature.pairingdiscovery.selection

import com.nethal.core.catalog.CatalogDeviceType
import com.nethal.core.catalog.CompatibilityProfile
import com.nethal.core.catalog.CredentialConvention
import com.nethal.core.catalog.DriverStage
import com.nethal.core.catalog.ManagementDefaults

/** Catálogo real simplificado (mesmo shape de `catalog-2026.07.26.json`) reusado pelos testes de UI do cluster 2g/2h/2i. */
internal fun catalogFixture(): List<CompatibilityProfile> = listOf(
    testProfile("nokia_g1425gb_v1", "Nokia", "G-1425G-B", CatalogDeviceType.ONT, DriverStage.READ_ONLY_ALPHA),
    testProfile("tplink_c6_stok_v1", "TP-Link", "Archer C6", CatalogDeviceType.ROUTER, DriverStage.READ_ONLY_ALPHA),
    testProfile("tplink_c6_encrypted_v1", "TP-Link", "Archer C6", CatalogDeviceType.ROUTER, DriverStage.DRAFT),
    testProfile("tplink_c20_v1", "TP-Link", "Archer C20", CatalogDeviceType.ROUTER, DriverStage.READ_ONLY_ALPHA),
    testProfile("tplink_c50v4_v1", "TP-Link", "Archer C50 v4", CatalogDeviceType.ROUTER, DriverStage.DRAFT),
)

internal fun testProfile(
    profileId: String,
    vendor: String,
    model: String,
    deviceType: CatalogDeviceType,
    stage: DriverStage,
) = CompatibilityProfile(
    profileId = profileId,
    vendor = vendor,
    model = model,
    deviceType = deviceType,
    productLine = "test",
    platformId = "test-platform",
    driverFamilyId = "$profileId-driver",
    stage = stage,
    stageReason = "fixture de teste",
    physicalTestAccess = false,
    managementDefaults = ManagementDefaults(
        ipConfidence = 0.5,
        ipConfidenceNote = "fixture",
        managementPort = 80,
        managementPortNote = "fixture",
    ),
    credentialConvention = CredentialConvention(
        confidence = 0.0,
        confidenceNote = "fixture",
        policyNote = "fixture",
    ),
    confidenceScoreOverall = 0.5,
    confidenceScoreOverallNote = "fixture",
)
