package com.nethal.feature.pairingauth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.capability.CapabilitySessionResult
import com.nethal.core.catalog.DriverFamily
import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.catalog.UnknownDriverFamilyException
import com.nethal.core.model.NetworkTarget
import com.nethal.core.protocol.http.HttpTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** `driverFamilyId` do único profile com handshake RSA TOFU documentado nesta rodada — ver KDoc de `PairingAuthUiState.Ready.showTofuWarning`. */
private const val TPLINK_STOK_LUCI_DRIVER_FAMILY_ID = "tplink-stok-luci-driver"

/**
 * Orquestra o cluster de pareamento por autenticação — Login (`2c`, issue #76), Conectando (`2e`,
 * issue #78) e Falha (`2f`, issue #79): resolve o `CompatibilityProfile`/`DriverFamily` reais a
 * partir do `matchedProfileId` produzido pelo Fingerprint Engine na Tela 3, e usa
 * `CapabilityEngine.testCredentials()` — nunca inventa outro caminho de autenticação (ver KDoc de
 * `CapabilityEngine`).
 *
 * Sucessor direto do antigo `AuthenticationViewModel` (`:app`, extraído para este módulo, ADR
 * 0002) — mesma lógica de resolução de driver e teste de credenciais, com duas mudanças de
 * escopo:
 *
 * 1. **Escopo do ViewModel muda de "uma rota" para "o grafo inteiro".** O protótipo novo (`2c` →
 *    `2d` → `2e`/`2f`) espalha o que antes era uma tela só em quatro rotas de navegação distintas
 *    — esta instância é criada uma vez por `pairingAuthGraph()` (escopo do `NavBackStackEntry` da
 *    rota `GRAPH`, mesmo padrão de `PairingDiscoverySharedState`) e reaproveitada por todas elas,
 *    porque o estado de teste de credenciais (`Testing`/`Success`/erro) precisa ser visível tanto
 *    no Login quanto no Conectando quanto na Falha.
 * 2. **[username]/[password] passam a viver aqui, não em `remember` do Composable.** Necessário
 *    para a issue #77 (AC: "voltar [de 2d] retorna ao formulário de login com os dados já
 *    digitados preservados") — um `remember` local seria descartado ao navegar para 2d, porque o
 *    Composable de 2c sai de composição (só o `NavBackStackEntry`/ViewModelStore sobrevive). Isto
 *    **não** reintroduz persistência de credencial: são propriedades comuns (nunca
 *    `SavedStateHandle`/Bundle), então nunca são serializadas — o mesmo raciocínio de
 *    `CapabilityEngine.InMemoryCredential`, só que também cobrindo a fase de digitação, antes do
 *    teste. [onCleared] zera [password] explicitamente quando esta instância é descartada (usuário
 *    sai do grafo de pareamento inteiro — sucesso entregue à próxima tela ou desistência).
 *
 * ## Driver sem sessão real implementada (honestidade, não falha silenciosa)
 *
 * Hoje só `TpLinkStokLuciDriverFamily` (`driverFamilyId` = `"tplink-stok-luci-driver"`) implementa
 * `authenticate()`/`readCapability()` de verdade. As demais Driver Families já registradas em
 * `defaultDriverFamilyRegistry()` usam o `authenticate()` default de `DriverFamily`, que devolve
 * `DriverFamilyAuthResult.Failure("Esta Driver Family ainda não implementa gerenciamento de sessão
 * real (authenticate()).")` sem tocar a rede — mensagem honesta que chega inalterada à tela de
 * Falha (`2f`) via `CredentialTestState.Failure.reason`, em vez de travar ou fingir sucesso.
 *
 * Profiles cujo `driverFamilyId` não tem nenhuma `DriverFamilyFactory` registrada nunca chegam a
 * [PairingAuthUiState.Ready]: `DriverFamilyRegistry.resolve` lança [UnknownDriverFamilyException]
 * durante [resolveDriver], tratada aqui como [PairingAuthUiState.DriverUnavailable] — o grafo
 * (`PairingAuthGraph.kt`) navega direto para a tela de Falha (`2f`) quando isso acontece, sem
 * nunca mostrar o formulário de Login (issue #76: "Estados 'resolvendo driver' e 'driver
 * indisponível' não são implementados [na tela de Login]").
 */
