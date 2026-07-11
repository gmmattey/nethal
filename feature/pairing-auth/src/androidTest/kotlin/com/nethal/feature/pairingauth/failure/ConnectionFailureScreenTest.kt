package com.nethal.feature.pairingauth.failure

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Tela `2f` — Falha na conexão (issue #79). Cobre os 3 casos honestos (driver indisponível,
 * credenciais inválidas, falha de conexão) — cada um com a mensagem específica do seu `reason`,
 * nunca um texto genérico "algo deu errado" (critério de aceite explícito da issue).
 */
class ConnectionFailureScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun credenciaisInvalidasMostraOReasonEspecifico() {
        composeRule.setContent {
            ConnectionFailureScreen(
                kind = FailureKind.INVALID_CREDENTIALS,
                reason = "usuário ou senha não conferem com o cadastrado no equipamento",
                onRetry = {},
                onFindPassword = {},
            )
        }

        composeRule.onNodeWithText("Usuário ou senha incorretos").assertExists()
        composeRule.onNodeWithText("usuário ou senha não conferem com o cadastrado no equipamento").assertExists()
    }

    @Test
    fun driverIndisponivelMostraOReasonEspecifico() {
        composeRule.setContent {
            ConnectionFailureScreen(
                kind = FailureKind.DRIVER_UNAVAILABLE,
                reason = "Ainda não existe driver implementado para Huawei HG8245 nesta versão do app.",
                onRetry = {},
                onFindPassword = {},
            )
        }

        composeRule.onNodeWithText("Não é possível autenticar").assertExists()
        composeRule.onNodeWithText("Ainda não existe driver implementado para Huawei HG8245 nesta versão do app.").assertExists()
    }

    @Test
    fun falhaDeConexaoMostraOReasonEspecifico() {
        composeRule.setContent {
            ConnectionFailureScreen(
                kind = FailureKind.CONNECTION_FAILURE,
                reason = "timeout ao conectar em 192.168.1.1:80",
                onRetry = {},
                onFindPassword = {},
            )
        }

        composeRule.onNodeWithText("Não foi possível conectar").assertExists()
        composeRule.onNodeWithText("timeout ao conectar em 192.168.1.1:80").assertExists()
    }

    @Test
    fun tentarNovamenteEOndeEncontroASenhaDisparamOsCallbacks() {
        var retried = false
        var findPasswordClicked = false

        composeRule.setContent {
            ConnectionFailureScreen(
                kind = FailureKind.CONNECTION_FAILURE,
                reason = "falha genérica de teste",
                onRetry = { retried = true },
                onFindPassword = { findPasswordClicked = true },
            )
        }

        composeRule.onNodeWithText("Tentar novamente").performClick()
        assert(retried)

        composeRule.onNodeWithText("Onde encontro a senha?").performClick()
        assert(findPasswordClicked)
    }
}
