package com.nethal.feature.wifinetwork

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.navigation.BottomNavDestination

/**
 * Entrada do módulo `:feature:wifi-network` (issue #84) no `NavHost` do composition root — mesmo
 * contrato de `NavGraphBuilder.xyzGraph()` da ADR 0002. Usa a rota já reservada em
 * `BottomNavDestination.NETWORK` (`home/network`), definida em `:core:navigation`; este módulo não
 * inventa string de rota própria.
 *
 * **Não conectado ao `NavHost` real ainda** — `app/.../navigation/BottomNavHost.kt` continua com o
 * composable placeholder até a consolidação dos 4 módulos de feature (Status/Rede/Dispositivos/
 * Configurações, issues #83-86) rodando em paralelo. Quem fizer essa consolidação troca o
 * `composable(placeholder)` correspondente por uma chamada a esta função.
 *
 * @param capabilityEngineProvider fornece a sessão ativa (`CapabilityEngine`) para esta tela, ou
 * `null` quando não há sessão — esta função não decide de onde a sessão vem (pareamento em
 * `:feature:pairing-auth`, issues #76-79, ainda em construção em paralelo); só consome o que for
 * entregue no momento da composição, igual ao que `CapabilitiesScreen` já faz hoje em `:app`.
 */
fun NavGraphBuilder.wifiNetworkGraph(
    capabilityEngineProvider: () -> CapabilityEngine?,
) {
    composable(BottomNavDestination.NETWORK.route) {
        val factory = remember(capabilityEngineProvider) { WifiNetworkViewModelFactory(capabilityEngineProvider()) }
        val viewModel: WifiNetworkViewModel = viewModel(factory = factory)
        WifiNetworkScreen(viewModel = viewModel)
    }
}
