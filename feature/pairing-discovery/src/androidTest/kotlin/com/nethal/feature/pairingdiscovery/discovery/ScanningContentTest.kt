package com.nethal.feature.pairingdiscovery.discovery

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Tela 2a — Buscando (issue #74). `ScanningContent` é `internal` só para permitir este teste
 * direto do estado visual sem depender do fluxo real de permissão em runtime (que dispara um
 * diálogo do sistema e não é determinístico em instrumented test).
 */
class ScanningContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exibeTituloDeBuscaEChamaCallbackAoTocarEmSelecionarManualmente() {
        var manualClicked = false

        composeRule.setContent {
            ScanningContent(onSelectManually = { manualClicked = true })
        }

        composeRule.onNodeWithText("Procurando seu roteador…").assertExists()
        composeRule.onNodeWithText("Não encontrou? Selecionar manualmente").performClick()

        assert(manualClicked)
    }
}
