package com.nethal.feature.pairingdiscovery.manualentry

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/**
 * Destino de "Outro/não sei" (2h) e "Informar IP manualmente" (2b-falha), issue #80. Cobre os
 * dois modos: sem `deviceLabel` (vendor/modelo desconhecidos) e com `deviceLabel` (modelo já
 * escolhido manualmente em 2i, só falta o IP).
 */
class ManualIpEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submeteOIpDigitadoSemDeviceLabel() {
        var submitted: String? = null

        composeRule.setContent {
            ManualIpEntryScreen(
                deviceLabel = null,
                error = null,
                onBack = {},
                onSubmit = { submitted = it },
            )
        }

        composeRule.onNodeWithText("IP do equipamento, ex.: 192.168.1.1").performTextInput("192.168.1.50")
        composeRule.onNodeWithText("Adicionar equipamento").performClick()

        assert(submitted == "192.168.1.50")
    }

    @Test
    fun comDeviceLabelMostraCopyDedicadaEBotaoContinuar() {
        composeRule.setContent {
            ManualIpEntryScreen(
                deviceLabel = "TP-Link Archer C6",
                error = null,
                onBack = {},
                onSubmit = {},
            )
        }

        composeRule.onNodeWithText(
            "Você selecionou TP-Link Archer C6. Informe o IP dele na sua rede local para continuar.",
        ).assertExists()
        composeRule.onNodeWithText("Continuar").assertExists()
    }

    @Test
    fun exibeErroQuandoInformado() {
        composeRule.setContent {
            ManualIpEntryScreen(
                deviceLabel = null,
                error = "Esse IP não parece ser da sua rede local. O NetHAL só testa equipamentos na sua LAN.",
                onBack = {},
                onSubmit = {},
            )
        }

        composeRule.onNodeWithText("Esse IP não parece ser da sua rede local. O NetHAL só testa equipamentos na sua LAN.")
            .assertExists()
    }
}
