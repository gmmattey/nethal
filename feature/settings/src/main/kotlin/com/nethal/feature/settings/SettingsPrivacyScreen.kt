package com.nethal.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Conteúdo de privacidade absorvido de `PrivacyScreen` (descontinuada, decisão de produto #66 —
 * `docs/product/decisions/0001-telas-orfas-redesenho.md`). Antes era uma tela própria de
 * onboarding acionada por "Ver privacidade" na Tela de Boas-vindas; agora é um item dentro de
 * Configurações (linha "Política de privacidade", seção SOBRE), consultável a qualquer momento —
 * texto preservado integralmente, só o ponto de entrada mudou.
 */
@Composable
fun SettingsPrivacyScreen(onBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("settings_privacy_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Privacidade", style = MaterialTheme.typography.headlineSmall)

            Text(
                text = "O NetHAL nunca armazena senhas — nem a senha do Wi-Fi (usada para " +
                    "conectar seu celular à rede) nem a senha de administrador do roteador " +
                    "ou modem (usada para acessar o painel de configuração do equipamento). " +
                    "São credenciais diferentes, e nenhuma das duas é persistida: existem " +
                    "apenas na sessão local do app e são descartadas ao fechar o módulo.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = "A permissão de localização é usada somente para ler informações " +
                    "de Wi-Fi (SSID/BSSID), exigida pelo Android — não para rastrear sua " +
                    "localização.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = "Se você optar por participar do programa de testers beta, os " +
                    "relatórios enviados são anônimos e sanitizados: nunca incluem senha, " +
                    "SSID em claro, MAC completo ou IP público completo.",
                style = MaterialTheme.typography.bodyLarge,
            )

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar")
            }
        }
    }
}
