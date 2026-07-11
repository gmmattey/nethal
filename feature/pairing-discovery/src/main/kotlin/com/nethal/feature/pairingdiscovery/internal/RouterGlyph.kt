package com.nethal.feature.pairingdiscovery.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícone outline de roteador desenhado via `Canvas` (mesmo traçado do protótipo,
 * `prototypes.dc.html`: retângulo + duas antenas + dois LEDs) — sem depender de
 * `material-icons-extended` (não há essa dependência no projeto; `Icons.Outlined.Router` não
 * existe no conjunto core do Compose Material). Reaproveitado em 2a (radar), 2b (card
 * encontrado) e 2g/2i (linha de tipo/modelo Roteador).
 */
@Composable
fun RouterGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    // Parâmetro renomeado para `iconSize` dentro do escopo de `Canvas` para não colidir com
    // `DrawScope.size` (Size, em px) — `size` teria sido resolvido para o receiver implícito do
    // draw scope, não para este parâmetro `Dp`, quebrando `iconSize.toPx()`.
    val iconSize = size
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = 1.8.dp.toPx()
        val w = iconSize.toPx()
        val scale = w / 24f

        fun p(x: Float, y: Float) = Offset(x * scale, y * scale)

        // Corpo do roteador (retângulo com cantos arredondados).
        drawRoundRect(
            color = tint,
            topLeft = p(3f, 9f),
            size = Size(18f * scale, 8f * scale),
            cornerRadius = CornerRadius(2f * scale, 2f * scale),
            style = Stroke(width = strokeWidth),
        )
        // LEDs.
        drawCircle(color = tint, radius = 1f * scale, center = p(7.5f, 13f))
        drawCircle(color = tint, radius = 1f * scale, center = p(11.5f, 13f))
        // Antenas.
        drawLine(
            color = tint,
            start = p(8f, 9f),
            end = p(8f, 6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = p(16f, 9f),
            end = p(16f, 6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
