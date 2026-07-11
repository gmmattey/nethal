package com.nethal.feature.onboarding

/**
 * Estado real das permissões relevantes ao onboarding, no momento em que a tela `1e`
 * (Permissões concedidas, issue #72) é exibida.
 *
 * Este módulo **nunca solicita** as permissões em si (isso acontece em `2a`/`DiscoveryScreen`
 * para localização, e na tela `1d` — issue #71, fora deste módulo — para notificações). O host
 * que compõe o onboarding (via `onboardingGraph`) é responsável por ler o estado real do Android
 * (`ContextCompat.checkSelfPermission`) e injetar aqui — a tela só exibe e nunca trava o fluxo
 * independentemente do valor (`/regras-android-nethal`, AC da issue #72).
 */
data class OnboardingPermissionsState(
    val locationGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    val allGranted: Boolean get() = locationGranted && notificationsGranted
}
