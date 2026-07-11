package com.nethal.feature.settings

import com.nethal.core.consent.ConsentRecord
import com.nethal.core.consent.ConsentRepository
import com.nethal.core.consent.ConsentScope
import com.nethal.core.consent.ConsentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cópia local do fake usado em `app/src/test/kotlin/com/nethal/lab/FakeConsentRepository.kt` —
 * módulos de feature nunca dependem de sources de teste de outro módulo, cada um mantém o seu
 * (mesmo raciocínio da regra de dependência única da ADR 0002, aplicada a testes).
 */
class FakeConsentRepository : ConsentRepository {
    private val records = mutableMapOf<ConsentScope, ConsentRecord>()
    private val state = MutableStateFlow(ConsentState.empty())

    override fun observeState(): Flow<ConsentState> = state

    override suspend fun grant(scope: ConsentScope, grantedAtEpochMillis: Long) {
        records[scope] = ConsentRecord(scope, granted = true, grantedAtEpochMillis = grantedAtEpochMillis)
        state.value = ConsentState(records.toMap())
    }

    override suspend fun revoke(scope: ConsentScope) {
        records[scope] = ConsentRecord(scope, granted = false, grantedAtEpochMillis = null)
        state.value = ConsentState(records.toMap())
    }

    fun isGranted(scope: ConsentScope): Boolean = records[scope]?.granted == true
}
