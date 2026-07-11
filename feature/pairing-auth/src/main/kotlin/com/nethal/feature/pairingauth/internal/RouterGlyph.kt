package com.nethal.feature.pairingauth.internal

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
 * Ícone outline de roteador desenhado via `Canvas` (mesmo traçado do protótipo, `2e` —
 * Conectando ao modem). Duplicado a partir de `:feature:pairing-discovery` em vez de
 * compartilhado (ADR 0002: nenhum `:feature:*` depende de outro `:feature:*`).
 */
@Composable
fun RouterGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val iconSize = size
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = 1.8.dp.toPx()
        val w = iconSize.toPx()
        val scale = w / 24f

        fun p(x: Float, y: Float) = Offset(x * scale, y * scale)

        drawRoundRect(
            color = tint,
            topLeft = p(3f, 9f),
            size = Size(18f * scale, 8f * scale),
            cornerRadius = CornerRadius(2f * scale, 2f * scale),
            style = Stroke(width = strokeWidth),
        )
        drawCircle(color = tint, radius = 1f * scale, center = p(7.5f, 13f))
        drawCircle(color = tint, radius = 1f * scale, center = p(11.5f, 13f))
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
