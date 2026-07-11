package com.nethal.core.designsystem.theme

import kotlinx.coroutines.flow.Flow

/**
 * Contrato de persistência do modo de tema escolhido pelo usuário (issue #132).
 *
 * Vive em `core:designsystem` — e não em `core:consent` — porque [ThemeMode] é conceito de design
 * system, não de consentimento/telemetria; `core:consent` é um domínio JVM-puro sobre opt-in de
 * dados e misturar preferência de aparência ali confundiria as fronteiras. Tanto `:app` (composition
 * root) quanto `:feature:settings` (seletor) já dependem de `core:designsystem`, então é o lar
 * natural do contrato. A implementação concreta (DataStore Preferences) fica em `:app`, mesmo padrão
 * de `ConsentDataStoreRepository`/`TelemetryDeviceIdDataStoreRepository` — o core não conhece Android.
 */
interface ThemeModeRepository {
    /** Emite o modo atual e cada mudança. Antes de qualquer escolha, emite [ThemeMode.SYSTEM]. */
    fun observeThemeMode(): Flow<ThemeMode>

    /** Persiste o modo escolhido; a emissão de [observeThemeMode] reflete a troca na hora. */
    suspend fun setThemeMode(mode: ThemeMode)
}
