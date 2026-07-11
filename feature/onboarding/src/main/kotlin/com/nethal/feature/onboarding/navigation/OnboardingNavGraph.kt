package com.nethal.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nethal.core.catalog.DriverRegistry
import com.nethal.feature.onboarding.OnboardingPermissionsState
import com.nethal.feature.onboarding.ui.OnboardingCompatibleDevicesScreen
import com.nethal.feature.onboarding.ui.OnboardingLocationScreen
import com.nethal.feature.onboarding.ui.OnboardingNearbyDevicesScreen
import com.nethal.feature.onboarding.ui.OnboardingPermissionsSummaryScreen

/**
 * Rotas expostas por `:feature:onboarding`. Só as 4 telas desta entrega (#69/#70/#72/#73) — `1a`
 * (boas-vindas, issue #68) e `1d` (notificações, issue #71) não existem neste módulo ainda; o host
 * que consumir este grafo (unificação de NavHost, issue #113) é responsável por inseri-las na
 * sequência quando estiverem prontas.
 */
object OnboardingRoutes {
    const val LOCATION = "onboarding/location" // 1b
    const val NEARBY_DEVICES = "onboarding/nearby-devices" // 1c
    const val PERMISSIONS_SUMMARY = "onboarding/permissions-summary" // 1e
    const val COMPATIBLE_DEVICES = "onboarding/compatible-devices" // 1f
}

/**
 * Grafo de navegação do onboarding, no formato `NavGraphBuilder.xyzGraph(...)` documentado em
 * `:core:navigation` (`NavigationContracts.kt`) para o composition root (#67/#113) plugar sem
 * `:feature:onboarding` depender de nenhum outro módulo `:feature:*`.
 *
 * A sequência padrão ligada aqui é `1b`→`1c`→`1e` (pula `1d`, ainda não implementada — issue #71).
 * Quando `1d` existir, o host troca [onNearbyDevicesContinue] para navegar para a rota de `1d`
 * antes de `1e`, sem precisar mudar nada dentro deste módulo. `1f` não é sequencial (acessível a
 * partir de `1a` via link "Ver dispositivos compatíveis", como no protótipo) — por isso não tem
 * callback de "continue" próprio, só [onCompatibleDevicesBack].
 *
 * [onboardingPermissionsState] é lido (não observado como estado reativo do Compose) no momento em
 * que `1e` é composta — o host consulta o estado real das permissões do Android
 * (`ContextCompat.checkSelfPermission`) e devolve aqui; este módulo nunca solicita permissão.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    driverRegistry: DriverRegistry,
    onboardingPermissionsState: () -> OnboardingPermissionsState,
    onPermissionsSummaryContinue: () -> Unit,
    onRecommendModel: () -> Unit = {},
    onLocationContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.NEARBY_DEVICES)
    },
    onNearbyDevicesContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.PERMISSIONS_SUMMARY)
    },
    onCompatibleDevicesBack: () -> Unit = {
        navController.popBackStack()
    },
) {
    composable(OnboardingRoutes.LOCATION) {
        OnboardingLocationScreen(onContinue = onLocationContinue)
    }

    composable(OnboardingRoutes.NEARBY_DEVICES) {
        OnboardingNearbyDevicesScreen(onContinue = onNearbyDevicesContinue)
    }

    composable(OnboardingRoutes.PERMISSIONS_SUMMARY) {
        OnboardingPermissionsSummaryScreen(
            state = onboardingPermissionsState(),
            onContinue = onPermissionsSummaryContinue,
        )
    }

    composable(OnboardingRoutes.COMPATIBLE_DEVICES) {
        OnboardingCompatibleDevicesScreen(
            driverRegistry = driverRegistry,
            onBack = onCompatibleDevicesBack,
            onRecommendModel = onRecommendModel,
        )
    }
}
