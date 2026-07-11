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
 * Cobre a troca entre os 4 destinos do host de bottom nav (#67) — casca de infraestrutura, sem
 * conteúdo final das abas Status/Rede/Dispositivos (issues #83, #84, #86). Configurações reaproveita
 * `SettingsScreen` já existente, aqui só verificada quanto à presença (o conteúdo dela já tem
 * cobertura própria em `SettingsViewModel`/testes unitários).
 */
@RunWith(AndroidJUnit4::class)
class BottomNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModelFactory: NetHalViewModelFactory

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<NetHalApplication>()
        viewModelFactory = NetHalViewModelFactory(
            consentRepository = app.consentRepository,
            discoveryEngine = app.discoveryEngine,
            networkEnvironmentReader = app.networkEnvironmentReader,
            fingerprintEngine = app.fingerprintEngine,
            manualIdentificationRepository = app.manualIdentificationRepository,
            driverRegistry = app.driverRegistry,
            driverFamilyRegistry = app.driverFamilyRegistry,
        )

        composeRule.setContent {
            NetHalLabTheme {
                BottomNavHost(viewModelFactory = viewModelFactory)
            }
        }
    }

    @Test
    fun destinoInicialEStatus() {
        composeRule.onNodeWithTag("home_status_screen").assertExists()
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
    fun voltarParaAbaAnteriorRestauraSeuConteudo() {
        composeRule.onNodeWithText("Rede").performClick()
        composeRule.onNodeWithText("Status").performClick()

        composeRule.onNodeWithTag("home_status_screen").assertExists()
        composeRule.onNodeWithText("Status").assertIsSelected()
    }
}
