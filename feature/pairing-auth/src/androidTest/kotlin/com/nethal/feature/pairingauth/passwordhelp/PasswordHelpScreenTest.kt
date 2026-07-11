package com.nethal.feature.pairingauth.passwordhelp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Tela `2d` — Onde encontrar a senha (issue #77). Cobre: navegação de volta ao login preserva o
 * estado (o `onBack` é o único jeito de sair, dados do formulário sobrevivem porque vivem no
 * `PairingAuthViewModel` compartilhado, não nesta tela) e, principalmente, o critério de
 * segurança: **nenhum valor de senha real ou de exemplo é exibido** — só orientação genérica de
 * onde procurar (`/seguranca-nethal`).
 */
class PasswordHelpScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun voltarDisparaOnBack() {
        var backCalled = false
        composeRule.setContent {
            PasswordHelpScreen(onBack = { backCalled = true })
        }

        // Duas ações levam de volta ao login: o botão circular no topo e o CTA principal.
        composeRule.onNodeWithText("Já encontrei, voltar ao login").performClick()
        assert(backCalled)
    }

    @Test
    fun nuncaExibeUmValorDeSenhaOuNumeroDeSerieConcreto() {
        composeRule.setContent {
            PasswordHelpScreen(onBack = {})
        }

        // Nenhum valor de exemplo do protótipo original (`admin`, `NTH-48291`) sobrevive aqui —
        // só os NOMES dos campos a procurar na etiqueta, nunca um valor preenchido.
        composeRule.onAllNodesWithText("admin", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("NTH-48291", substring = true).assertCountEquals(0)
    }
}
