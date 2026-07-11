package com.nethal.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Tokens sincronizados com docs/design/design-system.dc.html (fonte da verdade da marca NetHAL).
// Sistema "dark cyber utilitário" — accent Electric Blue. Teal/cyan (NetHalTeal/NetHalCyan) era
// resquício de uma exploração de marca anterior, descontinuada e arquivada em
// docs/design/_archive/2026-07-11-design-v1/ — nunca chegou a virar decisão de produto.
val NetHalAccent = Color(0xFF006FFF)

// ---------------------------------------------------------------------------------------------
// Tema escuro (dark) — padrão histórico do NetHAL Lab.
// ---------------------------------------------------------------------------------------------
val BackgroundDark = Color(0xFF0B0F19)
val SurfaceDark = Color(0xFF161B26)
val SurfaceVariantDark = Color(0xFF1D2433)
val OnBackgroundDark = Color(0xFFE8ECF5)
val OnSurfaceVariantDark = Color(0xFF8891A8)
val OnSurfaceTertiaryDark = Color(0xFF4C5567)
val ErrorDark = Color(0xFFEF4444)
val BorderDark = Color(0xFF262F40)
val SuccessDark = Color(0xFF10B981)
val WarningDark = Color(0xFFF59E0B)
val AccentSoftBackgroundDark = Color(0x14006FFF) // rgba(0,111,255,0.08~0.14)

// ---------------------------------------------------------------------------------------------
// Tema claro (light) — tokens `lightColorTokens` de docs/design/design-system.dc.html.
// ---------------------------------------------------------------------------------------------
val BackgroundLight = Color(0xFFF4F6FB)
val SurfaceLight = Color(0xFFFFFFFF)
// Surface-2 (elevação) não tem valor de light no design system ("—" na tabela). Escolha deste
// PR: cinza-azulado sutil entre BackgroundLight e BorderLight, usado só como divisor/inset —
// mantém a hierarquia de superfície do dark (SurfaceVariant um passo acima de Surface).
val SurfaceVariantLight = Color(0xFFE9EDF5)
val OnBackgroundLight = Color(0xFF10192B)
val OnSurfaceVariantLight = Color(0xFF5B6478)
val OnSurfaceTertiaryLight = Color(0xFF9AA3B8)
val BorderLight = Color(0xFFDCE2ED)
// `colorScheme.error` segue o vermelho neon (#EF4444) nos DOIS temas — é o slot que os componentes
// M3 consomem. A variante fechada de erro para chip/texto sobre fundo claro (#DC2626, contraste
// melhor no branco) fica em ErrorChipLight e é exposta via LocalNetHalExtendedColors.error.
val ErrorLight = Color(0xFFEF4444)
val AccentSoftBackgroundLight = Color(0x14006FFF)

// Variantes de chip/texto de status para o tema claro (tabela do design system): os valores neon
// do dark (#10B981 / #F59E0B / #EF4444) não têm contraste suficiente sobre superfícies claras, então
// no light usam-se estes tons mais fechados. Em dark, o "chip" usa o próprio neon (SuccessDark /
// WarningDark / ErrorDark) — não há token de chip separado no dark. Ver LocalNetHalExtendedColors.
val SuccessChipLight = Color(0xFF0E9B70)
val WarningChipLight = Color(0xFFB45309)
val ErrorChipLight = Color(0xFFDC2626)
