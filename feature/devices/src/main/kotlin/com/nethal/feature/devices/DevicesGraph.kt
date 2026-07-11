package com.nethal.feature.devices

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nethal.core.navigation.BottomNavDestination
import com.nethal.feature.devices.ui.DevicesScreen
import com.nethal.feature.devices.ui.DevicesViewModel
import com.nethal.feature.devices.ui.DevicesViewModelFactory

/**
 * Entrada do grafo de navegação da aba "Dispositivos" (`BottomNavDestination.DEVICES`,
 * `home/devices`) — issues #86 (UI) + #105 (backend). Auto-contido de propósito: constrói o
 * próprio `ViewModel` via [DevicesViewModelFactory], sem depender de `NetHalViewModelFactory`
 * (que vive em `:app`) nem de nenhum parâmetro externo além do `Context` obtido via
 * `LocalContext.current`. A fiação final em `BottomNavHost` (issue #67, feita fora desta
 * entrega — outras 3 abas em paralelo) só troca o composable placeholder por
 * `devicesGraph()`, sem precisar injetar nada a mais.
 */
fun NavGraphBuilder.devicesGraph() {
    composable(BottomNavDestination.DEVICES.route) {
        val context = LocalContext.current
        val viewModel: DevicesViewModel = viewModel(factory = DevicesViewModelFactory(context))
        DevicesScreen(viewModel = viewModel)
    }
}
