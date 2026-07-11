package com.nethal.feature.onboarding.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.core.consent.ConsentScope
import com.nethal.feature.onboarding.FakeConsentRepository
import org.junit.Rule
import org.junit.Test

/**
 * Cobre a issue #68 — tela `1a`, primeira do onboarding: trava SIG-312/313 preservada mesmo com
 * redesenho, links funcionais mantidos, consentimento gravado ao avançar.
 */
class OnboardingWelcomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exibeTituloECopyHonestaSobreLocalizacao() {
        composeTestRule.setContent {
            OnboardingWelcomeScreen(
                consentRepository = FakeConsentRepository(),
                onStartDiagnosis = {},
                onViewPrivacy = {},
                onViewCompatibleDevices = {},
            )
        }

        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.TITLE).assertExists()
        composeTestRule
            .onNodeWithText("nunca para rastrear sua localização geográfica", substring = true)
            .assertExists()
    }

    @Test
    fun botaoIniciarDiagnosticoComecaDesabilitadoELiberaComCheckboxMarcado() {
        composeTestRule.setContent {
            OnboardingWelcomeScreen(
                consentRepository = FakeConsentRepository(),
                onStartDiagnosis = {},
                onViewPrivacy = {},
                onViewCompatibleDevices = {},
            )
        }

        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.START_BUTTON).assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(OnboardingWelcomeScreenTestTags.NETWORK_AUTHORIZATION_CHECKBOX)
            .performClick()

        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.START_BUTTON).assertIsEnabled()
    }

    @Test
    fun avancarGravaAutorizacaoDeRedeELeituraDeStatus() {
        val repository = FakeConsentRepository()
        var started = false

        composeTestRule.setContent {
            OnboardingWelcomeScreen(
                consentRepository = repository,
                onStartDiagnosis = { started = true },
                onViewPrivacy = {},
                onViewCompatibleDevices = {},
            )
        }

        composeTestRule
            .onNodeWithTag(OnboardingWelcomeScreenTestTags.NETWORK_AUTHORIZATION_CHECKBOX)
            .performClick()
        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.START_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assert(started)
        assert(repository.isGranted(ConsentScope.NETWORK_AUTHORIZATION))
        assert(repository.isGranted(ConsentScope.READ_STATUS))
    }

    @Test
    fun linksDePrivacidadeEDispositivosCompativeisPermanecemFuncionais() {
        var viewedPrivacy = false
        var viewedCompatibleDevices = false

        composeTestRule.setContent {
            OnboardingWelcomeScreen(
                consentRepository = FakeConsentRepository(),
                onStartDiagnosis = {},
                onViewPrivacy = { viewedPrivacy = true },
                onViewCompatibleDevices = { viewedCompatibleDevices = true },
            )
        }

        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.PRIVACY_LINK).performClick()
        composeTestRule.onNodeWithTag(OnboardingWelcomeScreenTestTags.COMPATIBLE_DEVICES_LINK)
            .performClick()

        assert(viewedPrivacy)
        assert(viewedCompatibleDevices)
    }
}
