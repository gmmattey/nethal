package com.nethal.core.history

/**
 * Item de feed derivado de uma sequência de [ConnectionHistoryBlock] — a ponte entre o modelo de
 * série agregada da issue #104 e o visual de lista de eventos do protótipo `4i`
 * (`docs/design/prototypes.dc.html`, tela "Ferramentas — Histórico de conexão": ponto colorido +
 * título + "Hoje, 14:32 · 3 min").
 *
 * O protótipo foi desenhado como trilha de eventos discretos, mas a decisão de escopo do Rafael
 * (2026-07-11) fixou o backend como série agregada — não uma trilha bruta. [deriveConnectionHistoryEvents]
 * reconcilia os dois: cada item aqui é uma transição real de estado entre blocos (nunca um evento
 * inventado), o suficiente para alimentar o mesmo layout de lista sem introduzir um modelo de dados
 * paralelo de "log de eventos".
 */
sealed interface ConnectionHistoryEvent {
    val atEpochMs: Long

    /** Início de uma sequência de blocos [ConnectionQuality.OFFLINE]. */
    data class Drop(
        override val atEpochMs: Long,
        /** `null` enquanto a queda ainda está em curso no bloco mais recente (sem [Restored] correspondente ainda). */
        val durationMinutes: Int?,
    ) : ConnectionHistoryEvent

    /** Fim de uma sequência de blocos [ConnectionQuality.OFFLINE] — sempre emparelhado com um [Drop] anterior. */
    data class Restored(override val atEpochMs: Long) : ConnectionHistoryEvent

    /**
     * Uma sequência de blocos [ConnectionQuality.LENTO] já concluída (voltou a [ConnectionQuality.OK]
     * ou caiu para [ConnectionQuality.OFFLINE]). Diferente de [Drop]/[Restored], só é emitido quando
     * resolvido — instabilidade em curso não gera item ainda (ver KDoc de [deriveConnectionHistoryEvents]).
     */
    data class Instability(
        override val atEpochMs: Long,
        val durationMinutes: Int,
    ) : ConnectionHistoryEvent
}

/**
 * Deriva [ConnectionHistoryEvent] a partir de transições de [ConnectionQuality] entre blocos
 * consecutivos — pura função sobre [blocks], sem I/O.
 *
 * ## Regras
 * - Blocos [ConnectionQuality.SEM_DADO] são ignorados antes de detectar transições: uma janela sem
 *   nenhuma amostra (app fechado, sem Wi-Fi elegível para o worker, etc.) não é, por si só, um sinal
 *   de queda — o estado anterior conhecido é o que importa. Efeito colateral aceito e documentado:
 *   duas janelas OFFLINE separadas por um buraco de SEM_DADO viram uma única sequência (o melhor
 *   sinal disponível, dado que não há amostra no meio para provar recuperação).
 * - Sequência [ConnectionQuality.OFFLINE] vira dois itens: [ConnectionHistoryEvent.Drop] no início
 *   (sempre emitido, mesmo em curso — perder visibilidade de uma queda ativa é pior que a duração
 *   aparecer como "-") e [ConnectionHistoryEvent.Restored] no fim (só quando resolvida).
 * - Sequência [ConnectionQuality.LENTO] vira um único [ConnectionHistoryEvent.Instability], e só
 *   quando resolvida — ao contrário de queda, uma instabilidade em curso e sem duração conhecida
 *   não tem o mesmo valor de alerta imediato, e o protótipo `4i` não tem um segundo estado visual
 *   equivalente a "Restaurado" para instabilidade.
 * - Ordenado do mais recente para o mais antigo (mesma ordem do protótipo `4i`).
 */
fun deriveConnectionHistoryEvents(
    blocks: List<ConnectionHistoryBlock>,
    windowMinutes: Long = ConnectionHistoryAggregator.DEFAULT_WINDOW_MINUTES,
): List<ConnectionHistoryEvent> {
    val measured = blocks.filter { it.quality != ConnectionQuality.SEM_DADO }.sortedBy { it.startEpochMs }
    val runs = buildQualityRuns(measured, windowMinutes)

    val events = mutableListOf<ConnectionHistoryEvent>()
    for (run in runs) {
        when (run.quality) {
            ConnectionQuality.OFFLINE -> {
                val durationMinutes = if (run.ongoing) null else run.durationMinutes()
                events += ConnectionHistoryEvent.Drop(atEpochMs = run.startEpochMs, durationMinutes = durationMinutes)
                if (!run.ongoing) {
                    events += ConnectionHistoryEvent.Restored(atEpochMs = run.endEpochMs)
                }
            }
            ConnectionQuality.LENTO -> {
                if (!run.ongoing) {
                    events += ConnectionHistoryEvent.Instability(atEpochMs = run.startEpochMs, durationMinutes = run.durationMinutes())
                }
            }
            ConnectionQuality.OK, ConnectionQuality.SEM_DADO -> Unit
        }
    }
    return events.sortedByDescending { it.atEpochMs }
}

private data class QualityRun(
    val quality: ConnectionQuality,
    val startEpochMs: Long,
    val endEpochMs: Long,
    /** `true` quando este é o run do bloco mais recente — o estado pode continuar mudando depois. */
    val ongoing: Boolean,
) {
    fun durationMinutes(): Int = ((endEpochMs - startEpochMs) / 60_000L).toInt()
}

/** Agrupa blocos (já ordenados, sem [ConnectionQuality.SEM_DADO]) em sequências contíguas de mesma [ConnectionQuality]. */
private fun buildQualityRuns(blocks: List<ConnectionHistoryBlock>, windowMinutes: Long): List<QualityRun> {
    if (blocks.isEmpty()) return emptyList()
    val windowMs = windowMinutes * 60_000L

    val runs = mutableListOf<QualityRun>()
    var runQuality = blocks.first().quality
    var runStart = blocks.first().startEpochMs
    var lastBlockStart = blocks.first().startEpochMs

    for (index in 1 until blocks.size) {
        val block = blocks[index]
        if (block.quality == runQuality) {
            lastBlockStart = block.startEpochMs
        } else {
            runs += QualityRun(runQuality, runStart, lastBlockStart + windowMs, ongoing = false)
            runQuality = block.quality
            runStart = block.startEpochMs
            lastBlockStart = block.startEpochMs
        }
    }
    // Último run: "ongoing" porque é o bloco mais recente observado, o estado ainda pode mudar.
    runs += QualityRun(runQuality, runStart, lastBlockStart + windowMs, ongoing = true)
    return runs
}
