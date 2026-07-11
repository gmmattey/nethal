package com.nethal.feature.onboarding.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.feature.onboarding.OnboardingPermissionsState
import org.junit.Rule
import org.junit.Test

/** Cobre a issue #72 — resumo adaptativo, CTA nunca trava mesmo com permissão negada. */
class OnboardingPermissionsSummaryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tudoConcedidoExibeCopyDoPrototipo() {
        composeTestRule.setContent {
            OnboardingPermissionsSummaryScreen(
                state = OnboardingPermissionsState(locationGranted = true, notificationsGranted = true),
                onContinue = {},
            )
        }

        composeTestRule.onNodeWithText("Permissões concedidas").assertExists()
    }

    @Test
    fun permissaoNegadaNaoTravaFluxoEBotaoContinuaClicavel() {
        var continued = false

        composeTestRule.setContent {
            OnboardingPermissionsSummaryScreen(
                state = OnboardingPermissionsState(locationGranted = false, notificationsGranted = true),
                onContinue = { continued = true },
            )
        }

        composeTestRule.onNodeWithText("Podemos continuar").assertExists()
        composeTestRule
            .onNodeWithTag(OnboardingPermissionsSummaryScreenTestTags.CONTINUE_BUTTON)
            .assertIsEnabled()
            .performClick()

        assert(continued)
    }

    @Test
    fun ctaSempreDisponivelNavegaParaPareamento() {
        composeTestRule.setContent {
            OnboardingPermissionsSummaryScreen(
                state = OnboardingPermissionsState(locationGranted = false, notificationsGranted = false),
                onContinue = {},
            )
        }

        composeTestRule.onNodeWithText("Parear roteador →").assertExists()
    }
}
