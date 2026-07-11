package com.nethal.feature.onboarding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Glyphs desenhados diretamente com [Canvas] (sem `ImageVector`/recurso `.xml` nem biblioteca de
 * ícones nova) porque o app ainda não tem nenhuma dependência de ícones e o set oficial do NetHAL
 * (`docs/design/assets/icons/`) só existe como SVG solto, sem pipeline de importação para Compose.
 * Aproximação geométrica fiel à composição do protótipo (pin / roteador / check / chevron), não
 * uma cópia pixel-a-pixel do path SVG — se a Vera precisar de fidelidade vetorial exata, o caminho
 * correto é gerar um `ImageVector`/drawable a partir do SVG oficial, não hardcode aqui.
 */

/** Pin de localização (tela 1b) — cabeça circular + ponta triangular, replicando o glifo Material "place". */
@Composable
internal fun LocationPinGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.Accent) {
    Canvas(modifier = modifier) {
        val headRadius = size.width * 0.30f
        val headCenter = Offset(size.width / 2f, size.height * 0.34f)
        val tip = Offset(size.width / 2f, size.height * 0.98f)
        val tangentSpread = headRadius * 0.92f

        val path = Path().apply {
            moveTo(headCenter.x - tangentSpread, headCenter.y + headRadius * 0.55f)
            lineTo(tip.x, tip.y)
            lineTo(headCenter.x + tangentSpread, headCenter.y + headRadius * 0.55f)
            arcTo(
                rect = Rect(
                    headCenter.x - headRadius,
                    headCenter.y - headRadius,
                    headCenter.x + headRadius,
                    headCenter.y + headRadius,
                ),
                startAngleDegrees = 55f,
                sweepAngleDegrees = 250f,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(path, color = tint)
        drawCircle(color = OnboardingColors.Background, radius = headRadius * 0.42f, center = headCenter)
    }
}

/** Roteador (tela 1c) — replica `docs/design/assets/icons/{dark,light}/router.svg` com formas primitivas. */
@Composable
internal fun RouterGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.Accent) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        val bodyTop = size.height * 0.4f
        val bodyHeight = size.height * 0.32f
        val bodyLeft = size.width * 0.14f
        val bodyWidth = size.width * 0.72f

        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(size.width * 0.09f),
            style = Stroke(width = strokeWidth),
        )

        val dotRadius = size.minDimension * 0.045f
        val dotY = bodyTop + bodyHeight / 2f
        drawCircle(tint, dotRadius, Offset(bodyLeft + bodyWidth * 0.24f, dotY))
        drawCircle(tint, dotRadius, Offset(bodyLeft + bodyWidth * 0.48f, dotY))

        drawLine(
            color = tint,
            start = Offset(bodyLeft + bodyWidth * 0.30f, bodyTop),
            end = Offset(bodyLeft + bodyWidth * 0.30f, bodyTop - size.height * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(bodyLeft + bodyWidth * 0.70f, bodyTop),
            end = Offset(bodyLeft + bodyWidth * 0.70f, bodyTop - size.height * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** Check de confirmação (tela 1e) — item concedido. */
@Composable
internal fun CheckGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.Success) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        val path = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.55f)
            lineTo(size.width * 0.42f, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height * 0.26f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Traço neutro (tela 1e) — item não concedido. Nunca vermelho/erro: negar permissão não é erro. */
@Composable
internal fun DashGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.TextTertiary) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.22f, size.height / 2f),
            end = Offset(size.width * 0.78f, size.height / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** Chevron de voltar (tela 1f). */
@Composable
internal fun ChevronLeftGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.TextPrimary) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.2f)
            lineTo(size.width * 0.32f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Marca NetHAL (tela 1a) — quadrado arredondado fill accent com quadrado interno menor na cor de
 * fundo, replicando o hero do protótipo (`docs/design/prototypes.dc.html` `1a`: quadrado 76dp
 * raio 22 fill `#006FFF` + quadrado interno 26dp raio 7 `#0B0F19`) e coerente com a marca oficial
 * documentada na skill `/nethal-design` §Marca. Composição com [Box] (não [Canvas]) porque são só
 * dois retângulos arredondados concêntricos, sem necessidade de path vetorial.
 */
@Composable
internal fun NetHalMarkGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.Accent) {
    Box(
        modifier = modifier.background(tint, RoundedCornerShape(percent = 29)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .background(OnboardingColors.Background, RoundedCornerShape(percent = 27)),
        )
    }
}

/**
 * Sino de notificação (tela 1d) — corpo em domo + badana + batente, replicando
 * `docs/design/prototypes.dc.html` `1d` (path `M6 16V10a6 6 0 1112 0v6l2 3H4l2-3z` +
 * `M9.5 20a2.5 2.5 0 005 0`) com formas primitivas, mesma filosofia dos demais glyphs deste
 * arquivo.
 */
@Composable
internal fun BellGlyph(modifier: Modifier = Modifier, tint: Color = OnboardingColors.Accent) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val domeRadius = size.width * 0.32f
        val domeCenterY = size.height * 0.40f
        val bodyLeft = size.width * 0.5f - domeRadius
        val bodyRight = size.width * 0.5f + domeRadius
        val bodyBottom = size.height * 0.72f

        val bodyPath = Path().apply {
            moveTo(bodyLeft, bodyBottom)
            arcTo(
                rect = Rect(bodyLeft, domeCenterY - domeRadius, bodyRight, domeCenterY + domeRadius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(bodyRight, bodyBottom)
            lineTo(size.width * 0.86f, size.height * 0.84f)
            lineTo(size.width * 0.14f, size.height * 0.84f)
            close()
        }
        drawPath(
            path = bodyPath,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val clapperSize = size.width * 0.22f
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.5f - clapperSize / 2f, size.height * 0.86f),
            size = Size(clapperSize, clapperSize),
            style = Stroke(width = strokeWidth * 0.85f, cap = StrokeCap.Round),
        )
    }
}
