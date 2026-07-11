package com.nethal.feature.pairingauth

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.model.NetworkTarget
import com.nethal.core.protocol.http.DefaultHttpTransport
import com.nethal.core.protocol.http.HttpTransport
import com.nethal.core.protocol.http.HttpTransportConfig
import com.nethal.feature.pairingauth.connecting.ConnectingScreen
import com.nethal.feature.pairingauth.failure.ConnectionFailureScreen
import com.nethal.feature.pairingauth.failure.FailureKind
import com.nethal.feature.pairingauth.login.LoginScreen
import com.nethal.feature.pairingauth.passwordhelp.PasswordHelpScreen
import java.net.URL

/** Rotas internas do módulo (issues #76, #77, #78, #79) — nenhuma exposta fora do grafo. Sem argumentos de rota: `kind`/`reason` da tela de Falha (`2f`) são derivados do `PairingAuthUiState` compartilhado (ver [failureDetailsFrom]), não serializados na URL de navegação. */
object PairingAuthRoutes {
    const val GRAPH = "pairing_auth"
    const val LOGIN = "pairing_auth/login"
    const val PASSWORD_HELP = "pairing_auth/password_help"
    const val CONNECTING = "pairing_auth/connecting"
    const val FAILURE = "pairing_auth/failure"
}

/**
 * Grafo de navegação do cluster de pareamento por autenticação (issues #76, #77, #78, #79):
 * Login (`2c`) → Conectando (`2e`) → sucesso ([onAuthenticated]) *ou* Falha (`2f`), com Login ↔
 * "Onde encontrar a senha" (`2d`) acessível a partir do Login e também da Falha.
 *
 * [target]/[matchedProfileId] vêm do cluster de descoberta (`:feature:pairing-discovery`, issues
 * #74/#75/#80-82) através do composition root (`:app`) — este módulo nunca depende de outro
 * `:feature:*` (ADR 0002), então recebe esses dois valores como parâmetros simples, mesmo padrão
 * já usado por `NetHalNavHost` antes desta extração. [onTargetMissing] cobre o mesmo caso de
 * estado perdido que o resto do app trata (`target == null`, ex. processo recriado): o composition
 * root decide para onde voltar (hoje, para `PairingDiscoveryRoutes.GRAPH`).
 *
 * [onAuthenticated] é o único ponto de saída de sucesso — dispara só depois que a tela Conectando
 * (`2e`) observa `CredentialTestState.Success` e consegue capturar a sessão ativa
 * ([PairingAuthViewModel.captureAuthenticatedSession]). Diferente do antigo `AuthenticationScreen`
 * (`:app`), o `CapabilityEngine` entregue aqui **nunca é nulo**: a corrida rara em que a sessão cai
 * entre o sucesso do teste e a captura (ressalva Marisa, 2026-07-08, revisada nesta extração) agora
 * é tratada como uma falha de conexão normal, redirecionando para `2f`, em vez de propagar um
 * `CapabilityEngine?` nulo para o composition root decidir.
 *
 * ## Ressalva aberta — profile sem nenhuma `DriverFamilyFactory` registrada
 *
 * Quando `PairingAuthUiState.DriverUnavailable` é o estado inicial (profile sem driver
 * implementado nesta versão do app — caso raro, nenhum dos 3 drivers em produção hoje cai aqui),
 * o Login (`2c`) nunca chega a aparecer: a rota navega direto para a Falha (`2f`) via
 * `LaunchedEffect`. "Tentar novamente" nesse caso volta para o Login, que imediatamente detecta o
 * mesmo estado e navega de volta para a Falha — um "ping-pong" sem novo I/O de rede a cada clique
 * (backstack não cresce, `popBackStack(LOGIN, inclusive = false)` sempre remove a Falha anterior
 * antes de empurrar a nova), mas visualmente pouco elegante. Não corrigido nesta rodada — decisão
 * de produto (ex.: "Tentar novamente" desse caso específico deveria sair do grafo inteiro em vez de
 * voltar ao Login?) fica em aberto para Rafael.
 */
