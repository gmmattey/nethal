package com.nethal.feature.pairingauth.connecting

import com.nethal.core.designsystem.theme.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.feature.pairingauth.internal.RouterGlyph

/**
 * Tela `2e` — Conectando ao modem (issue #78). Acionada só quando
 * `PairingAuthUiState.Ready.credentialTestState` é `CredentialTestState.Testing` (o grafo,
 * `PairingAuthGraph.kt`, decide isso, não este Composable — puramente visual, sem lógica de
 * teste de credenciais).
 *
 * `deviceLabel` é opcional (fabricante + modelo já identificados nas telas anteriores) — quando
 * ausente, cai num texto genérico honesto em vez de inventar um identificador que o protótipo
 * mostrava só como exemplo (`NTH-48291`).
 */
@Composable
fun ConnectingScreen(deviceLabel: String?) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ConnectingRing()

                Text(
                    text = "Conectando ao modem",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Isso pode levar alguns segundos. Não feche o aplicativo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChecklistDone(text = "Rede local encontrada")
                    ChecklistDone(text = deviceLabel?.let { "$it identificado" } ?: "Equipamento identificado")
                    ChecklistPending(text = "Validando credenciais…")
                }
            }
        }
    }
}

@Composable
private fun ConnectingRing() {
    val transition = rememberInfiniteTransition(label = "connecting-ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "connecting-ring-angle",
    )

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(132.dp).rotate(angle),
            color = NetHalAccent,
            trackColor = MaterialTheme.colorScheme.outline,
            strokeWidth = 4.dp,
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            RouterGlyph(tint = NetHalAccent, size = 26.dp)
        }
    }
}

@Composable
private fun ChecklistDone(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color = NetHalAccent, shape = CircleShape),
        )
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.5.sp)
    }
}

@Composable
private fun ChecklistPending(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = NetHalAccent,
            trackColor = MaterialTheme.colorScheme.outline,
            strokeWidth = 2.dp,
        )
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
    }
}
