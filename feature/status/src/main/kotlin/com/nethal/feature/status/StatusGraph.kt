package com.nethal.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.navigation.BottomNavDestination

/**
 * Expõe a rota [BottomNavDestination.STATUS] — quem monta o `NavHost` (host de bottom nav, #67,
 * fora deste módulo) decide quando chamar este grafo; este módulo não conhece o host, só a rota que
 * lhe pertence (regra de dependência única da ADR 0002 — `:feature:status` depende só de
 * `:core:model`, `:core:navigation`, `:core:designsystem`, `:core:capability`, `:core:auth`, nunca de
 * outro `:feature:*`).
 *
 * [capabilityEngine] é a MESMA sessão já autenticada no fluxo de pareamento — entregue pronta por
 * quem compõe o grafo, mesmo handoff hoje usado entre Tela 5 (Autenticação) → Tela 4 (Capabilities).
 * Este módulo nunca autentica sozinho e nunca recebe usuário/senha (issue #107) — `null` só
 * significa "sessão perdida/não fornecida ainda", tratado honestamente como
 * [StatusUiState.SessionUnavailable] por [StatusViewModel], nunca como motivo para inventar dado.
 *
 * `:core:auth` está entre as dependências do módulo (contrato de sessão administrativa, decisão de
 * arquitetura do épico) mas não é referenciado diretamente por este arquivo: a sessão em si já
 * chega pronta via [CapabilityEngine] (que internamente já encapsula o mecanismo de autenticação da
 * Driver Family). Fica disponível no classpath para o dia em que esta tela precisar oferecer
 * reautenticação in-place sem reestruturar o módulo — não é dependência morta por acidente, é
 * decisão registrada.
 */
fun NavGraphBuilder.statusGraph(
    capabilityEngine: CapabilityEngine?,
) {
    composable(BottomNavDestination.STATUS.route) {
        val viewModel: StatusViewModel = viewModel(factory = statusViewModelFactory(capabilityEngine))
        StatusScreen(viewModel = viewModel)
    }
}

private fun statusViewModelFactory(capabilityEngine: CapabilityEngine?): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == StatusViewModel::class.java) { "Unknown ViewModel class: $modelClass" }
            return StatusViewModel(capabilityEngine) as T
        }
    }
