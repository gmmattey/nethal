package com.nethal.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cores semânticas do NetHAL que **não têm slot no `ColorScheme` do Material 3** e por isso não
 * respondem sozinhas ao tema. O M3 só oferece slot nativo para erro (`error`) — sucesso, aviso e
 * texto terciário ficam de fora. Em vez de espalhar `when (themeMode) { ... }` por cada tela (que
 * exigiria vazar o `ThemeMode` para dentro dos componentes), estas cores são resolvidas uma única
 * vez em `NetHalLabTheme` e distribuídas via este `CompositionLocal`. Cada `*Screen` lê
 * `LocalNetHalExtendedColors.current.success` etc. e passa a responder ao toggle de graça.
 *
 * ### Decisão sobre sucesso/aviso/erro em chip (issue #132)
 * O tema claro usa variantes de status mais fechadas que os neons do dark (contraste sobre branco):
 * sucesso `#0E9B70`, aviso `#B45309`, erro `#DC2626` — ver tabela em `design-system.dc.html`. Em vez
 * de criar componentes de chip que decidem cor por tema, a variante correta já vem embutida aqui:
 * [success]/[warning]/[error] valem o neon no dark e a variante fechada no light. Callers de chip e
 * de texto de status (ex.: `StatusChip`, texto destrutivo "Sair do programa beta") consomem estes
 * campos direto, sem lógica condicional.
 *
 * [error] é a variante de chip/texto de erro (`#EF4444` dark / `#DC2626` light); `colorScheme.error`
 * permanece `#EF4444` nos dois temas porque é o slot que componentes M3 (TextField em estado de erro
 * etc.) consomem — separar os dois evita que um componente M3 herde o vermelho de chip.
 *
 * [onSurfaceTertiary] é o "texto terciário" do design system (`#4C5567` dark / `#9AA3B8` light),
 * também sem slot M3.
 */
@Immutable
data class NetHalExtendedColors(
    val success: Color,
    val warning: Color,
    val error: Color,
    val onSurfaceTertiary: Color,
)

val DarkExtendedColors = NetHalExtendedColors(
    success = SuccessDark,
    warning = WarningDark,
    error = ErrorDark,
    onSurfaceTertiary = OnSurfaceTertiaryDark,
)

val LightExtendedColors = NetHalExtendedColors(
    success = SuccessChipLight,
    warning = WarningChipLight,
    error = ErrorChipLight,
    onSurfaceTertiary = OnSurfaceTertiaryLight,
)

/**
 * Fornecido por `NetHalLabTheme`. O default aponta para o dark só como fallback defensivo — na
 * prática nenhum composable do app roda fora de `NetHalLabTheme`.
 */
val LocalNetHalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
