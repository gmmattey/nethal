package com.nethal.feature.toolshistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nethal.core.history.ConnectionHistoryAggregator
import com.nethal.core.history.ConnectionQuality
import com.nethal.core.history.deriveConnectionHistoryEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orquestra a tela Histórico de conexão (issue #96) sobre [ConnectionHistoryAggregator] (issue
 * #104, `:core:history`), que por sua vez lê os samples reais persistidos por
 * [com.nethal.core.scheduling.MeasurementSampleRepository] (issue #112, já mergeada).
 *
 * Carrega uma vez ao entrar na tela ([init]) — [reload] existe para o usuário puxar de novo (ex.:
 * pull-to-refresh futuro), sem precisar recriar o `ViewModel`.
 */
class ConnectionHistoryViewModel(
    private val aggregator: ConnectionHistoryAggregator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectionHistoryUiState>(ConnectionHistoryUiState.Loading)
    val uiState: StateFlow<ConnectionHistoryUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = ConnectionHistoryUiState.Loading

            val blocks = aggregator.aggregate()
            val hasRealData = blocks.any { it.quality != ConnectionQuality.SEM_DADO }
            if (!hasRealData) {
                // Critério de aceite #96: sem dado real, estado vazio explícito — nunca lista mockada.
                _uiState.value = ConnectionHistoryUiState.Empty
                return@launch
            }

            val now = nowMillis()
            val items = deriveConnectionHistoryEvents(blocks).map { it.toUiItem(now) }
            _uiState.value = ConnectionHistoryUiState.Content(items)
        }
    }
}
