package com.nethal.core.driver.family.tplink.stokluci

/**
 * Modelos de dados da Driver Family `tplink-stok-luci-driver` (plataforma `tplink-stok-luci`, ver
 * `docs/architecture/hal-layering-model.md` §9.1 e profile `tplink_archer_c6_stok_v1` no
 * catálogo).
 *
 * Entendimento do protocolo vem de pesquisa em código aberto de terceiros — pacote Python
 * `tplinkrouterc6u` (GPL-3.0), citado como referência de existência/forma do protocolo, nunca
 * copiado literalmente. Ver `TpLinkStokLuciCrypto` para a citação completa.
 */

/** Sessão pós-login: token `stok` (usado no path de toda chamada autenticada) e cookie `sysauth`. */
internal data class TpLinkStokLuciSession(
    val stok: String,
    val sysauthCookie: String,
)

/**
 * Par de chave RSA em hex (módulo, expoente), como devolvido pelos endpoints `form=keys`
 * (cifra de senha) e `form=auth` (assinatura de chamadas autenticadas — não usada nesta rodada,
 * só armazenada para documentar/preparar a etapa 6, fora de escopo).
 */
internal data class TpLinkStokLuciRsaKey(
    val modulusHex: String,
    val exponentHex: String,
)

/** Resposta de `form=auth`: sequência (usada para assinar chamadas autenticadas, etapa 6, fora de escopo) e a chave RSA de assinatura. */
internal data class TpLinkStokLuciAuthKeys(
    val seq: Long,
    val signingKey: TpLinkStokLuciRsaKey,
)
