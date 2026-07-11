package com.nethal.feature.pairingdiscovery.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.feature.pairingdiscovery.internal.PairingTokens

/**
 * Tela 2b (falha) — descoberta automática não encontrou nenhum candidato: sem gateway
 * identificável (AP isolation, VPN ativa, rede sem gateway) ou permissão de localização negada.
 *
 * Decisão registrada na spec de #80 ("entrada manual por IP"): esta tela deixa de oferecer só um
 * campo de texto cru e passa a oferecer dois caminhos — "Selecionar equipamento" (entra no
 * cluster guiado tipo→fabricante→modelo por 2g) como caminho principal, e "Informar IP
 * manualmente" (vai direto para o mesmo campo de IP usado a partir de "Outro/não sei" em 2h) como
 * fallback avançado. O campo de IP nunca desaparece — só deixa de ser a única opção.
 */
@Composable
fun DiscoveryFailedScreen(
    reason: FailureReason,
    onRetry: () -> Unit,
    onSelectManually: () -> Unit,
    onEnterIpManually: () -> Unit,
) {
    Scaffold(containerColor = PairingTokens.BackgroundPrincipal) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PairingTokens.BackgroundPrincipal)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Não foi possível encontrar sua rede",
                color = PairingTokens.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = probableReasonText(reason),
                color = PairingTokens.TextSecondary,
                fontSize = 13.sp,
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PairingTokens.Accent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Tentar novamente", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Text(
                text = "Ou continue sem descoberta automática:",
                color = PairingTokens.TextTertiary,
                fontSize = 11.5.sp,
            )

            Button(
                onClick = onSelectManually,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PairingTokens.Surface,
                    contentColor = PairingTokens.TextPrimary,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Selecionar equipamento", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onEnterIpManually,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PairingTokens.TextSecondary),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Informar IP manualmente", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

private fun probableReasonText(reason: FailureReason): String = when (reason) {
    FailureReason.NO_GATEWAY_FOUND ->
        "Não conseguimos identificar um gateway válido na sua rede. Isso pode acontecer " +
            "quando o roteador isola os dispositivos (AP isolation), há uma VPN ativa, ou a " +
            "rede não tem um gateway padrão identificável."

    FailureReason.NOT_ON_WIFI ->
        "Seu aparelho não parece estar conectado a uma rede Wi-Fi. A descoberta de rede " +
            "local só funciona em Wi-Fi."

    FailureReason.LOCATION_PERMISSION_DENIED ->
        "Sem a permissão de localização, o Android não libera os dados de Wi-Fi " +
            "necessários para identificar o gateway."
}
