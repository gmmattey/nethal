package com.nethal.feature.pairingauth.login

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.nethal.feature.pairingauth.readyViewModel
import org.junit.Rule
import org.junit.Test

/**
 * Tela `2c` — Login no modem (issue #76). Cobre: avisos de segurança preservados (senha nunca
 * salva, sessão administrativa única), TOFU condicional só para `tplink-stok-luci-driver`,
 * navegação para "Onde encontrar a senha" e envio do formulário.
 */
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun avisoDeSenhaNaoSalvaESessaoUnicaSempreVisiveis() {
        composeRule.setContent {
            LoginScreen(viewModel = readyViewModel(), onNavigateToPasswordHelp = {}, onSubmit = {})
        }

        composeRule.onNodeWithText(
            "Sua senha não é salva neste aparelho nem enviada a nenhum servidor — ela " +
                "existe só durante esta sessão, na memória do app.",
        ).assertExists()

        composeRule.onNodeWithText(
            "Este e outros equipamentos da mesma família aceitam só uma sessão " +
                "administrativa por vez. Se a WebUI do equipamento estiver aberta em um " +
                "navegador, feche-a antes de entrar — do contrário a autenticação pode falhar.",
        ).assertExists()
    }

    @Test
    fun avisoDeTofuSoAparecePraDriverStokLuci() {
        composeRule.setContent {
            LoginScreen(
                viewModel = readyViewModel(driverFamilyId = "tplink-stok-luci-driver"),
                onNavigateToPasswordHelp = {},
                onSubmit = {},
            )
        }

        composeRule.onNodeWithText(
            "Este equipamento busca sua própria chave de criptografia no momento do " +
                "login, sem certificado digital — não é possível confirmar de antemão que " +
                "você está realmente falando com o seu roteador. Use esta autenticação " +
                "apenas na sua própria rede local, em que você confia.",
        ).assertExists()
    }

    @Test
    fun avisoDeTofuNaoAparecePraOutrosDrivers() {
        composeRule.setContent {
            LoginScreen(
                viewModel = readyViewModel(driverFamilyId = "tplink-legacy-cgi-driver"),
                onNavigateToPasswordHelp = {},
                onSubmit = {},
            )
        }

        composeRule.onNodeWithText(
            "Este equipamento busca sua própria chave de criptografia no momento do " +
                "login, sem certificado digital — não é possível confirmar de antemão que " +
                "você está realmente falando com o seu roteador. Use esta autenticação " +
                "apenas na sua própria rede local, em que você confia.",
        ).assertDoesNotExist()
    }

    @Test
    fun esqueciASenhaNavegaParaAjuda() {
        var navigated = false
        composeRule.setContent {
            LoginScreen(
                viewModel = readyViewModel(),
                onNavigateToPasswordHelp = { navigated = true },
                onSubmit = {},
            )
        }

        composeRule.onNodeWithText("Esqueci a senha do modem").performClick()
        assert(navigated)
    }

    @Test
    fun botaoDeEnviarComecaDesabilitadoEHabilitaSoComUsuarioESenhaPreenchidos() {
        var submitted = false
        val viewModel = readyViewModel()

        composeRule.setContent {
            LoginScreen(viewModel = viewModel, onNavigateToPasswordHelp = {}, onSubmit = { submitted = true })
        }

        // "USUÁRIO" já vem preenchido com "admin" (mesmo default do protótipo `2c`) — falta só a senha.
        composeRule.onNodeWithText("Entrar no modem").assertExists()
        composeRule.onNodeWithTag("login-password-field").performTextInput("uma-senha-qualquer")

        composeRule.onNodeWithText("Entrar no modem").performClick()
        assert(submitted)
    }

    @Test
    fun campoDeUsuarioVazioMantemOBotaoDeEnviarDesabilitado() {
        val viewModel = readyViewModel()

        composeRule.setContent {
            LoginScreen(viewModel = viewModel, onNavigateToPasswordHelp = {}, onSubmit = {})
        }

        composeRule.onNodeWithTag("login-username-field").performTextClearance()
        composeRule.onNodeWithTag("login-password-field").performTextInput("uma-senha-qualquer")

        composeRule.onNodeWithText("Entrar no modem").assertIsNotEnabled()
    }
}
