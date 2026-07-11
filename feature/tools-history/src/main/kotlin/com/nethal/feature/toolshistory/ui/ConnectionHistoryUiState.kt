package com.nethal.feature.toolshistory.ui

/**
 * Estado da tela Histórico de conexão (issue #96, protótipo `4i`).
 *
 * [Empty] é o estado exigido pelo critério de aceite da #96 ("se não houver dado real disponível,
 * usa estado vazio explícito — nunca histórico mockado/fictício"): nenhum
 * [com.nethal.core.scheduling.MeasurementSample] persistido ainda, então não há série pra agregar
 * nem evento pra derivar. [Content.items] vazio é um caso **diferente** — há dado real (blocos
 * medidos), só não houve nenhuma transição de qualidade (rede estável o período todo).
 */
sealed interface ConnectionHistoryUiState {

    data object Loading : ConnectionHistoryUiState

    /** Nenhuma amostra de medição encontrada no período — nada para agregar. */
    data object Empty : ConnectionHistoryUiState

    /** [items] pode ser vazia (rede estável, nenhuma queda/instabilidade no período) — ver KDoc do sealed interface. */
    data class Content(val items: List<ConnectionHistoryItemUi>) : ConnectionHistoryUiState
}

/**
 * Item de lista já formatado para exibição — a tradução de
 * [com.nethal.core.history.ConnectionHistoryEvent] para o visual do protótipo `4i` (ponto colorido
 * + título + "Hoje, 14:32 · 3 min") acontece no [ConnectionHistoryViewModel][com.nethal.feature.toolshistory.ui.ConnectionHistoryViewModel],
 * não na camada de domínio — formatação de data/hora relativa é decisão de apresentação.
 */
data class ConnectionHistoryItemUi(
    val kind: Kind,
    val title: String,
    val subtitle: String,
) {
    enum class Kind { DROP, RESTORED, INSTABILITY }
}
