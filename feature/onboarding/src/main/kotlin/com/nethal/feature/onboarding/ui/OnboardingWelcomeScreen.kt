package com.nethal.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nethal.core.consent.ConsentRepository
import com.nethal.core.consent.ConsentScope
import kotlinx.coroutines.launch

/** Test tags estáveis para Compose UI Test — issue #68. */
object OnboardingWelcomeScreenTestTags {
    const val TITLE = "onboarding_welcome_title"
    const val NETWORK_AUTHORIZATION_CHECKBOX = "onboarding_welcome_network_authorization"
    const val START_BUTTON = "onboarding_welcome_start"
    const val PRIVACY_LINK = "onboarding_welcome_privacy_link"
    const val COMPATIBLE_DEVICES_LINK = "onboarding_welcome_compatible_devices_link"
}

/**
 * Tela `1a` — Onboarding: Boas-vindas (issue #68), primeira tela do fluxo, substitui
 * `app/.../ui/onboarding/WelcomeScreen.kt`.
 *
 * A spec da Vera (`docs/design/specs/2026-07-11-onboarding-e-pareamento-manual.md`) cobre `1b`,
 * `1c`, `1e`, `1f` — não cobre esta tela. Implementação direto do protótipo
 * (`docs/design/prototypes.dc.html` `1a`), com o mesmo rigor de honestidade aplicado por ela nas
 * outras telas do lote:
 *
 * - **Checkbox de autorização de rede preservado (SIG-312/313):** o protótipo `1a` não desenha
 *   nenhum checkbox — só hero, título, corpo, botão "Começar" e um link. A issue #68 é explícita:
 *   essa é regra de negócio, não decoração de layout, e não pode sumir ao redesenhar. Adicionado
 *   abaixo do corpo, reaproveitando [OnboardingCheckboxRow] (mesmo componente da tela `1d`).
 * - **Botão primário mantém o texto "Iniciar diagnóstico" (não "Começar" do protótipo):** a AC da
 *   issue nomeia esse texto explicitamente, entre aspas, junto da trava SIG-312/313 — é a mesma
 *   string usada por `WelcomeViewModel` hoje (`confirmAndProceed`), tratada aqui como requisito de
 *   produto deliberado, não como resíduo do protótipo genérico. Fidelidade visual (cor, raio,
 *   posição) segue o protótipo; só o texto do CTA diverge, e essa divergência é este comentário.
 * - **"Ver privacidade" preservado mesmo sem existir no protótipo:** a issue exige manter esse
 *   botão funcionalmente (destino real depende da issue #85, ainda não implementada — decisão
 *   #66) — adicionado como segundo link, abaixo de "Ver dispositivos compatíveis" (este sim já no
 *   protótipo, `href="#1f"`).
 * - **"Sair" removido:** existia na tela antiga, mas não está no protótipo nem é exigido pela AC
 *   da issue #68 — dropado por não ter mais lugar no paradigma de app sempre ligado (decisão #66).
 *
 * Concede `ConsentScope.NETWORK_AUTHORIZATION` e `ConsentScope.READ_STATUS` ao avançar — mesma
 * semântica de `WelcomeViewModel.confirmAndProceed` hoje em produção, preservada aqui para não
 * regredir consentimento silenciosamente.
 */
@Composable
fun OnboardingWelcomeScreen(
    consentRepository: ConsentRepository,
    onStartDiagnosis: () -> Unit,
    onViewPrivacy: () -> Unit,
    onViewCompatibleDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var networkAuthorizationConfirmed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun confirmAndStart() {
        if (!networkAuthorizationConfirmed) return
        coroutineScope.launch {
            consentRepository.grant(ConsentScope.NETWORK_AUTHORIZATION, System.currentTimeMillis())
            consentRepository.grant(ConsentScope.READ_STATUS, System.currentTimeMillis())
            onStartDiagnosis()
        }
    }

    // `verticalScroll` torna o eixo principal ilimitado — por isso o conteúdo flui em sequência
    // (sem `Modifier.weight`, incompatível com altura infinita) em vez de vertically-centered como
    // `1b`/`1c`/`1e`. Necessário aqui porque o corpo desta tela é mais longo (2 parágrafos +
    // checkbox) e precisa caber em telas pequenas sem cortar conteúdo.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
            .padding(PaddingValues(horizontal = 26.dp, vertical = 28.dp))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingConcentricHero {
            NetHalMarkGlyph(modifier = Modifier.size(76.dp))
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Bem-vindo ao NETHAL",
            color = OnboardingColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(OnboardingWelcomeScreenTestTags.TITLE),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Ferramenta experimental para detectar e testar compatibilidade com " +
                "roteadores e ONTs na sua rede local.",
            color = OnboardingColors.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Para identificar sua rede, o Android exige permissão de localização. " +
                "O NetHAL usa isso apenas para ler o nome (SSID) da rede Wi-Fi conectada — " +
                "nunca para rastrear sua localização geográfica.",
            color = OnboardingColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingCheckboxRow(
            label = "Esta é a minha rede, ou tenho autorização para testá-la.",
            checked = networkAuthorizationConfirmed,
            onCheckedChange = { networkAuthorizationConfirmed = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingWelcomeScreenTestTags.NETWORK_AUTHORIZATION_CHECKBOX),
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingProgressDots(activeIndex = 0)

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingPrimaryButton(
            text = "Iniciar diagnóstico",
            onClick = ::confirmAndStart,
            enabled = networkAuthorizationConfirmed,
            modifier = Modifier.testTag(OnboardingWelcomeScreenTestTags.START_BUTTON),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Ver dispositivos compatíveis",
            color = OnboardingColors.TextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewCompatibleDevices)
                .padding(vertical = 6.dp)
                .testTag(OnboardingWelcomeScreenTestTags.COMPATIBLE_DEVICES_LINK),
        )

        Text(
            text = "Ver privacidade",
            color = OnboardingColors.TextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewPrivacy)
                .padding(vertical = 6.dp)
                .testTag(OnboardingWelcomeScreenTestTags.PRIVACY_LINK),
        )
    }
}
