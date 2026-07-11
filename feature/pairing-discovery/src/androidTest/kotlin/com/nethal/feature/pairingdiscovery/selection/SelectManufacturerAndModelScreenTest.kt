package com.nethal.feature.pairingdiscovery.selection

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nethal.core.catalog.CatalogDeviceType
import com.nethal.core.catalog.CompatibilityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Filtro em cascata tipo → fabricante → modelo (issues #81, #82) contra o catálogo real
 * simplificado de [catalogFixture]. Cobre: 2h lista só fabricante(s) reais do tipo escolhido +
 * "Outro/não sei" por último; 2i lista só modelos do `(tipo, fabricante)`, deduplicados, com
 * modelo `DRAFT` desabilitado.
 */
class SelectManufacturerAndModelScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selecionarFabricanteFiltraPorTipoEIncluiOutroNaoSeiPorUltimo() {
        var manufacturerSelected: String? = null
        var otherSelected = false

        composeRule.setContent {
            SelectManufacturerScreen(
                profiles = catalogFixture(),
                type = CatalogDeviceType.ONT,
                typeLabel = "ONT",
                onBack = {},
                onManufacturerSelected = { manufacturerSelected = it },
                onOtherSelected = { otherSelected = true },
            )
        }

        // ONT só tem Nokia no catálogo — TP-Link não pode aparecer nesta tela.
        composeRule.onAllNodesWithText("TP-Link").assertCountEquals(0)

        composeRule.onNodeWithText("Nokia").performClick()
        assertEquals("Nokia", manufacturerSelected)

        composeRule.onNodeWithText("Outro / não sei").performClick()
        assert(otherSelected)
    }

    @Test
    fun selecionarModeloFiltraPorTipoEFabricanteEDedupeArcherC6() {
        var confirmed: CompatibilityProfile? = null

        composeRule.setContent {
            SelectModelScreen(
                profiles = catalogFixture(),
                type = CatalogDeviceType.ROUTER,
                typeLabel = "Roteador",
                vendor = "TP-Link",
                onBack = {},
                onModelSelected = { confirmed = it },
            )
        }

        // Archer C6 aparece uma única vez (dedupe pelo estágio de maior maturidade) — se o
        // dedupe falhasse, este node teria mais de uma ocorrência e o teste falharia aqui.
        composeRule.onAllNodesWithText("Archer C6").assertCountEquals(1)

        composeRule.onNodeWithText("Archer C6").performClick()
        assertEquals("tplink_c6_stok_v1", confirmed?.profileId)
    }

    @Test
    fun modeloEmPesquisaAbreDialogoDeConfirmacao() {
        var confirmed: CompatibilityProfile? = null

        composeRule.setContent {
            SelectModelScreen(
                profiles = catalogFixture(),
                type = CatalogDeviceType.ROUTER,
                typeLabel = "Roteador",
                vendor = "TP-Link",
                onBack = {},
                onModelSelected = { confirmed = it },
            )
        }

        composeRule.onNodeWithText("Archer C50 v4").performClick()
        // Não confirma direto — modelo DRAFT abre diálogo "continuar mesmo assim" antes.
        assertNull(confirmed)

        composeRule.onNodeWithText("Continuar mesmo assim").performClick()
        assertEquals("tplink_c50v4_v1", confirmed?.profileId)
    }
}
