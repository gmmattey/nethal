package com.nethal.feature.wifinetwork.unavailable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * TODO(#120): substituir por `com.nethal.feature.toolscommon.UnavailableResourceState` quando a PR
 * #120 (`:feature:tools-common`) mergear em `main` — assinatura já espelhada de propósito
 * (`label`/`reason`/`icon`/`dialogTitle`/`resolutionLabel`/`onResolutionClick`) para a troca ser um
 * import a menos, sem mudar nenhum call site desta tela.
 *
 * Fallback local só para a issue #84 não ficar bloqueada por uma PR de outro agente ainda não
 * mergeada. Mesmas regras do design system seção 1v (protótipo `4g`): nunca esconde a ação nem a
 * deixa muda ao toque — o item continua tocável e o toque sempre abre a explicação do motivo.
 */
@Composable
fun UnavailableResourceState(
    label: String,
    reason: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {},
    dialogTitle: String = "Recurso indisponível",
    resolutionLabel: String? = null,
    onResolutionClick: (() -> Unit)? = null,
) {
    var dialogVisible by remember { mutableStateOf(false) }

    UnavailableResourceListItem(
        label = label,
        onClick = { dialogVisible = true },
        modifier = modifier,
        icon = icon,
    )

    if (dialogVisible) {
        UnavailableFeatureDialog(
            reason = reason,
            onDismissRequest = { dialogVisible = false },
            title = dialogTitle,
            resolutionLabel = resolutionLabel,
            onResolutionClick = onResolutionClick,
        )
    }
}

/** Opacidade 38-45% do design system para elemento indisponível ainda tocável (seção 1v). */
private const val UNAVAILABLE_ALPHA = 0.4f

@Composable
fun UnavailableResourceListItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "$label, recurso indisponível", onClick = onClick)
            .alpha(UNAVAILABLE_ALPHA)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(19.dp)) { icon() }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        ChevronRight(tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
    }
}

@Composable
fun UnavailableFeatureDialog(
    reason: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recurso indisponível",
    resolutionLabel: String? = null,
    onResolutionClick: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        Column(
            modifier = modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (resolutionLabel != null && onResolutionClick != null) {
                    TextButton(onClick = {
                        onResolutionClick()
                        onDismissRequest()
                    }) {
                        Text(resolutionLabel)
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Entendi")
                }
            }
        }
    }
}

/**
 * Chevron outline consistente com `docs/design/assets/icons/dark/chevron-right.svg` e sua variante
 * `light/` (`M9 6l6 6-6 6`, stroke 2, viewBox 24x24), desenhado via [Canvas] em vez de trazer uma
 * dependência de ícones só para este componente — mesma solução já usada na versão de
 * `:feature:tools-common` (PR #120).
 */
@Composable
private fun ChevronRight(tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val path = Path().apply {
            moveTo(9f * scale, 6f * scale)
            lineTo(15f * scale, 12f * scale)
            lineTo(9f * scale, 18f * scale)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
