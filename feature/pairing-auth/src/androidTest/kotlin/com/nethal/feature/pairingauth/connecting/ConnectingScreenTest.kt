package com.nethal.feature.pairingauth.connecting

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Tela `2e` — Conectando ao modem (issue #78). Puramente visual: sem lógica própria de teste de credenciais. */
class ConnectingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mostraTituloEChecklist() {
        composeRule.setContent {
            ConnectingScreen(deviceLabel = "TP-Link Archer C6")
        }

        composeRule.onNodeWithText("Conectando ao modem").assertExists()
        composeRule.onNodeWithText("Rede local encontrada").assertExists()
        composeRule.onNodeWithText("TP-Link Archer C6 identificado").assertExists()
        composeRule.onNodeWithText("Validando credenciais…").assertExists()
    }

    @Test
    fun semDeviceLabelUsaTextoGenericoHonesto() {
        composeRule.setContent {
            ConnectingScreen(deviceLabel = null)
        }

        composeRule.onNodeWithText("Equipamento identificado").assertExists()
    }
}
