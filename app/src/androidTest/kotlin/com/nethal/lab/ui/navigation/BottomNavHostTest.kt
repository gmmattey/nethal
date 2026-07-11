package com.nethal.lab.ui.navigation

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nethal.core.designsystem.theme.NetHalLabTheme
import com.nethal.lab.NetHalApplication
import com.nethal.lab.ui.common.NetHalViewModelFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cobre a troca entre os 4 destinos do host de bottom nav (#67) — desde a consolidação da issue
 * #147, as quatro abas montam o `NavGraphBuilder` real de cada módulo (`statusGraph`,
 * `wifiNetworkGraph`, `devicesGraph`, `settingsGraph`), sem `capabilityEngine`/`pairedDeviceIp`
 * (`null`/`null`, mesmo contrato de "sem sessão pareada" tratado honestamente por cada tela).
 * Configurações monta o `settingsGraph()` real de `:feature:settings` (#85); aqui verificada quanto
 * à presença e à navegação interna para o item de privacidade absorvido de `PrivacyScreen` (decisão
 * #66) — o conteúdo em si (toggle de programa beta etc.) tem cobertura própria em
 * `SettingsViewModelTest`.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModelFactory: NetHalViewModelFactory

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<NetHalApplication>()
        // driverRegistry/driverFamilyRegistry removidos daqui: quebra pré-existente, não relacionada à
        // #97 — `NetHalViewModelFactory` não aceita mais esses parâmetros desde 4fc6cd3 ("enxuga
        // NetHalViewModelFactory", ADR 0002); este teste instrumentado nunca foi atualizado e não
        // compilava. Corrigido no mesmo diff por estar editando este arquivo de qualquer forma.
        viewModelFactory = NetHalViewModelFactory(
            consentRepository = app.consentRepository,
            themeModeRepository = app.themeModeRepository,
        )

        composeRule.setContent {
            NetHalLabTheme {
                BottomNavHost(
                    viewModelFactory = viewModelFactory,
                    capabilityEngine = null,
                    pairedDeviceIp = null,
                    telemetryCollector = app.telemetryCollector,
                )
            }
        }
    }

    @Test
    fun destinoInicialEStatus() {
        composeRule.onNodeWithTag("status_screen").assertExists()
        composeRule.onNodeWithText("Status").assertIsSelected()
    }

    @Test
    fun trocaParaRedeAtualizaConteudoESelecao() {
        composeRule.onNodeWithText("Rede").performClick()

        composeRule.onNodeWithTag("home_network_screen").assertExists()
        composeRule.onNodeWithText("Rede").assertIsSelected()
    }

    @Test
    fun trocaParaDispositivosAtualizaConteudo() {
        composeRule.onNodeWithText("Dispositivos").performClick()

        composeRule.onNodeWithTag("home_devices_screen").assertExists()
    }

    @Test
    fun trocaParaConfiguracoesReaproveitaSettingsScreen() {
        composeRule.onNodeWithText("Configurações").performClick()

        composeRule.onNodeWithTag("home_settings_screen").assertExists()
    }

    @Test
    fun politicaDePrivacidadeNavegaParaConteudoAbsorvidoDaPrivacyScreen() {
        composeRule.onNodeWithText("Configurações").performClick()

        composeRule.onNodeWithText("Política de privacidade").performClick()

        composeRule.onNodeWithTag("settings_privacy_screen").assertExists()

        composeRule.onNodeWithText("Voltar").performClick()

        composeRule.onNodeWithTag("home_settings_screen").assertExists()
    }

    @Test
    fun voltarParaAbaAnteriorRestauraSeuConteudo() {
        composeRule.onNodeWithText("Rede").performClick()
        composeRule.onNodeWithText("Status").performClick()

        composeRule.onNodeWithTag("status_screen").assertExists()
        composeRule.onNodeWithText("Status").assertIsSelected()
    }
}
