package com.nethal.lab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.model.NetworkTarget
import com.nethal.lab.ui.authentication.AuthenticationScreen
import com.nethal.lab.ui.authentication.AuthenticationViewModel
import com.nethal.lab.ui.capabilities.CapabilitiesScreen
import com.nethal.lab.ui.capabilities.CapabilitiesViewModel
import com.nethal.lab.ui.capabilities.CapabilityItem
import com.nethal.lab.ui.common.NetHalViewModelFactory
import com.nethal.lab.ui.discovery.DiscoveryScreen
import com.nethal.lab.ui.discovery.DiscoveryViewModel
import com.nethal.lab.ui.equipment.EquipmentDetectedScreen
import com.nethal.lab.ui.equipment.EquipmentDetectedViewModel
import com.nethal.lab.ui.onboarding.BetaOptInScreen
import com.nethal.lab.ui.onboarding.BetaOptInViewModel
import com.nethal.lab.ui.onboarding.WelcomeScreen
import com.nethal.lab.ui.onboarding.WelcomeViewModel
import com.nethal.lab.ui.privacy.PrivacyScreen
import com.nethal.lab.ui.report.ReportScreen
import com.nethal.lab.ui.report.ReportViewModel

private object Routes {
    const val WELCOME = "welcome"
    const val PRIVACY = "privacy"
    const val BETA_OPT_IN = "beta_opt_in"
    const val DISCOVERY = "discovery"
    const val TARGET_SELECTED = "target_selected"
    const val AUTHENTICATION = "authentication"
    // Ordem de navegação deste app (Tela 3 → Tela 5 → Tela 4 → Tela 6) diverge da numeração da
    // spec (Tela 3 → Tela 4 → Tela 5) — decisão confirmada (ver KDoc de AuthenticationUiState):
    // ler capabilities exige sessão autenticada nesta implementação, então autenticar vem antes.
    const val CAPABILITIES = "capabilities"
    const val REPORT = "report"
    // Host do "modo uso diário" (#67, ADR 0002 Fase 2) — `BottomNavHost`, com as abas Status,
    // Rede, Dispositivos e Configurações. Substitui a antiga rota órfã `settings`: Configurações
    // agora vive como aba dentro deste host, não como destino solto no funil de pareamento.
    const val HOME = "home"
}

@Composable
fun NetHalNavHost(
    viewModelFactory: NetHalViewModelFactory,
    navController: NavHostController = rememberNavController(),
) {
    // Guardado no escopo do NavHost (não dentro de um `composable {}`) para sobreviver à
    // navegação entre "discovery" → "target_selected" → "authentication" → "capabilities" →
    // "report". Os ViewModels dessas telas precisam desses valores no construtor (não dá para
    // injetar via `SavedStateHandle` sem Parcelable/serializer dedicado nesta entrega) — factory
    // por instância cobre isso.
    var selectedTarget by remember { mutableStateOf<NetworkTarget?>(null) }
    var matchedProfileId by remember { mutableStateOf<String?>(null) }
    // Sessão autenticada entregue pela Tela 5 (`AuthenticationViewModel.captureAuthenticatedSession`)
    // para a Tela 4 reaproveitar sem autenticar de novo — mesmo padrão de estado compartilhado
    // usado acima para `NetworkTarget`. `null` até a Tela 5 confirmar sucesso.
    var authenticatedCapabilityEngine by remember { mutableStateOf<CapabilityEngine?>(null) }
    // Itens já lidos pela Tela 4, entregues à Tela 6 para exibição — a sessão em si já foi
    // encerrada por `CapabilitiesViewModel.closeSession` antes desta navegação.
    var reportItems by remember { mutableStateOf<List<CapabilityItem>>(emptyList()) }

    /** Limpa todo o estado de uma sessão de diagnóstico anterior — chamado ao concluir/reiniciar o fluxo (Tela 6 → boas-vindas). */
    fun resetDiagnosisState() {
        selectedTarget = null
        matchedProfileId = null
        authenticatedCapabilityEngine = null
        reportItems = emptyList()
    }

    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            val viewModel: WelcomeViewModel = viewModel(factory = viewModelFactory)
            WelcomeScreen(
                viewModel = viewModel,
                onStartDiagnosis = { navController.navigate(Routes.BETA_OPT_IN) },
                onViewPrivacy = { navController.navigate(Routes.PRIVACY) },
                onExit = { /* encerrado pela Activity host */ },
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BETA_OPT_IN) {
            val viewModel: BetaOptInViewModel = viewModel(factory = viewModelFactory)
            BetaOptInScreen(
                viewModel = viewModel,
                onDecided = {
                    navController.navigate(Routes.DISCOVERY) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DISCOVERY) {
            val viewModel: DiscoveryViewModel = viewModel(factory = viewModelFactory)

            DiscoveryScreen(
                viewModel = viewModel,
                onSingleCandidateReady = { target ->
                    selectedTarget = target
                    navController.navigate(Routes.TARGET_SELECTED)
                },
                onCandidateChosen = { target ->
                    selectedTarget = target
                    navController.navigate(Routes.TARGET_SELECTED)
                },
            )
        }

        composable(Routes.TARGET_SELECTED) {
            val target = selectedTarget
            if (target == null) {
                // Estado perdido (ex.: processo recriado) — volta para a descoberta em vez
                // de mostrar uma tela sem dado nenhum.
                navController.navigate(Routes.DISCOVERY) {
                    popUpTo(Routes.DISCOVERY) { inclusive = true }
                }
            } else {
                val viewModel: EquipmentDetectedViewModel = viewModel(
                    factory = viewModelFactory.forEquipmentDetected(target),
                )
                EquipmentDetectedScreen(
                    viewModel = viewModel,
                    onContinue = { profileId ->
                        matchedProfileId = profileId
                        navController.navigate(Routes.AUTHENTICATION)
                    },
                )
            }
        }

        composable(Routes.AUTHENTICATION) {
            val target = selectedTarget
            if (target == null) {
                // Mesmo raciocínio de TARGET_SELECTED acima — estado perdido, volta para a
                // descoberta em vez de tentar autenticar sem NetworkTarget nenhum.
                navController.navigate(Routes.DISCOVERY) {
                    popUpTo(Routes.DISCOVERY) { inclusive = true }
                }
            } else {
                val viewModel: AuthenticationViewModel = viewModel(
                    factory = viewModelFactory.forAuthentication(target, matchedProfileId),
                )
                AuthenticationScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = { engine ->
                        authenticatedCapabilityEngine = engine
                        navController.navigate(Routes.CAPABILITIES)
                    },
                )
            }
        }

        composable(Routes.CAPABILITIES) {
            val viewModel: CapabilitiesViewModel = viewModel(
                factory = viewModelFactory.forCapabilities(authenticatedCapabilityEngine, matchedProfileId),
            )
            CapabilitiesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onContinue = { items ->
                    reportItems = items
                    navController.navigate(Routes.REPORT)
                },
            )
        }

        composable(Routes.REPORT) {
            val viewModel: ReportViewModel = viewModel(
                factory = viewModelFactory.forReport(matchedProfileId, reportItems),
            )
            ReportScreen(
                viewModel = viewModel,
                onFinish = {
                    resetDiagnosisState()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            BottomNavHost(viewModelFactory = viewModelFactory)
        }
    }
}
