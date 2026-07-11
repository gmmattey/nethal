package com.nethal.feature.onboarding

/**
 * Estado real das permissões relevantes ao onboarding, no momento em que a tela `1e`
 * (Permissões concedidas, issue #72) é exibida.
 *
 * A permissão de localização **nunca** é solicitada por este módulo (isso acontece em
 * `2a`/`DiscoveryScreen`, fora daqui) — só a de notificação é, diretamente pela tela `1d`
 * (`OnboardingNotificationsScreen`, issue #71). O host que compõe o onboarding (via
 * `onboardingGraph`) é responsável por ler o estado real do Android
 * (`ContextCompat.checkSelfPermission`) e injetar aqui — a tela `1e` só exibe e nunca trava o
 * fluxo independentemente do valor (`/regras-android-nethal`, AC da issue #72).
 */
data class OnboardingPermissionsState(
    val locationGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    val allGranted: Boolean get() = locationGranted && notificationsGranted
}