fun NavGraphBuilder.pairingAuthGraph(
    navController: NavHostController,
    target: NetworkTarget?,
    matchedProfileId: String?,
    dependencies: PairingAuthDependencies,
    onAuthenticated: (CapabilityEngine) -> Unit,
    onTargetMissing: () -> Unit,
) {
    navigation(startDestination = PairingAuthRoutes.LOGIN, route = PairingAuthRoutes.GRAPH) {

        composable(PairingAuthRoutes.LOGIN) { entry ->
            if (target == null) {
                LaunchedEffect(Unit) { onTargetMissing() }
                return@composable
            }

            val parentEntry = remember(entry) { navController.getBackStackEntry(PairingAuthRoutes.GRAPH) }
            val viewModel: PairingAuthViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = pairingAuthViewModelFactory(target, matchedProfileId, dependencies),
            )
            val uiState by viewModel.uiState.collectAsState()

            when (val state = uiState) {
                is PairingAuthUiState.DriverUnavailable -> {
                    LaunchedEffect(state) {
                        navController.navigate(PairingAuthRoutes.FAILURE)
                    }
                }
                else -> {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToPasswordHelp = { navController.navigate(PairingAuthRoutes.PASSWORD_HELP) },
                        onSubmit = {
                            viewModel.submit()
                            navController.navigate(PairingAuthRoutes.CONNECTING)
                        },
                    )
                }
            }
        }

        composable(PairingAuthRoutes.PASSWORD_HELP) {
            PasswordHelpScreen(onBack = { navController.popBackStack() })
        }

        composable(PairingAuthRoutes.CONNECTING) { entry ->
            if (target == null) {
                LaunchedEffect(Unit) { onTargetMissing() }
                return@composable
            }

            val parentEntry = remember(entry) { navController.getBackStackEntry(PairingAuthRoutes.GRAPH) }
            val viewModel: PairingAuthViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = pairingAuthViewModelFactory(target, matchedProfileId, dependencies),
            )
            val uiState by viewModel.uiState.collectAsState()
            val readyState = uiState as? PairingAuthUiState.Ready

            LaunchedEffect(readyState?.credentialTestState) {
                when (val testState = readyState?.credentialTestState) {
                    is CredentialTestState.Success -> {
                        val engine = viewModel.captureAuthenticatedSession()
                        if (engine != null) {
                            onAuthenticated(engine)
                        } else {
                            // Corrida rara: a sessão caiu entre o teste bem-sucedido e a captura
                            // (mesma sessão, nenhuma outra tentativa) — tratada como falha de
                            // conexão normal (reaproveita CredentialTestState.Failure), nunca
                            // propagada como engine nulo para fora do grafo.
                            viewModel.markSessionLostAfterSuccess()
                            navController.navigate(PairingAuthRoutes.FAILURE)
                        }
                    }
                    is CredentialTestState.InvalidCredentials, is CredentialTestState.Failure -> {
                        navController.navigate(PairingAuthRoutes.FAILURE)
                    }
                    else -> Unit
                }
            }

            val deviceLabel = readyState?.let { "${it.vendor} ${it.model}".trim() }?.takeIf { it.isNotBlank() }
            ConnectingScreen(deviceLabel = deviceLabel)
        }

        composable(PairingAuthRoutes.FAILURE) { entry ->
            if (target == null) {
                LaunchedEffect(Unit) { onTargetMissing() }
                return@composable
            }

            val parentEntry = remember(entry) { navController.getBackStackEntry(PairingAuthRoutes.GRAPH) }
            val viewModel: PairingAuthViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = pairingAuthViewModelFactory(target, matchedProfileId, dependencies),
            )
            val uiState by viewModel.uiState.collectAsState()
            val (kind, reason) = failureDetailsFrom(uiState)

            ConnectionFailureScreen(
                kind = kind,
                reason = reason,
                onRetry = {
                    viewModel.resetAfterFailure()
                    navController.popBackStack(route = PairingAuthRoutes.LOGIN, inclusive = false)
                },
                onFindPassword = { navController.navigate(PairingAuthRoutes.PASSWORD_HELP) },
            )
        }
    }
}

