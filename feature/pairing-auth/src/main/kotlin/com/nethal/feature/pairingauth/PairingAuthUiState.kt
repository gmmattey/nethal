package com.nethal.feature.pairingauth

/**
 * Estado do cluster de pareamento — Login (protótipo `2c`), Conectando (`2e`) e Falha (`2f`),
 * issues #76/#78/#79. Sucessor do antigo `AuthenticationUiState` (extraído de `:app` para este
 * módulo, ADR 0002) — vocabulário preservado, só o nome do tipo muda para refletir o módulo novo.
 */
sealed interface PairingAuthUiState {

    /** Resolvendo o `CompatibilityProfile`/Driver Family a partir do `matchedProfileId` da Tela 3. Síncrono (sem I/O de rede) — na prática a UI quase nunca observa este estado por mais de um frame. */
    data object ResolvingDriver : PairingAuthUiState

    /**
     * Não é possível autenticar contra este equipamento nesta versão do app — nunca uma exceção
     * não tratada. Cobre: nenhuma identificação da Tela 3 (`matchedProfileId == null`), profile
     * não encontrado no catálogo local (drift), nenhuma Driver Family registrada para
     * `driverFamilyId` do profile (`UnknownDriverFamilyException`) e host recusado pela guarda de
     * IP privado (RFC 1918) de toda `DriverFamily`. Renderizado pela tela de Falha (`2f`, issue
     * #79) — o Login (`2c`) nunca chega a aparecer para o usuário neste caso (ver KDoc do grafo).
     */
    data class DriverUnavailable(val reason: String) : PairingAuthUiState

    data class Ready(
        val vendor: String,
        val model: String,
        /**
         * `true` só para o profile `tplink_archer_c6_stok_v1` (`driverFamilyId` =
         * `"tplink-stok-luci-driver"`) — pendência de gate registrada em
         * `docs/drivers/compatibility-catalog.md`, seção "Limitação conhecida — TOFU no handshake
         * stok/luci do TP-Link Archer C6". Exibido na tela de Login (`2c`) com cor de
         * erro/aviso, preservado 1:1 a partir da implementação anterior (`/seguranca-nethal`).
         */
        val showTofuWarning: Boolean,
        val credentialTestState: CredentialTestState,
    ) : PairingAuthUiState
}

/**
 * Resultado do botão "Entrar no modem" (`CapabilityEngine.testCredentials()`). [Failure] cobre
 * também o caso honesto de Driver Family sem sessão real implementada — ver KDoc de
 * [PairingAuthViewModel.submit].
 */
sealed interface CredentialTestState {
    data object Idle : CredentialTestState
    data object Testing : CredentialTestState
    data object Success : CredentialTestState
    data class InvalidCredentials(val reason: String) : CredentialTestState
    data class Failure(val reason: String) : CredentialTestState
}
