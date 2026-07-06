package com.nethal.core.driver.tplink

import kotlinx.coroutines.runBlocking

/**
 * Diagnóstico manual do driver TP-Link contra hardware real na LAN. Não é chamado por nenhum
 * outro código do produto e nunca roda em `test`/CI — só via `./gradlew :core:tplinkManualCheck`,
 * disparado manualmente pelo próprio usuário no terminal dele.
 *
 * A senha nunca deve ser passada como argumento de linha de comando (ficaria no histórico do
 * shell) nem digitada numa sessão do Claude Code — sempre via prompt interativo, num terminal
 * próprio, fora de qualquer transcript de conversa.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Uso: gradlew :core:tplinkManualCheck --args=\"<ip> <usuario> [cbc|gcm]\"")
        println("A senha é pedida depois, de forma interativa — nunca via argumento.")
        println("Variante de cifra padrão: cbc (firmware antigo). Use 'gcm' se o login falhar com cbc.")
        return
    }

    val ip = args[0]
    val username = args[1]
    val cipherVariant = when (args.getOrNull(2)?.lowercase()) {
        "gcm" -> TplinkCipherVariant.AES_GCM
        else -> TplinkCipherVariant.AES_CBC
    }

    val console = System.console()
    val password = if (console != null) {
        String(console.readPassword("Senha do TP-Link (não aparece na tela): "))
    } else {
        println("Aviso: console interativo não detectado (comum ao rodar via IDE/Gradle Daemon).")
        println("A senha pode ficar visível neste terminal. Prefira rodar via `gradlew` direto num shell.")
        print("Senha do TP-Link: ")
        readlnOrNull().orEmpty()
    }

    if (password.isBlank()) {
        println("Senha vazia, abortando.")
        return
    }

    println("Conectando em $ip como \"$username\" (cifra: $cipherVariant)...")

    val driver = try {
        TplinkOntDriver(ip, cipherVariant = cipherVariant)
    } catch (e: IllegalArgumentException) {
        println("Host recusado: ${e.message}")
        return
    }

    val result = runBlocking { driver.readSnapshot(username, password) }

    when (result) {
        is TplinkDriverResult.Success -> {
            val snapshot = result.snapshot
            println()
            println("--- Device Info ---")
            println(snapshot.deviceInfo?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- WAN ---")
            println(snapshot.wan?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- Wi-Fi ---")
            if (snapshot.wifi.isEmpty()) println("(nenhuma banda interpretada)") else snapshot.wifi.forEach(::println)
            println("--- Clientes conectados ---")
            if (snapshot.connectedClients.isEmpty()) println("(nenhum cliente interpretado)") else snapshot.connectedClients.forEach(::println)
            println("(copie estes valores, e quaisquer diferenças de endpoint/campo observadas, para o catálogo de compatibilidade)")
        }
        is TplinkDriverResult.Failure -> {
            println("Falha: ${result.reason} — ${result.message}")
            if (cipherVariant == TplinkCipherVariant.AES_CBC) {
                println("Se a falha for de resposta inesperada durante o login, tente novamente com o argumento 'gcm' (firmware recente pode usar AES-GCM em vez de AES-CBC).")
            }
        }
    }
}
