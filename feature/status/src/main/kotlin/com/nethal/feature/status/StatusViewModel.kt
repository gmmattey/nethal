package com.nethal.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nethal.core.capability.CapabilityEngine
import com.nethal.core.catalog.CapabilityReadResult
import com.nethal.core.model.CapabilityId
import com.nethal.core.model.CapabilityPayload
import com.nethal.core.model.WifiBand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orquestra a tela Status (issue #83) com o mecanismo de atualização contínua da issue #107.
 *
 * ## Mecanismo de atualização escolhido (registrado no PR, critério de aceite de #107)
 *
 * Combinação de **poll em intervalo enquanto a tela está visível** ([onScreenStarted]/[POLL_INTERVAL_MILLIS])
 * + **pull-to-refresh manual** ([refreshNow], design system seção "Navegação por gestos" 1p — a
 * única forma de atualização que a spec visual documenta explicitamente). WorkManager foi descartado
 * de propósito: ele existe para trabalho garantido mesmo em background/processo morto, exatamente o
 * oposto do que a skill `/seguranca-nethal` pede aqui — a sessão administrativa não pode ficar viva
 * fora do primeiro plano.
 *
 * ## Ciclo de vida da sessão (skill `/seguranca-nethal`)
 *
 * [capabilityEngine] chega já autenticado (mesmo handoff usado hoje entre Tela 5 → Tela 4, ver
 * `AuthenticationViewModel.captureAuthenticatedSession`) — este módulo nunca autentica sozinho e
 * nunca vê credencial crua. [onScreenStarted] (chamado pela tela ao entrar em composição) liga o
 * poll; [onScreenStopped] (chamado ao sair de composição — inclusive ao trocar de aba na bottom nav,
 * que remove este composable da árvore mesmo com o `ViewModel` sobrevivendo via
 * `saveState`/`restoreState`) cancela o poll E encerra a sessão local
 * ([CapabilityEngine.closeSession]) — nunca fica lendo o equipamento com a tela fora de composição.
 * [onCleared] repete o encerramento como rede de segurança (processo matando o `ViewModel` sem passar
 * pelo `DisposableEffect` da tela).
 *
 * Encerrar a sessão descarta a credencial em memória de [CapabilityEngine] (ver KDoc de
 * `CapabilityEngine.closeSession`) — reentrar nesta tela depois de [onScreenStopped] sem uma nova
 * sessão autenticada resulta em [StatusUiState.SessionUnavailable] honesto, não numa tentativa de
 * reautenticar sozinha (este módulo não tem para onde voltar sem depender de outro `:feature:*`,
 * proibido pela ADR 0002). Provisionar uma sessão nova ao reabrir a aba é decisão do composition
 * root (`:app`), fora do escopo deste módulo.
 */
class StatusViewModel(
    private val capabilityEngine: CapabilityEngine?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatusUiState>(StatusUiState.Loading)
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var pollingJob: Job? = null

    /** Chamado pela tela ao entrar em composição — inicia (ou reinicia, se já tinha parado) o poll ao vivo. */
    fun onScreenStarted() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                loadStatus()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /** Chamado pela tela ao sair de composição — para o poll e encerra a sessão administrativa (nunca fica aberta em background). */
    fun onScreenStopped() {
        pollingJob?.cancel()
        pollingJob = null
        capabilityEngine?.closeSession()
    }

    /** Pull-to-refresh (design system 1p) — leitura imediata, sem esperar o próximo tick do poll. */
    fun refreshNow() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _isRefreshing.value = true
            loadStatus()
            _isRefreshing.value = false
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                loadStatus()
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        capabilityEngine?.closeSession()
    }

    /** Lê sequencialmente (mesmo raciocínio de `CapabilitiesViewModel`: renovação de sessão não foi desenhada para leituras concorrentes). */
    private suspend fun loadStatus() {
        val engine = capabilityEngine
        if (engine == null || !engine.isSessionActive) {
            _uiState.value = StatusUiState.SessionUnavailable(
                reason = "Nenhuma sessão administrativa ativa chegou até a tela de Status — " +
                    "pareie o equipamento novamente para ver dado ao vivo.",
            )
            return
        }

        val deviceInfoResult = engine.readCapability(CapabilityId.READ_DEVICE_INFO)
        val wifiResult = engine.readCapability(CapabilityId.READ_WIFI_STATUS)
        val wanResult = engine.readCapability(CapabilityId.READ_WAN_STATUS)

        _uiState.value = buildLoadedState(deviceInfoResult, wifiResult, wanResult)
    }

    private fun buildLoadedState(
        deviceInfoResult: CapabilityReadResult,
        wifiResult: CapabilityReadResult,
        wanResult: CapabilityReadResult,
    ): StatusUiState.Loaded {
        val deviceInfo = (deviceInfoResult as? CapabilityReadResult.Success)
            ?.payload.let { it as? CapabilityPayload.DeviceInfo }?.info
        val wifi = (wifiResult as? CapabilityReadResult.Success)
            ?.payload.let { it as? CapabilityPayload.Wifi }?.status
        val wan = (wanResult as? CapabilityReadResult.Success)
            ?.payload.let { it as? CapabilityPayload.Wan }?.status

        val equipmentLabel = listOfNotNull(deviceInfo?.vendor, deviceInfo?.model)
            .joinToString(" ")
            .ifBlank { "Equipamento" }

        val equipmentDetail = buildString {
            append(if (deviceInfoResult is CapabilityReadResult.Success) "Online" else "Status desconhecido")
            deviceInfo?.uptimeSeconds?.let { append(" · ${formatUptime(it)}") }
            deviceInfo?.firmware?.let { append(" · firmware $it") }
        }

        val equipmentDot = when (deviceInfoResult) {
            is CapabilityReadResult.Success -> StatusDotLevel.OK
            is CapabilityReadResult.SessionExpired -> StatusDotLevel.ERROR
            else -> StatusDotLevel.WARNING
        }

        val primaryRadio = wifi?.radios?.firstOrNull { it.enabled != false } ?: wifi?.radios?.firstOrNull()
        val wifiDisplay = when {
            wifiResult is CapabilityReadResult.Success && primaryRadio != null ->
                WifiStatusDisplay(
                    label = listOfNotNull(primaryRadio.ssid, bandLabel(primaryRadio.band))
                        .joinToString(" · ")
                        .ifBlank { bandLabel(primaryRadio.band) },
                    detail = listOfNotNull(
                        primaryRadio.channel?.let { "canal $it" },
                        primaryRadio.security,
                    ).joinToString(" · ").ifBlank { "sem detalhes" },
                    dot = if (primaryRadio.enabled == false) StatusDotLevel.WARNING else StatusDotLevel.OK,
                )
            wifiResult is CapabilityReadResult.Success -> null
            else -> WifiStatusDisplay(
                label = "Wi-Fi",
                detail = readResultReason(wifiResult),
                dot = StatusDotLevel.ERROR,
            )
        }

        return StatusUiState.Loaded(
            equipmentLabel = equipmentLabel,
            equipmentDetail = equipmentDetail,
            equipmentDot = equipmentDot,
            wifi = wifiDisplay,
            publicIp = wan?.ipv4Address,
            // Sem CapabilityId de teste de velocidade hoje — ver KDoc de SpeedSample. Nunca mockado.
            speed = null,
            lastUpdatedAtMillis = nowMillis(),
        )
    }

    private fun readResultReason(result: CapabilityReadResult): String = when (result) {
        is CapabilityReadResult.Unavailable -> result.reason
        is CapabilityReadResult.Failure -> result.reason
        is CapabilityReadResult.SessionExpired -> result.reason
        is CapabilityReadResult.Success -> "" // inatingível nos call sites acima (já filtrado por `is Success`)
    }

    private fun bandLabel(band: WifiBand): String = when (band) {
        WifiBand.GHZ_2_4 -> "2,4 GHz"
        WifiBand.GHZ_5 -> "5 GHz"
        WifiBand.GHZ_6 -> "6 GHz"
        WifiBand.UNKNOWN -> "Wi-Fi"
    }

    private fun formatUptime(totalSeconds: Long): String {
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h"
            else -> "<1h"
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}
