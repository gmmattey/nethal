package com.nethal.feature.pairingdiscovery.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Botão circular de voltar do protótipo (32x32dp, fundo surface, borda, chevron esquerdo
 * desenhado via `Canvas` — mesmo path SVG `M15 6l-6 6 6 6` do `prototypes.dc.html`). Sem
 * dependência de `material-icons-core` (não declarada no projeto), consistente com
 * [RouterGlyph].
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(32.dp)
            .background(color = PairingTokens.Surface, shape = CircleShape)
            .border(width = 1.dp, color = PairingTokens.Border, shape = CircleShape)
            .clickable(onClick = onClick),
    ) {
        val strokeWidth = 2.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.minDimension * 0.16f
        val path = Path().apply {
            moveTo(cx + half, cy - half * 1.4f)
            lineTo(cx - half, cy)
            lineTo(cx + half, cy + half * 1.4f)
        }
        drawPath(
            path = path,
            color = PairingTokens.TextPrimary,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
