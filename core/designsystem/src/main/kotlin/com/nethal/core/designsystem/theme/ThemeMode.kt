package com.nethal.core.designsystem.theme

/**
 * Modo de tema escolhido pelo usuário no seletor de Configurações (issue #132).
 *
 * - [LIGHT] / [DARK] forçam o esquema de cores independentemente do sistema.
 * - [SYSTEM] segue o modo do dispositivo (`isSystemInDarkTheme()`), resolvido dentro de
 *   `NetHalLabTheme`.
 *
 * O padrão de produto é [SYSTEM] — respeita a preferência global do aparelho até o usuário
 * escolher explicitamente claro ou escuro.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
