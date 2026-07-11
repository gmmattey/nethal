package com.nethal.feature.devices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nethal.feature.devices.domain.LanDeviceScanner
import com.nethal.feature.devices.domain.LanScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tela "Dispositivos" (issues #86/#105) — dispara o scan de LAN ao ser criada e expõe o
 * resultado. Sem ação de escrita: bloquear/permitir dispositivo (visto no protótipo `3i`/`3j`)
 * não tem capability nenhuma implementada ainda (explicitamente fora de escopo da #105) — a tela
 * (`DevicesScreen`) não renderiza seção "Bloqueados" nem botão "Permitir" com dado inventado.
 */
class DevicesViewModel(
    private val scanner: LanDeviceScanner,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = DevicesUiState.Loading
        viewModelScope.launch {
            _uiState.value = runCatching { scanner.scan() }.fold(
                onSuccess = { result ->
                    when (result) {
                        is LanScanResult.Success -> DevicesUiState.Loaded(result.devices)
                        LanScanResult.NoNetwork -> DevicesUiState.NoNetwork
                    }
                },
                onFailure = { error ->
                    DevicesUiState.Failed(error.message ?: "Falha ao buscar dispositivos na rede.")
                },
            )
        }
    }
}
