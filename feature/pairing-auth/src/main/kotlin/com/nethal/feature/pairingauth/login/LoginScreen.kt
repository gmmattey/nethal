package com.nethal.feature.pairingauth.login

import com.nethal.core.designsystem.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.feature.pairingauth.CredentialTestState
import com.nethal.feature.pairingauth.PairingAuthUiState
import com.nethal.feature.pairingauth.PairingAuthViewModel

/**
 * Tela `2c` — Login no modem (issue #76). Cobre só o formulário [PairingAuthUiState.Ready] — os
 * estados "resolvendo driver" e "driver indisponível" nunca chegam a renderizar esta tela (o
 * grafo, `PairingAuthGraph.kt`, redireciona `DriverUnavailable` direto para a tela de Falha, `2f`,
 * antes de compor este Composable; `ResolvingDriver` é síncrono e não tem UI dedicada).
 *
 * Os três avisos de segurança abaixo (senha não salva, sessão administrativa única, TOFU
 * condicional) são preservados 1:1 a partir do antigo `AuthenticationScreen.ReadyContent`
 * (`:app`) — requisito de segurança, não decoração (`/seguranca-nethal`), mesmo o protótipo visual
 * (`docs/design/prototypes.dc.html`, `2c`) não os desenhando explicitamente.
 */
@Composable
fun LoginScreen(
    viewModel: PairingAuthViewModel,
    onNavigateToPasswordHelp: () -> Unit,
    onSubmit: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val readyState = uiState as? PairingAuthUiState.Ready
    val isTesting = readyState?.credentialTestState is CredentialTestState.Testing
    val canSubmit = readyState != null &&
        viewModel.username.isNotBlank() &&
        viewModel.password.isNotBlank() &&
        !isTesting

    Scaffold(containerColor = BackgroundDark) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .padding(horizontal = 26.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Acesse seu modem",
                color = OnBackgroundDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = subtitleFor(readyState),
                color = OnSurfaceVariantDark,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OnBackgroundDark,
                unfocusedTextColor = OnBackgroundDark,
                focusedBorderColor = NetHalAccent,
                unfocusedBorderColor = BorderDark,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    FieldCaption("USUÁRIO")
                    OutlinedTextField(
                        value = viewModel.username,
                        onValueChange = viewModel::onUsernameChanged,
                        singleLine = true,
                        enabled = readyState != null && !isTesting,
                        colors = fieldColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login-username-field"),
                    )
                }
                Column {
                    FieldCaption("SENHA DO MODEM")
                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = viewModel::onPasswordChanged,
                        singleLine = true,
                        enabled = readyState != null && !isTesting,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login-password-field"),
                    )
                }
                TextButton(onClick = onNavigateToPasswordHelp) {
                    Text(
                        text = "Esqueci a senha do modem",
                        color = NetHalAccent,
                        fontSize = 12.sp,
                    )
                }
            }

            SecurityNoticeCard(showTofuWarning = readyState?.showTofuWarning == true)

            if (readyState != null) {
                CredentialTestFeedback(readyState.credentialTestState)
            }

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = NetHalAccent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text(
                    text = if (isTesting) "Entrando..." else "Entrar no modem",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun subtitleFor(state: PairingAuthUiState.Ready?): String {
    val deviceLabel = state?.let { "${it.vendor} ${it.model}".trim() }
    return if (deviceLabel.isNullOrBlank()) {
        "Você já está conectado à rede Wi-Fi. Informe as credenciais de administrador do modem para continuar."
    } else {
        "Você já está conectado à rede Wi-Fi. Informe as credenciais de administrador do $deviceLabel para continuar."
    }
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        color = OnSurfaceTertiaryDark,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Card de avisos de segurança do Login — preservado 1:1 (texto e destaque visual) a partir do
 * antigo `AuthenticationScreen.ReadyContent` (`:app`): senha nunca salva, sessão administrativa
 * única, e TOFU condicional ao driver `tplink-stok-luci-driver` (`/seguranca-nethal`).
 */
@Composable
private fun SecurityNoticeCard(showTofuWarning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(color = SurfaceDark, shape = RoundedCornerShape(18.dp))
            .border(width = 1.dp, color = BorderDark, shape = RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Sua senha não é salva neste aparelho nem enviada a nenhum servidor — ela " +
                "existe só durante esta sessão, na memória do app.",
            color = OnSurfaceVariantDark,
            fontSize = 11.5.sp,
        )
        Text(
            text = "Este e outros equipamentos da mesma família aceitam só uma sessão " +
                "administrativa por vez. Se a WebUI do equipamento estiver aberta em um " +
                "navegador, feche-a antes de entrar — do contrário a autenticação pode falhar.",
            color = OnSurfaceVariantDark,
            fontSize = 11.5.sp,
        )
        if (showTofuWarning) {
            Text(
                text = "Este equipamento busca sua própria chave de criptografia no momento do " +
                    "login, sem certificado digital — não é possível confirmar de antemão que " +
                    "você está realmente falando com o seu roteador. Use esta autenticação " +
                    "apenas na sua própria rede local, em que você confia.",
                color = ErrorDark,
                fontSize = 11.5.sp,
            )
        }
    }
}

@Composable
private fun CredentialTestFeedback(testState: CredentialTestState) {
    when (testState) {
        is CredentialTestState.Idle, is CredentialTestState.Testing, is CredentialTestState.Success -> Unit
        // Falha/inválida: o teste some para a tela de Falha (2f) por navegação — este texto inline
        // só aparece no instante entre a resposta chegar e a navegação disparar, honesto mas breve.
        is CredentialTestState.InvalidCredentials -> Text(
            text = "Usuário ou senha incorretos: ${testState.reason}",
            color = ErrorDark,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        is CredentialTestState.Failure -> Text(
            text = "Não foi possível autenticar: ${testState.reason}",
            color = ErrorDark,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
