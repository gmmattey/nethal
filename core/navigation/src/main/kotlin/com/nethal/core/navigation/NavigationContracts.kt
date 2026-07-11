package com.nethal.core.navigation

/**
 * Contratos de rota para os destinos raiz da navegação inferior do NetHAL Lab ("modo uso diário",
 * host implementado em #67).
 *
 * Cada valor corresponde a uma aba fixa da `NavigationBar` Material 3 (spec exata em
 * `docs/design/design-system.dc.html`, seção 1l). Um módulo `:feature:*` que assuma um destino usa
 * a rota definida aqui — nenhuma feature inventa string de rota própria, e nenhuma feature depende
 * de outra para descobrir a rota certa (regra de dependência única da ADR 0002).
 *
 * Casca da Fase 2 (retomada de #67): hoje o composition root (`:app`) monta os quatro destinos
 * diretamente com composables placeholder (`BottomNavHost`). Quando os módulos `:feature:status`,
 * `:feature:wifi-network`, `:feature:devices` e `:feature:settings` nascerem (issues #83-86), cada
 * um passa a expor seu próprio `NavGraphBuilder.xyzGraph()` usando a rota correspondente abaixo —
 * este enum não muda, só quem o consome.
 */
enum class BottomNavDestination(val route: String) {
    STATUS("home/status"),
    NETWORK("home/network"),
    DEVICES("home/devices"),
    SETTINGS("home/settings"),
}
