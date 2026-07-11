package com.nethal.feature.devices.ui

import com.nethal.feature.devices.domain.LanDevice

sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    /** `devices` pode ser vazia (nenhum dispositivo encontrado) — UI trata isso como estado
     * vazio explícito, nunca preenche com dado mockado (critério de aceite da issue #86). */
    data class Loaded(val devices: List<LanDevice>) : DevicesUiState

    data object NoNetwork : DevicesUiState

    data class Failed(val message: String) : DevicesUiState
}
