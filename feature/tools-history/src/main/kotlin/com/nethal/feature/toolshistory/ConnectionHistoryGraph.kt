package com.nethal.feature.toolshistory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nethal.core.history.ConnectionHistoryAggregator
import com.nethal.core.scheduling.db.SqliteMeasurementSampleRepository
import com.nethal.feature.toolshistory.ui.ConnectionHistoryScreen
import com.nethal.feature.toolshistory.ui.ConnectionHistoryViewModel

/**
 * Rota do módulo `:feature:tools-history` (issues #96/#104, protótipo `4i` em
 * `docs/design/prototypes.dc.html`). String de rota própria — mesmo padrão de `TracerouteRoutes`/
 * `ToolsDnsRoutes` (sub-rota interna a um módulo `:feature:tools-*`, não um contrato compartilhado).
 *
 * **Não wireada ainda — de propósito.** A consolidação de navegação da issue #147 está em
 * andamento em paralelo, tocando `core/navigation/AdvancedToolDestination.kt`,
 * `feature/settings/SettingsScreen.kt` e `app/.../BottomNavHost.kt`. Este módulo nasce "solto",
 * como `:feature:tools-speedtest`/`:feature:tools-dns` nasceram antes de #147 existir — quando o
 * composition root (`:app`) passar a depender deste módulo, falta: (1) chamar
 * `connectionHistoryGraph` dentro do `NavHost` raiz, e (2) adicionar uma entrada em
 * `AdvancedToolDestination`/`SettingsScreen` navegando para [ConnectionHistoryRoutes.ROOT].
 */
object ConnectionHistoryRoutes {
    const val ROOT = "tools/connection-history"
}

/**
 * Auto-contido (mesmo padrão de `toolsDnsGraph`): constrói o próprio
 * [SqliteMeasurementSampleRepository] a partir do [Context] recebido — sem depender de
 * `NetHalViewModelFactory` (que vive em `:app`).
 */
fun NavGraphBuilder.connectionHistoryGraph(
    context: Context,
    onBack: () -> Unit,
) {
    composable(ConnectionHistoryRoutes.ROOT) {
        val viewModel: ConnectionHistoryViewModel = viewModel(
            factory = connectionHistoryViewModelFactory(context.applicationContext),
        )
        ConnectionHistoryScreen(viewModel = viewModel, onBack = onBack)
    }
}

private fun connectionHistoryViewModelFactory(context: Context): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ConnectionHistoryViewModel::class.java) { "Unknown ViewModel class: $modelClass" }
            val repository = SqliteMeasurementSampleRepository(context)
            return ConnectionHistoryViewModel(ConnectionHistoryAggregator(repository)) as T
        }
    }
