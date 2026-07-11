package com.nethal.lab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nethal.core.telemetry.TelemetryCollector
import com.nethal.lab.telemetry.screenViewEvent

/**
 * Dispara `screen_view` (issue #97, Lane B) a cada composição de tela real, observando
 * `navController.currentBackStackEntryAsState()` — mesmo padrão que `BottomNavHost` já usa para saber
 * a rota atual (`currentRoute` na `bottomBar`). Centraliza num único ponto por `NavHostController`, em
 * vez de instrumentar manualmente cada composable de tela.
 *
 * `destination.route` devolve sempre o **padrão de rota declarado** (`"pairing_discovery/select_manufacturer/{type}"`),
 * nunca o valor resolvido dos argumentos — por isso é seguro como `screenName`: nunca carrega vendor,
 * IP ou qualquer dado real preenchido na navegação.
 *
 * Chamado duas vezes na árvore do app — uma por `NavHostController`, já que `BottomNavHost` monta o
 * seu próprio (aninhado dentro do `NavHost` raiz de `NetHalNavHost`, não é o mesmo grafo):
 * uma vez em [com.nethal.lab.ui.navigation.NetHalNavHost] (onboarding/pareamento) e outra dentro de
 * [BottomNavHost] (uso diário + ferramentas).
 */
@Composable
fun TelemetryScreenViewReporter(
    navController: NavHostController,
    telemetryCollector: TelemetryCollector,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    LaunchedEffect(route) {
        if (route != null) {
            telemetryCollector.sendProductEvent(screenViewEvent(screenName = route))
        }
    }
}
