package com.nethal.feature.pairingauth.passwordhelp

import com.nethal.core.designsystem.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.feature.pairingauth.internal.BackButton

/**
 * Tela `2d` — Onde encontrar a senha (issue #77). Tela nova, sem equivalente anterior em `:app`.
 *
 * Orientação **genérica** (etiqueta física do equipamento) — nunca sugere, lista ou preenche uma
 * senha padrão de verdade (`/seguranca-nethal`: "uso automático de credencial padrão sem
 * confirmação explícita" é proibição absoluta). A lista abaixo só nomeia os *campos* a procurar na
 * etiqueta (usuário, senha, número de série) — nenhum valor concreto de nenhum equipamento real é
 * exibido, diferente do protótipo original (`prototypes.dc.html`, `2d`), que mostrava valores de
 * exemplo (`admin`, `NTH-48291`) que poderiam ser lidos como reais.
 *
 * [onBack] volta ao Login (`2c`) sem limpar nada — os campos já digitados sobrevivem porque
 * [com.nethal.feature.pairingauth.PairingAuthViewModel] é compartilhado por todo o grafo, não
 * recriado por rota (ver seu KDoc).
 */
@Composable
fun PasswordHelpScreen(onBack: () -> Unit) {
    Scaffold(containerColor = BackgroundDark) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackButton(onClick = onBack)
                Text(
                    text = "Onde encontrar a senha",
                    color = OnBackgroundDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 18.dp)
                    .background(color = SurfaceDark, shape = RoundedCornerShape(22.dp))
                    .border(width = 1.dp, color = BorderDark, shape = RoundedCornerShape(22.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LabelSticker()
                Text(
                    text = "A senha padrão de administrador costuma estar impressa na etiqueta " +
                        "colada na parte traseira ou inferior do equipamento.",
                    color = OnSurfaceVariantDark,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = "O QUE PROCURAR NA ETIQUETA",
                color = OnSurfaceTertiaryDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = SurfaceDark, shape = RoundedCornerShape(18.dp))
                    .border(width = 1.dp, color = BorderDark, shape = RoundedCornerShape(18.dp)),
            ) {
                LabelRow(label = "Campo \"Usuário\" / \"User\"", divider = true)
                LabelRow(label = "Campo \"Senha\" / \"Password\" / \"WiFi Key\"", divider = true)
                LabelRow(label = "Número de série (S/N) — ajuda o suporte a identificar o modelo", divider = false)
            }

            Text(
                text = "Os nomes exatos dos campos variam por fabricante — a etiqueta é a fonte " +
                    "confiável, o NetHAL nunca preenche ou sugere uma senha por você.",
                color = OnSurfaceTertiaryDark,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
            )

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = NetHalAccent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Já encontrei, voltar ao login", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LabelSticker() {
    Column(
        modifier = Modifier
            .background(color = BackgroundDark, shape = RoundedCornerShape(14.dp))
            .border(width = 1.5.dp, color = BorderDark, shape = RoundedCornerShape(14.dp))
            .padding(vertical = 18.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .background(color = BorderDark, shape = CircleShape)
                        .padding(4.dp),
                )
            }
        }
        Text(
            text = "ETIQUETA",
            color = NetHalAccent,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun LabelRow(label: String, divider: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(text = label, color = OnSurfaceVariantDark, fontSize = 12.5.sp)
        }
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(BorderDark),
            )
        }
    }
}
