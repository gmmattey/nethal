package com.nethal.feature.pairingdiscovery.equipmentfound

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.core.catalog.ManualIdentificationCandidate
import com.nethal.core.catalog.ManualIdentificationRepository
import com.nethal.core.fingerprint.FingerprintEngine
import com.nethal.core.fingerprint.FingerprintResult
import com.nethal.core.model.NetworkTarget
import com.nethal.core.model.TargetRole
import com.nethal.core.model.TargetSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/**
 * Tela 2b — Equipamento detectado (issue #75). Preserva o comportamento de
 * `EquipmentDetectedViewModel`: estado identificando, card com dados do fingerprint, e o
 * `matchedProfileId` repassado ao `onContinue` (critério de aceite explícito da issue).
 */
class EquipmentFoundScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val target = NetworkTarget(ip = "192.168.1.1", role = TargetRole.PRIMARY_GATEWAY, source = TargetSource.GATEWAY)

    private fun fingerprintEngine(result: FingerprintResult) = object : FingerprintEngine {
        override suspend fun identify(target: NetworkTarget): FingerprintResult = result
    }

    private val fakeManualIdentificationRepository = object : ManualIdentificationRepository {
        override fun observeCandidates(): Flow<List<ManualIdentificationCandidate>> = flowOf(emptyList())
        override suspend fun submit(candidate: ManualIdentificationCandidate) = Unit
    }

    @Test
    fun continuarRepassaOMatchedProfileIdIdentificado() {
        val result = FingerprintResult(
            vendor = "TP-Link",
            model = "Archer C6",
            firmware = null,
            matchedProfileId = "tplink_c6_stok_v1",
            confidence = 0.85,
            detectedProtocols = emptyList(),
            manifestVersion = "2026.07.26",
            manifestGeneratedAt = "2026-07-26",
            rawEvidence = null,
        )

        var continuedWith: String? = "not-called"
        val viewModel = EquipmentDetectedViewModel(
            target = target,
            fingerprintEngine = fingerprintEngine(result),
            manualIdentificationRepository = fakeManualIdentificationRepository,
        )

        composeRule.setContent {
            EquipmentFoundScreen(viewModel = viewModel, onContinue = { continuedWith = it })
        }

        composeRule.onNodeWithText("Conectar a este roteador").performClick()

        assert(continuedWith == "tplink_c6_stok_v1")
    }

    @Test
    fun confiancaBaixaAbreFormularioDeCorrecaoPorPadrao() {
        val result = FingerprintResult(
            vendor = null,
            model = null,
            firmware = null,
            matchedProfileId = null,
            confidence = 0.05,
            detectedProtocols = emptyList(),
            manifestVersion = "2026.07.26",
            manifestGeneratedAt = "2026-07-26",
            rawEvidence = null,
        )

        val viewModel = EquipmentDetectedViewModel(
            target = target,
            fingerprintEngine = fingerprintEngine(result),
            manualIdentificationRepository = fakeManualIdentificationRepository,
        )

        composeRule.setContent {
            EquipmentFoundScreen(viewModel = viewModel, onContinue = {})
        }

        // Confiança abaixo do `LOW_CONFIDENCE_THRESHOLD` abre o formulário de correção direto —
        // só o formulário aberto mostra este texto (não o botão "Corrigir identificação").
        composeRule.onNodeWithText(
            "Sua correção fica registrada apenas neste aparelho como candidata — " +
                "não promove automaticamente nenhum driver para uso estável.",
        ).assertExists()
    }
}