class PairingAuthViewModel(
    private val target: NetworkTarget,
    private val matchedProfileId: String?,
    private val driverRegistry: DriverRegistry,
    private val driverFamilyRegistry: DriverFamilyRegistry,
    private val httpTransport: HttpTransport,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingAuthUiState>(PairingAuthUiState.ResolvingDriver)
    val uiState: StateFlow<PairingAuthUiState> = _uiState.asStateFlow()

    var username by mutableStateOf("admin")
        private set

    var password by mutableStateOf("")
        private set

    private var resolvedDriverFamily: DriverFamily? = null
    private var capabilityEngine: CapabilityEngine? = null

    /**
     * `true` depois de [captureAuthenticatedSession] devolver o [CapabilityEngine] ativo para a
     * próxima tela (Capabilities) assumir — a partir daí [onCleared] não encerra mais a sessão:
     * encerrá-la passa a ser responsabilidade de quem a recebeu.
     */
    private var sessionHandedOff = false

    val isSessionActive: Boolean get() = capabilityEngine?.isSessionActive == true

    init {
        resolveDriver()
    }

    fun onUsernameChanged(value: String) {
        username = value
    }

    fun onPasswordChanged(value: String) {
        password = value
    }

    private fun resolveDriver() {
        val profileId = matchedProfileId
        if (profileId.isNullOrBlank()) {
            _uiState.value = PairingAuthUiState.DriverUnavailable(
                reason = "Nenhum equipamento foi identificado com confiança suficiente na etapa anterior. " +
                    "Volte e corrija a identificação antes de autenticar.",
            )
            return
        }

        val profile = driverRegistry.profiles().firstOrNull { it.profileId == profileId }
        if (profile == null) {
            _uiState.value = PairingAuthUiState.DriverUnavailable(
                reason = "O driver \"$profileId\" não foi encontrado no catálogo local deste app " +
                    "(catálogo pode estar desatualizado).",
            )
            return
        }

        val driverFamily = try {
            driverFamilyRegistry.resolve(profile, target.ip, httpTransport)
        } catch (e: UnknownDriverFamilyException) {
            _uiState.value = PairingAuthUiState.DriverUnavailable(
                reason = "Ainda não existe driver implementado para ${profile.vendor} ${profile.model} " +
                    "nesta versão do app.",
            )
            return
        } catch (e: IllegalArgumentException) {
            _uiState.value = PairingAuthUiState.DriverUnavailable(
                reason = e.message ?: "Endereço do equipamento recusado por segurança.",
            )
            return
        }

        resolvedDriverFamily = driverFamily
        _uiState.value = PairingAuthUiState.Ready(
            vendor = profile.vendor,
            model = profile.model,
            showTofuWarning = profile.driverFamilyId == TPLINK_STOK_LUCI_DRIVER_FAMILY_ID,
            credentialTestState = CredentialTestState.Idle,
        )
    }

    /**
     * Botão "Entrar no modem" do Login (`2c`) — sempre cria um novo `CapabilityEngine` para esta
     * credencial (nunca reaproveita um engine de uma tentativa anterior, mesmo raciocínio
     * conservador do resto do NetHAL: uma nova tentativa é sempre uma nova sessão explícita, não
     * uma renovação silenciosa). Chamado só quando o estado atual é [PairingAuthUiState.Ready] —
     * o grafo garante isso ao desabilitar o botão de submit fora desse estado.
     */
    fun submit() {
        val currentState = _uiState.value as? PairingAuthUiState.Ready ?: return
        val driverFamily = resolvedDriverFamily ?: return
        if (username.isBlank() || password.isBlank()) return

        _uiState.value = currentState.copy(credentialTestState = CredentialTestState.Testing)

        viewModelScope.launch {
            val engine = CapabilityEngine(driverFamily, username, password)
            capabilityEngine = engine
            sessionHandedOff = false
            val result = engine.testCredentials()

            val newTestState = when (result) {
                is CapabilitySessionResult.Active -> CredentialTestState.Success
                is CapabilitySessionResult.InvalidCredentials -> CredentialTestState.InvalidCredentials(result.reason)
                is CapabilitySessionResult.Failure -> CredentialTestState.Failure(result.reason)
            }

            val latestState = _uiState.value as? PairingAuthUiState.Ready ?: return@launch
            _uiState.value = latestState.copy(credentialTestState = newTestState)
        }
    }

    /**
     * Chamado pela tela de Falha (`2f`) ao clicar "Tentar novamente": volta o teste para `Idle` e
     * limpa [password] (nunca [username]) antes de retornar ao Login — decisão de UX própria desta
     * implementação (a issue #79 deixava em aberto "campos limpos ou preservados, decidir com
     * Vera"; sem spec dedicada disponível para este cluster, optei por limpar só a senha, mesmo
     * padrão adotado por a maioria dos apps de login após uma falha, sem forçar o usuário a
     * redigitar o usuário que provavelmente estava certo).
     */
    fun resetAfterFailure() {
        password = ""
        val currentState = _uiState.value as? PairingAuthUiState.Ready ?: return
        _uiState.value = currentState.copy(credentialTestState = CredentialTestState.Idle)
    }

    /**
     * Corrida rara em que a sessão caiu entre [submit] devolver sucesso e a tela Conectando (`2e`)
     * conseguir chamar [captureAuthenticatedSession] logo em seguida (mesmo raciocínio da antiga
     * "Ressalva aberta (revisão Marisa, 2026-07-08)" do `AuthenticationViewModel`) — reaproveita
     * `CredentialTestState.Failure` em vez de inventar um quarto estado só para isto, para que a
     * tela de Falha (`2f`) trate exatamente como qualquer outra falha de conexão honesta.
     */
    fun markSessionLostAfterSuccess() {
        val currentState = _uiState.value as? PairingAuthUiState.Ready ?: return
        _uiState.value = currentState.copy(
            credentialTestState = CredentialTestState.Failure(
                "A sessão foi encerrada antes de continuar. Tente novamente.",
            ),
        )
    }

    /**
     * Entrega a sessão autenticada ativa (mesma instância de [CapabilityEngine], com sessão já
     * aberta por [submit]) para a próxima tela (Capabilities) ler capabilities sem autenticar de
     * novo. Chamado só a partir da tela Conectando (`2e`) ao observar
     * [CredentialTestState.Success] — devolve `null` (nunca um engine morto) se não houver sessão
     * ativa no momento da chamada; o grafo trata esse `null` como falha de conexão (ver
     * `PairingAuthGraph.kt`).
     *
     * Marca [sessionHandedOff] = `true`: a partir daqui [onCleared] deixa de fechar a sessão
     * aqui, porque a posse dela passou para quem a recebeu.
     */
    fun captureAuthenticatedSession(): CapabilityEngine? {
        val engine = capabilityEngine?.takeIf { it.isSessionActive } ?: return null
        sessionHandedOff = true
        return engine
    }

    /**
     * Encerra a sessão local (se houver) e descarta a credencial em memória do `CapabilityEngine`
     * e deste ViewModel — chamado automaticamente por [onCleared] quando o `NavBackStackEntry` do
     * grafo (`pairing_auth`) é finalmente removido da pilha (sucesso entregue adiante ou usuário
     * desistiu do pareamento), nunca a cada troca de tela dentro do próprio cluster (diferente do
     * antigo `AuthenticationScreen.closeSession()` via `DisposableEffect` por tela — aqui o
     * ViewModel é do grafo, não da rota).
     */
    override fun onCleared() {
        password = ""
        if (!sessionHandedOff) {
            capabilityEngine?.closeSession()
        }
    }
}