/** Deriva (categoria, mensagem) da tela de Falha (`2f`) a partir do estado atual — nunca um texto genérico, sempre o `reason` real devolvido pelo driver/`CapabilityEngine` (issue #79). */
private fun failureDetailsFrom(state: PairingAuthUiState): Pair<FailureKind, String> = when (state) {
    is PairingAuthUiState.DriverUnavailable -> FailureKind.DRIVER_UNAVAILABLE to state.reason
    is PairingAuthUiState.Ready -> when (val testState = state.credentialTestState) {
        is CredentialTestState.InvalidCredentials -> FailureKind.INVALID_CREDENTIALS to testState.reason
        is CredentialTestState.Failure -> FailureKind.CONNECTION_FAILURE to testState.reason
        // Estado defensivo: a tela de Falha só é alcançada a partir de InvalidCredentials/Failure
        // (ver composable(CONNECTING) acima) — Idle/Testing/Success aqui indicam navegação direta
        // fora do fluxo esperado (ex. restauração de processo em ponto intermediário).
        else -> FailureKind.CONNECTION_FAILURE to "Não foi possível concluir a autenticação com o modem."
    }
    is PairingAuthUiState.ResolvingDriver -> FailureKind.CONNECTION_FAILURE to "Não foi possível concluir a autenticação com o modem."
}

private fun pairingAuthViewModelFactory(
    target: NetworkTarget,
    matchedProfileId: String?,
    dependencies: PairingAuthDependencies,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PairingAuthViewModel::class.java) { "Unknown ViewModel class: $modelClass" }
        return PairingAuthViewModel(
            target = target,
            matchedProfileId = matchedProfileId,
            driverRegistry = dependencies.driverRegistry,
            driverFamilyRegistry = dependencies.driverFamilyRegistry,
            httpTransport = buildPairingAuthHttpTransport(),
        ) as T
    }
}

/**
 * Transporte HTTP único usado por este grafo para qualquer `DriverFamily` resolvida pelo
 * `DriverFamilyRegistry`. Só `tplink-stok-luci-driver` tem `authenticate()` real hoje (as demais
 * Driver Families usam o default de `DriverFamily.authenticate()`, que nunca chama a rede — ver
 * KDoc de [PairingAuthViewModel]), então os parâmetros abaixo espelham exatamente a configuração
 * já validada contra hardware físico em `core/tooling/ManualCheckRunner.kt` (`runTplinkC6Stok`) —
 * headers, `Content-Type` e `Referer` exigidos pelo firmware real do TP-Link Archer C6, plataforma
 * stok/luci. Movido de `NetHalViewModelFactory` (`:app`) para cá nesta extração — é conhecimento de
 * autenticação, não do composition root (ADR 0002).
 *
 * Quando outra Driver Family ganhar `authenticate()` real com requisitos de transporte diferentes,
 * esta função vai precisar decidir por `driverFamilyId` (nunca por fabricante) — decisão de
 * arquitetura para revisar com Rafael quando isso acontecer, não antecipada aqui.
 */
private fun buildPairingAuthHttpTransport(): HttpTransport = DefaultHttpTransport(
    HttpTransportConfig(
        connectTimeoutMillis = 10_000,
        getReadTimeoutMillis = 20_000,
        postReadTimeoutMillis = 20_000,
        getAcceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        postAcceptHeader = "application/json, text/javascript, */*; q=0.01",
        postContentType = "application/x-www-form-urlencoded; charset=UTF-8",
        extraPostHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
        // Timestamp fixo (`1693386897767`) confirmado por captura HAR real (Playwright, 2026-07-11,
        // firmware `1.1.10 Build 20230830 rel.69433(5553)`) — o navegador real sempre envia esse
        // sufixo no Referer, tanto na fase de login (`login.html?t=...`) quanto nas leituras
        // autenticadas seguintes (`index.<timestamp>.html`), nunca a URL "limpa" sem sufixo que esta
        // função enviava antes. Mesmo valor já usado por `TpLinkStokLuciManualCheck.kt` (evidência
        // anterior, mesma unidade física) — alinhado aqui pela primeira vez. É um carimbo de build do
        // firmware, não algo por sessão; se um firmware diferente usar outro timestamp, este valor
        // precisa ser recapturado (não há como derivar sem reler a página de login real).
        postRefererProvider = { url ->
            val base = URL(url)
            val root = "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}"
            if (url.contains("/cgi-bin/luci/;stok=/login")) {
                "$root/webpages/login.html?t=1693386897767"
            } else {
                "$root/webpages/index.1693386897767.html"
            }
        },
        followRedirectsManually = false,
    ),
)
