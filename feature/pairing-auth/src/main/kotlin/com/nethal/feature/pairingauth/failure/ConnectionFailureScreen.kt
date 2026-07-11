package com.nethal.feature.pairingauth.failure

import com.nethal.core.designsystem.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * As 3 causas honestas de falha cobertas pela tela `2f` (issue #79) — nunca uma quarta categoria
 * genérica "algo deu errado". Espelha o vocabulário de `PairingAuthUiState`/`CredentialTestState`
 * (`DriverUnavailable`, `InvalidCredentials`, `Failure`); mapeado 1:1 para o mesmo vocabulário de
 * resultado de autenticação da telemetria (`success`/`invalid_credentials`/`failure`, issue #97 —
 * ver comentário do Rafael na issue #79) para não haver dois vocabulários de erro divergentes.
 */
enum class FailureKind {
    DRIVER_UNAVAILABLE,
    INVALID_CREDENTIALS,
    CONNECTION_FAILURE,
}

/**
 * Tela `2f` — Falha na conexão (issue #79). Consolida os 3 casos de erro antes espalhados entre
 * `DriverUnavailableContent` e `CredentialTestFeedback` inline (`:app`, `AuthenticationScreen`
 * antigo) — [reason] é sempre a mensagem específica devolvida pelo `DriverFamily`/`CapabilityEngine`
 * ([PairingAuthUiState.DriverUnavailable.reason] ou
 * [com.nethal.feature.pairingauth.CredentialTestState.InvalidCredentials]/[Failure].reason),
 * nunca um texto genérico "algo deu errado".
 */
@Composable
fun ConnectionFailureScreen(
    kind: FailureKind,
    reason: String,
    onRetry: () -> Unit,
    onFindPassword: () -> Unit,
) {
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
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            color = LocalNetHalExtendedColors.current.error.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorGlyph()
                }
                Text(
                    text = titleFor(kind),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
                )
                Text(
                    text = leadInFor(kind),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = reason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = NetHalAccent),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Tentar novamente", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = onFindPassword,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Onde encontro a senha?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun titleFor(kind: FailureKind): String = when (kind) {
    FailureKind.DRIVER_UNAVAILABLE -> "Não é possível autenticar"
    FailureKind.INVALID_CREDENTIALS -> "Usuário ou senha incorretos"
    FailureKind.CONNECTION_FAILURE -> "Não foi possível conectar"
}

private fun leadInFor(kind: FailureKind): String = when (kind) {
    FailureKind.DRIVER_UNAVAILABLE ->
        "Este equipamento ainda não tem suporte completo para autenticação neste app."
    FailureKind.INVALID_CREDENTIALS ->
        "O modem recusou as credenciais informadas. Verifique o usuário e a senha e tente novamente."
    FailureKind.CONNECTION_FAILURE ->
        "Não foi possível concluir a autenticação com o modem."
}

@Composable
private fun ErrorGlyph() {
    // Capturado fora do `Canvas`: o lambda de desenho roda em `DrawScope`, sem `MaterialTheme`.
    val glyphColor = LocalNetHalExtendedColors.current.error
    Canvas(modifier = Modifier.size(40.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(
            color = glyphColor,
            radius = size.minDimension / 2f - strokeWidth,
            style = Stroke(width = strokeWidth),
        )
        val inset = size.minDimension * 0.32f
        drawLine(
            color = glyphColor,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth * 1.1f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = glyphColor,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth * 1.1f,
            cap = StrokeCap.Round,
        )
    }
}
