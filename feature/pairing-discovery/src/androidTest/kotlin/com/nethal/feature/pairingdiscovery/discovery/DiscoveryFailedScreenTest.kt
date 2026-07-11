package com.nethal.feature.pairingdiscovery.discovery

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Decisão registrada na spec de #80: a falha de descoberta oferece dois caminhos ("Selecionar
 * equipamento" → cluster manual 2g, "Informar IP manualmente" → entrada de IP direta) em vez de
 * só um campo de texto cru.
 */
class DiscoveryFailedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ofereceOsDoisCaminhosDeRecuperacao() {
        var retried = false
        var selectedManually = false
        var enteredIpManually = false

        composeRule.setContent {
            DiscoveryFailedScreen(
                reason = FailureReason.NO_GATEWAY_FOUND,
                onRetry = { retried = true },
                onSelectManually = { selectedManually = true },
                onEnterIpManually = { enteredIpManually = true },
            )
        }

        composeRule.onNodeWithText("Tentar novamente").performClick()
        assert(retried)

        composeRule.onNodeWithText("Selecionar equipamento").performClick()
        assert(selectedManually)

        composeRule.onNodeWithText("Informar IP manualmente").performClick()
        assert(enteredIpManually)
    }
}
