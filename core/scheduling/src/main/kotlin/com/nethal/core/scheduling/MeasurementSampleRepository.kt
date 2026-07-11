package com.nethal.core.scheduling

/**
 * Persistência das execuções da medição periódica (issue #112) — formato consumível pela camada
 * de agregação/tendência da issue #104 (que decide blocos de tempo, classificação OK/LENTO/OFFLINE
 * etc.; esta interface só guarda a linha bruta de cada rodada, sem opinião sobre o que fazer com
 * elas depois).
 */
interface MeasurementSampleRepository {
    suspend fun insert(sample: MeasurementSample)

    /** Mais recentes primeiro. */
    suspend fun recent(source: MeasurementSourceType, limit: Int): List<MeasurementSample>

    /** Poda de retenção — sem política de retenção automática embutida no scheduler; quem chama decide quando/se rodar. */
    suspend fun deleteOlderThan(source: MeasurementSourceType, beforeEpochMs: Long)
}
