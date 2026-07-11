package com.nethal.lab.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nethal.core.designsystem.R
import com.nethal.core.navigation.BottomNavDestination
import com.nethal.lab.ui.common.NetHalViewModelFactory
import com.nethal.lab.ui.settings.SettingsScreen
import com.nethal.lab.ui.settings.SettingsViewModel

/**
 * Host de navegação inferior do NetHAL Lab ("modo uso diário", #67) — composition root montado em
 * `:app` (ADR 0002 Fase 2). Hoje o `NavHost` interno aponta para composables placeholder simples;
 * quando os módulos `:feature:status` / `:feature:wifi-network` / `:feature:devices` /
 * `:feature:settings` nascerem (issues #83-86), cada um passa a expor seu próprio
 * `NavGraphBuilder.xyzGraph()` usando as rotas de [BottomNavDestination] — este host não muda de
 * forma, só troca `composable(placeholder)` por `xyzGraph(navController)`.
 *
 * Configurações reaproveita a `SettingsScreen` já existente (antes pendurada numa rota órfã em
 * `NetHalNavHost`) — as outras três abas ainda não têm conteúdo real, só a casca (spec completa em
 * `docs/design/design-system.dc.html` seção 1l).
 *
 * Layout e comportamento (altura 80dp, padding 12/16dp, ícone 24dp, indicador pill 64×32dp raio
 * 16dp, rótulo 12sp 600/400) usam o default do `NavigationBar` Material 3 — bate com a spec sem
 * precisar de layout manual (levantamento prévio da issue #67).
 *
 * Nota para a Vera: o ícone da aba "Dispositivos" no protótipo (fluxo 3, telas 3i/3j) não bate com
 * nenhum SVG do set oficial (`docs/design/assets/icons/{dark,light}/`) — nem `router.svg` nem
 * `switch.svg`. Usamos `router.svg` (`ic_nav_devices`, em `:core:designsystem`) como placeholder
 * temporário; precisa de confirmação/ícone novo antes de qualquer entrega além da casca.
 */
@Composable
fun BottomNavHost(
    viewModelFactory: NetHalViewModelFactory,
    navController: NavHostController = rememberNavController(),
) {
    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.destination.route,
                        onClick = {
                            navController.navigate(item.destination.route) {
                                // Preserva estado das outras abas ao trocar (padrão M3 de bottom
                                // nav com múltiplos back stacks) — não recria a aba do zero a cada
                                // toque.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavDestination.STATUS.route,
            modifier = Modifier.padding(innerPadding),
            // "Fade through" do design system (seção Motion): fade out 90ms + fade in 110ms,
            // com leve scale 0.96→1 na entrada.
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 110, delayMillis = 90)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 110, delayMillis = 90),
                    )
            },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 90)) },
        ) {
            composable(BottomNavDestination.STATUS.route) {
                NavPlaceholderScreen(
                    testTag = "home_status_screen",
                    text = "Conteúdo de Status — casca (issue #83)",
                )
            }
            composable(BottomNavDestination.NETWORK.route) {
                NavPlaceholderScreen(
                    testTag = "home_network_screen",
                    text = "Conteúdo de Rede — casca (issue #84)",
                )
            }
            composable(BottomNavDestination.DEVICES.route) {
                NavPlaceholderScreen(
                    testTag = "home_devices_screen",
                    text = "Conteúdo de Dispositivos — casca (issue #86)",
                )
            }
            composable(BottomNavDestination.SETTINGS.route) {
                val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

private data class BottomNavItem(
    val destination: BottomNavDestination,
    val label: String,
    val iconRes: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(BottomNavDestination.STATUS, "Status", R.drawable.ic_nav_status),
    BottomNavItem(BottomNavDestination.NETWORK, "Rede", R.drawable.ic_nav_network),
    BottomNavItem(BottomNavDestination.DEVICES, "Dispositivos", R.drawable.ic_nav_devices),
    BottomNavItem(BottomNavDestination.SETTINGS, "Configurações", R.drawable.ic_nav_settings),
)

@Composable
private fun NavPlaceholderScreen(testTag: String, text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
