package com.nethal.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nethal.core.navigation.BottomNavDestination

/**
 * Rotas do fluxo de Configurações. A raiz usa [BottomNavDestination.SETTINGS] (contrato
 * compartilhado em `:core:navigation`, consumido pelo `BottomNavHost` em `:app`); a rota de
 * privacidade é interna a este módulo — nenhum outro `:feature:*` navega direto para ela (regra de
 * dependência única, ADR 0002). Se o botão "Ver privacidade" da tela de Boas-vindas (#68) precisar
 * apontar para cá, isso se resolve no composition root (`:app`), não por dependência cruzada entre
 * módulos de feature.
 */
private object SettingsRoutes {
    val ROOT = BottomNavDestination.SETTINGS.route
    const val PRIVACY = "home/settings/privacy"
}

/**
 * Ponto de entrada do módulo `:feature:settings` (issue #85) — o `BottomNavHost` (`:app`) monta
 * este grafo dentro do seu próprio `NavHost`, sem saber do que ele é feito por dentro.
 *
 * `appVersionLabel` vem do composition root (`BuildConfig.VERSION_NAME`/`VERSION_CODE` de `:app`,
 * o único módulo que tem acesso a isso) — este módulo não inventa número de versão.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    viewModelFactory: ViewModelProvider.Factory,
    appVersionLabel: String,
) {
    composable(SettingsRoutes.ROOT) {
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        SettingsScreen(
            viewModel = viewModel,
            appVersionLabel = appVersionLabel,
            onOpenPrivacy = { navController.navigate(SettingsRoutes.PRIVACY) },
        )
    }

    composable(SettingsRoutes.PRIVACY) {
        SettingsPrivacyScreen(onBack = { navController.popBackStack() })
    }
}
