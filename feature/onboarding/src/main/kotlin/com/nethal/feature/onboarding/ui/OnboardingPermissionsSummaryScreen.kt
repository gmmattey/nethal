package com.nethal.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.feature.onboarding.OnboardingPermissionsState

/** Test tags estáveis para Compose UI Test — issue #72. */
object OnboardingPermissionsSummaryScreenTestTags {
    const val TITLE = "onboarding_permissions_summary_title"
    const val CONTINUE_BUTTON = "onboarding_permissions_summary_continue"
    const val LOCATION_ROW = "onboarding_permissions_summary_location_row"
    const val NOTIFICATIONS_ROW = "onboarding_permissions_summary_notifications_row"
}

/**
 * Tela `1e` — Onboarding: Permissões concedidas (issue #72).
 *
 * Resumo adaptativo do estado real de [state] — nunca o check genérico único do protótipo. Se
 * alguma permissão foi negada, o fluxo **não trava**: título muda para "Podemos continuar", corpo
 * explica a degradação (sem inventar prazo/promessa), e o CTA continua sempre habilitado
 * (`/regras-android-nethal`, AC da issue #72). Repetir a solicitação de permissão não é
 * responsabilidade desta tela — isso já aconteceu em `1b`/`1d`.
 */
@Composable
fun OnboardingPermissionsSummaryScreen(
    state: OnboardingPermissionsState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) = onboardingPermissionsSummaryCopy(state)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
            .padding(PaddingValues(horizontal = 26.dp, vertical = 28.dp)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ringColor = if (state.allGranted) OnboardingColors.Success else OnboardingColors.TextTertiary
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (state.allGranted) {
                    CheckGlyph(modifier = Modifier.size(24.dp))
                } else {
                    DashGlyph(modifier = Modifier.size(24.dp), tint = ringColor)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = OnboardingColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(OnboardingPermissionsSummaryScreenTestTags.TITLE),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = body,
                color = OnboardingColors.TextSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OnboardingPermissionStatusRow(
                label = "Localização",
                granted = state.locationGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(OnboardingPermissionsSummaryScreenTestTags.LOCATION_ROW),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OnboardingPermissionStatusRow(
                label = "Notificações",
                granted = state.notificationsGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(OnboardingPermissionsSummaryScreenTestTags.NOTIFICATIONS_ROW),
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OnboardingProgressDots(activeIndex = 3, modifier = Modifier.padding(bottom = 20.dp))
        }

        OnboardingPrimaryButton(
            text = "Parear roteador →",
            onClick = onContinue,
            modifier = Modifier.testTag(OnboardingPermissionsSummaryScreenTestTags.CONTINUE_BUTTON),
        )
    }
}

@Composable
private fun OnboardingPermissionStatusRow(
    label: String,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (granted) {
            CheckGlyph(modifier = Modifier.size(16.dp))
        } else {
            DashGlyph(modifier = Modifier.size(16.dp))
        }
        Text(
            text = label,
            color = OnboardingColors.TextPrimary,
            fontSize = 13.sp,
        )
    }
}

/**
 * Copy adaptativa conforme `docs/design/specs/2026-07-11-onboarding-e-pareamento-manual.md`
 * §1e. Nunca inventa prazo; degradação descrita é sempre acionável (IP manual).
 */
fun onboardingPermissionsSummaryCopy(state: OnboardingPermissionsState): Pair<String, String> {
    if (state.allGranted) {
        return "Permissões concedidas" to "Agora vamos localizar e parear seu roteador na rede."
    }

    val missing = buildList {
        if (!state.locationGranted) add("localização")
        if (!state.notificationsGranted) add("notificações")
    }.joinToString(" e ")

    val degradation = when {
        !state.locationGranted && !state.notificationsGranted ->
            "a busca automática do roteador não vai funcionar e você não vai receber alertas. " +
                "Você pode inserir o IP manualmente e continuar."
        !state.locationGranted ->
            "a busca automática do roteador não vai funcionar. Você pode inserir o IP " +
                "manualmente e continuar."
        else ->
            "você não vai receber alertas do NetHAL. Isso não impede o pareamento do roteador."
    }

    return "Podemos continuar" to "Sem $missing, $degradation"
}
