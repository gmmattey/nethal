package com.nethal.lab.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nethal.core.model.NetworkTarget

/**
 * Espaço reservado para a Tela 3 (Equipamento detectado), fora do escopo desta entrega — o
 * Fingerprint Engine ainda não existe (Feat 3). Existe só para o fluxo de descoberta ter um
 * destino final após escolher um `NetworkTarget`, e permitir acesso às Configurações.
 */
@Composable
fun TargetSelectedPlaceholderScreen(target: NetworkTarget, onOpenSettings: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Equipamento escolhido: ${target.ip}",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = "Identificação de fabricante/modelo ainda não implementada nesta versão.",
                style = MaterialTheme.typography.bodyMedium,
            )

            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Configurações")
            }
        }
    }
}
