package com.nethal.feature.onboarding.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.core.consent.ConsentScope
import com.nethal.feature.onboarding.FakeConsentRepository
import org.junit.Rule
import org.junit.Test

/**
 * Cobre a issue #71 — tela `1d`: pedido de notificação + fusão do opt-in de telemetria beta
 * (decisão `docs/product/decisions/0001-telas-orfas-redesenho.md`, `BetaOptInScreen` fundida
 * aqui). Não clica no botão "Ativar notificações" real (dispararia o prompt do sistema em API
 * 33+, fora do controle do Compose Test) — cobre o gate de consentimento via "Agora não", que não
 * depende do launcher de permissão do Android.
 */
class OnboardingNotificationsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exibeTituloECopyDeNotificacoes() {
        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = FakeConsentRepository(),
                onContinue = {},
            )
        }

        composeTestRule.onNodeWithTag(OnboardingNotificationsScreenTestTags.TITLE).assertExists()
        composeTestRule
            .onNodeWithText("queda de conexão", substring = true)
            .assertExists()
    }

    @Test
    fun exibeTextoDeColetaDeDadosPreservadoDoBetaOptIn() {
        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = FakeConsentRepository(),
                onContinue = {},
            )
        }

        composeTestRule
            .onNodeWithText("Fabricante, modelo e firmware", substring = true)
            .assertExists()
        composeTestRule
            .onNodeWithText("nunca coleta senha", substring = true)
            .assertExists()
    }

    @Test
    fun ativarNotificacoesESkipEstaoPresentesEClicaveis() {
        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = FakeConsentRepository(),
                onContinue = {},
            )
        }

        composeTestRule
            .onNodeWithTag(OnboardingNotificationsScreenTestTags.ACTIVATE_BUTTON)
            .assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithTag(OnboardingNotificationsScreenTestTags.SKIP_BUTTON)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun rationaleNaoApareceSemNegacaoPrevia() {
        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = FakeConsentRepository(),
                onContinue = {},
            )
        }

        composeTestRule.onNodeWithTag(OnboardingNotificationsScreenTestTags.RATIONALE).assertDoesNotExist()
    }

    @Test
    fun agoraNaoSemMarcarTelemetriaNaoConcedeConsentimentoEAvanca() {
        val repository = FakeConsentRepository()
        var continued = false

        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = repository,
                onContinue = { continued = true },
            )
        }

        composeTestRule.onNodeWithTag(OnboardingNotificationsScreenTestTags.SKIP_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assert(continued)
        assert(!repository.isGranted(ConsentScope.TELEMETRY_BETA))
    }

    @Test
    fun marcarTelemetriaEAvancarConcedeTelemetryBeta() {
        val repository = FakeConsentRepository()
        var continued = false

        composeTestRule.setContent {
            OnboardingNotificationsScreen(
                consentRepository = repository,
                onContinue = { continued = true },
            )
        }

        composeTestRule
            .onNodeWithTag(OnboardingNotificationsScreenTestTags.TELEMETRY_CHECKBOX)
            .performClick()
        composeTestRule.onNodeWithTag(OnboardingNotificationsScreenTestTags.SKIP_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assert(continued)
        assert(repository.isGranted(ConsentScope.TELEMETRY_BETA))
    }
}
