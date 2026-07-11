package com.nethal.feature.pairingdiscovery.selection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.core.catalog.CatalogDeviceType
import org.junit.Rule
import org.junit.Test

/**
 * Tela 2g — Selecionar tipo (issue #80). Roteador/ONT habilitados (têm profile real no
 * catálogo); Mesh/Ponto de acesso ficam desabilitados — tocáveis, mas abrem diálogo em vez de
 * navegar (estado desabilitado do design system, nunca escondidos).
 */
class SelectDeviceTypeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tipoComProfileNavegaDireto() {
        var selected: CatalogDeviceType? = null

        composeRule.setContent {
            SelectDeviceTypeScreen(
                profiles = catalogFixture(),
                onBack = {},
                onTypeSelected = { selected = it },
            )
        }

        composeRule.onNodeWithText("Roteador").performClick()

        assert(selected == CatalogDeviceType.ROUTER)
    }

    @Test
    fun tipoSemProfileAbreDialogoEmVezDeNavegar() {
        var selected: CatalogDeviceType? = null

        composeRule.setContent {
            SelectDeviceTypeScreen(
                profiles = catalogFixture(),
                onBack = {},
                onTypeSelected = { selected = it },
            )
        }

        composeRule.onNodeWithText("Mesh").performClick()

        // Nunca navega para um tipo sem driver — mostra o diálogo de "ainda não há suporte".
        assert(selected == null)
        composeRule.onNodeWithText("Ainda não há driver para Mesh no NetHAL. Hoje cobrimos Roteador e ONT.")
            .assertExists()
    }
}
