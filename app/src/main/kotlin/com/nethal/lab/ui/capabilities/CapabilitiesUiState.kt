package com.nethal.lab.ui.capabilities

import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.model.CapabilityId

/**
 * Estado da Tela 4 — Capabilities (spec §11): lista de capabilities com estado e, para todo item
 * que não estiver `AVAILABLE`, o motivo (campo `reason` de [CapabilityReadResult] — mesmo
 * vocabulário do core, sem reinterpretação nova aqui).
 *
 * Lê a partir da MESMA sessão autenticada aberta no cluster de Login (`:feature:pairing-auth`,
 * telas 2c/2e) — nunca abre uma nova (ver `CapabilitiesViewModel`, que recebe o `CapabilityEngine`
 * já ativo via `PairingAuthViewModel.captureAuthenticatedSession()`/`NetHalNavHost`, mesmo padrão
 * de estado compartilhado já usado ali para `NetworkTarget` entre Discovery e EquipmentDetected).
 */
sealed interface CapabilitiesUiState {

    /** Lendo cada [CapabilityId] do vocabulário oficial via `CapabilityEngine.readCapability`, sequencialmente. */
    data object Loading : CapabilitiesUiState

    /**
     * Não há sessão autenticada ativa disponível para esta tela — estado perdido (processo
     * recriado, navegação direta para esta rota) ou a sessão da Tela 5 caiu entre o "Testar" e o
     * "Continuar". Nunca finge ter capabilities: manda voltar para autenticar de novo.
     */
    data class SessionUnavailable(val reason: String) : CapabilitiesUiState

    data class Loaded(
        val vendor: String,
        val model: String,
        val items: List<CapabilityItem>,
    ) : CapabilitiesUiState
}

/**
 * Um item da lista da Tela 4: o [CapabilityId] oficial e o resultado bruto de
 * `CapabilityEngine.readCapability(id)` — [CapabilityReadResult] chega inalterado até a UI, sem
 * nenhuma capability inventada nem estado sintético que a Driver Family não devolveu.
 *
 * Reaproveitado como está pela Tela 6 (Relatório) — "dados lidos"/"capabilities"/"erros" são só
 * recortes desta mesma lista por tipo de [CapabilityReadResult], não uma cópia com outro formato.
 */
data class CapabilityItem(
    val id: CapabilityId,
    val result: CapabilityReadResult,
)
