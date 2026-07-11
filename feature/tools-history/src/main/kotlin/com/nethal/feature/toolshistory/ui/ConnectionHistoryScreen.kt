package com.nethal.feature.toolshistory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.core.designsystem.theme.LocalNetHalExtendedColors

/**
 * Tela Histórico de conexão (issue #96, protótipo `4i` em `docs/design/prototypes.dc.html`).
 * Consome [ConnectionHistoryViewModel] (issue #104) — nunca lista item de exemplo hardcoded como
 * se fosse dado real (critério de aceite: estado [ConnectionHistoryUiState.Empty] explícito quando
 * não há [com.nethal.core.scheduling.MeasurementSample] persistida ainda).
 *
 * [onBack] é fornecido por quem monta o grafo (`ConnectionHistoryGraph.kt`), mesmo padrão de
 * `TracerouteScreen`/`DnsLookupScreen`.
 */
@Composable
fun ConnectionHistoryScreen(viewModel: ConnectionHistoryViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("connection_history_screen"),
    ) {
        ConnectionHistoryHeader(onBack = onBack)
        Spacer(modifier = Modifier.height(18.dp))

        when (val state = uiState) {
            ConnectionHistoryUiState.Loading -> LoadingBody()
            ConnectionHistoryUiState.Empty -> EmptyBody()
            is ConnectionHistoryUiState.Content -> ContentBody(items = state.items)
        }
    }
}

@Composable
private fun ConnectionHistoryHeader(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClickLabel = "Voltar", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            BackChevron(tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = "Histórico de conexão",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize().testTag("connection_history_loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBody() {
    Box(modifier = Modifier.fillMaxSize().testTag("connection_history_empty"), contentAlignment = Alignment.Center) {
        Text(
            text = "Nenhum dado de histórico ainda. O NetHAL Lab precisa medir sua conexão por um tempo antes de mostrar o histórico.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun ContentBody(items: List<ConnectionHistoryItemUi>) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().testTag("connection_history_no_incidents"), contentAlignment = Alignment.Center) {
            Text(
                text = "Sua rede esteve estável nos últimos 7 dias. Nenhuma queda ou instabilidade detectada.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("connection_history_list"),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items, key = { "${it.kind}-${it.title}-${it.subtitle}" }) { item ->
                val isLast = items.last() == item
                ConnectionHistoryRow(item = item, showDivider = !isLast)
            }
        }
    }
}

@Composable
private fun ConnectionHistoryRow(item: ConnectionHistoryItemUi, showDivider: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extended = LocalNetHalExtendedColors.current
    val dotColor = when (item.kind) {
        ConnectionHistoryItemUi.Kind.DROP -> extended.error
        ConnectionHistoryItemUi.Kind.RESTORED -> extended.success
        ConnectionHistoryItemUi.Kind.INSTABILITY -> extended.warning
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, fontSize = 13.5.sp, color = colors.onBackground)
            Text(
                text = item.subtitle,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
    if (showDivider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
    }
}

/** Seta de "voltar" (`M15 6l-6 6 6 6`), mesmo desenho de `TracerouteScreen`/`DnsLookupScreen`. */
@Composable
private fun BackChevron(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(15f * scale, 6f * scale)
            lineTo(9f * scale, 12f * scale)
            lineTo(15f * scale, 18f * scale)
        }
        drawPath(path = path, color = tint, style = Stroke(width = 2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
