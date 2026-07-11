package com.nethal.feature.onboarding.ui

import com.nethal.feature.onboarding.OnboardingPermissionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cobre a copy adaptativa da issue #72 — nunca deve travar nem prometer prazo. */
class OnboardingPermissionsSummaryCopyTest {

    @Test
    fun `tudo concedido usa copy do prototipo`() {
        val (title, body) = onboardingPermissionsSummaryCopy(
            OnboardingPermissionsState(locationGranted = true, notificationsGranted = true),
        )

        assertEquals("Permissões concedidas", title)
        assertEquals("Agora vamos localizar e parear seu roteador na rede.", body)
    }

    @Test
    fun `localizacao negada explica degradacao com alternativa de IP manual`() {
        val (title, body) = onboardingPermissionsSummaryCopy(
            OnboardingPermissionsState(locationGranted = false, notificationsGranted = true),
        )

        assertEquals("Podemos continuar", title)
        assertTrue(body.contains("localização"))
        assertTrue(body.contains("IP"))
    }

    @Test
    fun `notificacoes negadas nao mencionam busca automatica`() {
        val (title, body) = onboardingPermissionsSummaryCopy(
            OnboardingPermissionsState(locationGranted = true, notificationsGranted = false),
        )

        assertEquals("Podemos continuar", title)
        assertTrue(body.contains("notificações"))
        assertTrue(body.contains("alertas"))
    }

    @Test
    fun `ambas negadas nunca prometem prazo`() {
        val (title, body) = onboardingPermissionsSummaryCopy(
            OnboardingPermissionsState(locationGranted = false, notificationsGranted = false),
        )

        assertEquals("Podemos continuar", title)
        assertTrue(body.contains("localização e notificações"))
        assertTrue(body.contains("IP manualmente"))
    }
}
