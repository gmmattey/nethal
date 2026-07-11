package com.nethal.feature.status

/**
 * Estado da tela "Status" (issue #83, uso diário — destino [com.nethal.core.navigation.BottomNavDestination.STATUS]).
 *
 * Diferente da extinta `CapabilitiesScreen` (decisão `docs/product/decisions/0001-telas-orfas-redesenho.md`),
 * esta tela nunca lista o vocabulário bruto de `CapabilityId` — só os recortes de dado ao vivo já
 * lidos ([Loaded]), com o motivo (`reason`) honesto quando algo não estiver disponível, nunca um
 * valor inventado.
 */
sealed interface StatusUiState {

    /** Primeira leitura ainda em andamento — nenhum dado chegou até agora nesta instância de tela. */
    data object Loading : StatusUiState

    /**
     * Sem sessão administrativa ativa disponível para esta tela — estado perdido (processo
     * recriado, sessão fechada por [StatusViewModel.onScreenStopped] numa visita anterior e nunca
     * reaberta) ou a sessão nunca chegou a esta tela. Nunca finge ter dado: mostra o motivo e deixa
     * quem compõe o grafo (`:app`, fora deste módulo) decidir como reencaminhar o usuário para
     * reautenticar — este módulo não conhece o fluxo de pareamento (regra de dependência única da
     * ADR 0002).
     */
    data class SessionUnavailable(val reason: String) : StatusUiState

    data class Loaded(
        val equipmentLabel: String,
        val equipmentDetail: String?,
        val equipmentDot: StatusDotLevel,
        val wifi: WifiStatusDisplay?,
        val publicIp: String?,
        val speed: SpeedSample?,
        val lastUpdatedAtMillis: Long,
    ) : StatusUiState
}

/** Cor do indicador de status (design system, tokens de sucesso/aviso/erro — nunca cor decorativa). */
enum class StatusDotLevel {
    OK,
    WARNING,
    ERROR,
}

data class WifiStatusDisplay(
    val label: String,
    val detail: String,
    val dot: StatusDotLevel,
)

/**
 * Amostra de velocidade para o card com sparkline (design system, seção "Componentes" 1h/1i).
 *
 * Nenhum [com.nethal.core.model.CapabilityId] hoje cobre teste de velocidade/throughput — este tipo
 * existe para o componente já nascer pronto para consumir uma capability futura, mas
 * [StatusViewModel] nunca produz uma instância mockada: enquanto não houver capability real, o card
 * mostra o estado "indisponível" (design system, seção 1v), nunca um número inventado.
 */
data class SpeedSample(
    val downloadMbps: Double,
    val history: List<Float>,
)
