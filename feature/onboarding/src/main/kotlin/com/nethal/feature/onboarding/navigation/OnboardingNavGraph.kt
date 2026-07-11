package com.nethal.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.consent.ConsentRepository
import com.nethal.feature.onboarding.OnboardingPermissionsState
import com.nethal.feature.onboarding.ui.OnboardingCompatibleDevicesScreen
import com.nethal.feature.onboarding.ui.OnboardingLocationScreen
import com.nethal.feature.onboarding.ui.OnboardingNearbyDevicesScreen
import com.nethal.feature.onboarding.ui.OnboardingNotificationsScreen
import com.nethal.feature.onboarding.ui.OnboardingPermissionsSummaryScreen
import com.nethal.feature.onboarding.ui.OnboardingWelcomeScreen

/**
 * Rotas expostas por `:feature:onboarding`. As 6 telas do fluxo (#68/#69/#70/#71/#72/#73) — `1a`
 * (boas-vindas) é a primeira da sequência, `1d` (notificações) fica entre `1c` e `1e`, igual ao
 * protótipo (`1a`→`1b`→`1c`→`1d`→`1e`, `1f` acessível a partir de `1a`).
 */
object OnboardingRoutes {
    const val WELCOME = "onboarding/welcome" // 1a
    const val LOCATION = "onboarding/location" // 1b
    const val NEARBY_DEVICES = "onboarding/nearby-devices" // 1c
    const val NOTIFICATIONS = "onboarding/notifications" // 1d
    const val PERMISSIONS_SUMMARY = "onboarding/permissions-summary" // 1e
    const val COMPATIBLE_DEVICES = "onboarding/compatible-devices" // 1f
}

/**
 * Grafo de navegação do onboarding, no formato `NavGraphBuilder.xyzGraph(...)` documentado em
 * `:core:navigation` (`NavigationContracts.kt`) para o composition root (#67/#113) plugar sem
 * `:feature:onboarding` depender de nenhum outro módulo `:feature:*`.
 *
 * Sequência padrão ligada aqui: `1a`→`1b`→`1c`→`1d`→`1e`. `1f` não é sequencial (acessível a
 * partir de `1a` via link "Ver dispositivos compatíveis", como no protótipo) — por isso não tem
 * callback de "continue" próprio, só [onCompatibleDevicesBack].
 *
 * [onboardingPermissionsState] é lido (não observado como estado reativo do Compose) no momento em
 * que `1e` é composta — o host consulta o estado real das permissões do Android
 * (`ContextCompat.checkSelfPermission`) e devolve aqui; `1b`/`1c` nunca solicitam permissão real
 * (só `1d`, notificações, que solicita `POST_NOTIFICATIONS` diretamente).
 *
 * [consentRepository] é injetado direto nas telas que gravam consentimento (`1a` grava
 * `NETWORK_AUTHORIZATION`/`READ_STATUS`; `1d` grava `TELEMETRY_BETA`) — mesmo padrão de injeção
 * direta já usado para [driverRegistry] em `1f`, sem `ViewModel` neste módulo.
 *
 * [onViewPrivacy] é responsabilidade do host: destino real (item de Privacidade em Configurações,
 * issue #85) ainda não existe neste módulo — decisão #66.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    driverRegistry: DriverRegistry,
    consentRepository: ConsentRepository,
    onboardingPermissionsState: () -> OnboardingPermissionsState,
    onPermissionsSummaryContinue: () -> Unit,
    onViewPrivacy: () -> Unit = {},
    onRecommendModel: () -> Unit = {},
    onWelcomeContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.LOCATION)
    },
    onViewCompatibleDevices: () -> Unit = {
        navController.navigate(OnboardingRoutes.COMPATIBLE_DEVICES)
    },
    onLocationContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.NEARBY_DEVICES)
    },
    onNearbyDevicesContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.NOTIFICATIONS)
    },
    onNotificationsContinue: () -> Unit = {
        navController.navigate(OnboardingRoutes.PERMISSIONS_SUMMARY)
    },
    onCompatibleDevicesBack: () -> Unit = {
        navController.popBackStack()
    },
) {
    composable(OnboardingRoutes.WELCOME) {
        OnboardingWelcomeScreen(
            consentRepository = consentRepository,
            onStartDiagnosis = onWelcomeContinue,
            onViewPrivacy = onViewPrivacy,
            onViewCompatibleDevices = onViewCompatibleDevices,
        )
    }

    composable(OnboardingRoutes.LOCATION) {
        OnboardingLocationScreen(onContinue = onLocationContinue)
    }

    composable(OnboardingRoutes.NEARBY_DEVICES) {
        OnboardingNearbyDevicesScreen(onContinue = onNearbyDevicesContinue)
    }

    composable(OnboardingRoutes.NOTIFICATIONS) {
        OnboardingNotificationsScreen(
            consentRepository = consentRepository,
            onContinue = onNotificationsContinue,
        )
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
