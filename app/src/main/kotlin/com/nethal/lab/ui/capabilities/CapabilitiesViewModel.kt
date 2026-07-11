package com.nethal.lab.ui.capabilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.catalog.DriverRegistry
import com.nethal.core.model.CapabilityId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orquestra a Tela 4 — Capabilities (spec §11): lê cada [CapabilityId] do vocabulário oficial
 * usando a MESMA sessão (mesma instância de [CapabilityEngine]) já autenticada com sucesso no
 * cluster de Login (`:feature:pairing-auth`, telas 2c/2e) — recebida pronta via construtor
 * (handoff feito por `PairingAuthViewModel.captureAuthenticatedSession()`/`NetHalNavHost`), nunca
 * cria uma sessão nova nem pede credencial de novo.
 *
 * `driverRegistry`/`matchedProfileId` só servem para exibir vendor/model no cabeçalho da tela —
 * mesmo padrão de resolução de profile já usado por `PairingAuthViewModel`, não uma segunda fonte
 * de verdade sobre qual driver está ativo (a sessão em si já está resolvida contra o driver certo,
 * montada no cluster de Login).
 *
 * Lê sequencialmente (não em paralelo): [CapabilityEngine.readCapability] pode disparar uma
 * renovação automática de sessão por trás (`SessionExpired`), e essa renovação não foi desenhada
 * para chamadas concorrentes — mesmo raciocínio conservador do resto do NetHAL.
 */
class CapabilitiesViewModel(
    private val capabilityEngine: CapabilityEngine?,
    matchedProfileId: String?,
    driverRegistry: DriverRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CapabilitiesUiState>(CapabilitiesUiState.Loading)
    val uiState: StateFlow<CapabilitiesUiState> = _uiState.asStateFlow()

    private val profile = matchedProfileId?.let { id -> driverRegistry.profiles().firstOrNull { it.profileId == id } }

    init {
        loadCapabilities()
    }

    private fun loadCapabilities() {
        val engine = capabilityEngine
        val currentProfile = profile
        if (engine == null || !engine.isSessionActive || currentProfile == null) {
            _uiState.value = CapabilitiesUiState.SessionUnavailable(
                reason = "Nenhuma sessão autenticada chegou até esta tela. Volte e teste as " +
                    "credenciais na tela de autenticação antes de listar as capabilities.",
            )
            return
        }

        _uiState.value = CapabilitiesUiState.Loading
        viewModelScope.launch {
            val items = CapabilityId.entries.map { id -> CapabilityItem(id = id, result = engine.readCapability(id)) }
            _uiState.value = CapabilitiesUiState.Loaded(
                vendor = currentProfile.vendor,
                model = currentProfile.model,
                items = items,
            )
        }
    }

    /**
     * Encerra a sessão recebida da Tela 5 — chamar de `DisposableEffect`/`onDispose` na Tela 4 ao
     * sair de composição (avançar para o Relatório ou voltar). Este é o ponto final da sessão: a
     * Tela 6 só exibe os itens já lidos aqui, nunca chama `readCapability` de novo.
     */
    fun closeSession() {
        capabilityEngine?.closeSession()
    }
}
