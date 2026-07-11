package com.nethal.feature.pairingdiscovery.internal

import androidx.compose.ui.graphics.Color

/**
 * Tokens literais do design system NetHAL (`docs/design/design-system.dc.html`, skill
 * `/nethal-design`) replicados localmente neste módulo. `:core:designsystem` ainda não expõe
 * esses tokens como `ColorScheme`/objeto público (só tema base, extraído mecanicamente na Fase 1
 * da ADR 0002) — e a PR #116 (fix dos tokens de cor teal/cyan → Electric Blue) está em voo em
 * paralelo, tocando exatamente esse arquivo. Para não competir com ela nem entregar telas fora
 * da paleta oficial, os valores abaixo replicam o `.dc.html` diretamente. Quando #116 mergear e
 * `:core:designsystem` ganhar tokens nomeados equivalentes, promover este objeto para lá e
 * remover a duplicação (decisão registrada, não uma omissão).
 */
internal object PairingTokens {
    val BackgroundPrincipal = Color(0xFF0B0F19)
    val Surface = Color(0xFF161B26)
    val SurfaceElevated = Color(0xFF1D2433)
    val Border = Color(0xFF262F40)
    val Accent = Color(0xFF006FFF)
    val AccentSoftBackground = Color(0x14006FFF) // rgba(0,111,255,0.08~0.14)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFE8ECF5)
    val TextSecondary = Color(0xFF8891A8)
    val TextTertiary = Color(0xFF4C5567)
}
