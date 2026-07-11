package com.nethal.feature.toolscommon

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class UnavailableResourceStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itemFicaVisivelETocavelSemAcaoDeResolucao() {
        composeRule.setContent {
            UnavailableResourceState(
                label = "Diagnóstico avançado",
                reason = "Este diagnóstico requer firmware v2.2+. Atualize para desbloquear.",
                icon = { Text("icon") },
            )
        }

        // Item bloqueado continua visível na árvore (nunca escondido).
        composeRule.onNodeWithText("Diagnóstico avançado").assertExists()

        // Diálogo não aparece antes do toque.
        composeRule.onNodeWithText("Recurso indisponível").assertDoesNotExist()

        // Toque continua funcional — abre o diálogo com o motivo.
        composeRule.onNodeWithText("Diagnóstico avançado").performClick()
        composeRule.onNodeWithText("Recurso indisponível").assertExists()
        composeRule.onNodeWithText(
            "Este diagnóstico requer firmware v2.2+. Atualize para desbloquear.",
        ).assertExists()

        // Sem ação de resolução configurada, só "Entendi" fecha o diálogo.
        composeRule.onNodeWithText("Entendi").performClick()
        composeRule.onNodeWithText("Recurso indisponível").assertDoesNotExist()
    }

    @Test
    fun dialogoExibeAcaoDeResolucaoQuandoInformada() {
        var resolutionClicked = false
        composeRule.setContent {
            UnavailableResourceState(
                label = "Verificação de porta (SNMP)",
                reason = "Requer firmware v2.2 ou superior.",
                resolutionLabel = "Atualizar firmware",
                onResolutionClick = { resolutionClicked = true },
            )
        }

        composeRule.onNodeWithText("Verificação de porta (SNMP)").performClick()
        composeRule.onNodeWithText("Atualizar firmware").assertExists()

        composeRule.onNodeWithText("Atualizar firmware").performClick()

        assert(resolutionClicked)
        // Ação de resolução também fecha o diálogo.
        composeRule.onNodeWithText("Requer firmware v2.2 ou superior.").assertDoesNotExist()
    }

    @Test
    fun dialogoDireto_semItemDeLista_podeSerAcionadoIndependentemente() {
        composeRule.setContent {
            UnavailableFeatureDialog(
                reason = "A reinicialização remota exige capability SET_REBOOT_WAN.",
                onDismissRequest = {},
            )
        }

        composeRule.onNodeWithText("Recurso indisponível").assertExists()
        composeRule.onNodeWithText(
            "A reinicialização remota exige capability SET_REBOOT_WAN.",
        ).assertExists()
    }
}
