package com.nethal.core.driver.nokia

import kotlinx.coroutines.runBlocking

/**
 * Diagnóstico manual do driver Nokia contra hardware real na LAN (SIG-333). Não é chamado por
 * nenhum outro código do produto e nunca roda em `test`/CI — só via `./gradlew :core:nokiaManualCheck`,
 * disparado manualmente pelo próprio usuário no terminal dele.
 *
 * A senha nunca deve ser passada como argumento de linha de comando (ficaria no histórico do
 * shell) nem digitada numa sessão do Claude Code — sempre via prompt interativo, num terminal
 * próprio, fora de qualquer transcript de conversa.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Uso: gradlew :core:nokiaManualCheck --args=\"<ip> <usuario>\"")
        println("A senha é pedida depois, de forma interativa — nunca via argumento.")
        return
    }

    val ip = args[0]
    val username = args[1]

    val console = System.console()
    val password = if (console != null) {
        String(console.readPassword("Senha do Nokia (não aparece na tela): "))
    } else {
        println("Aviso: console interativo não detectado (comum ao rodar via IDE/Gradle Daemon).")
        println("A senha pode ficar visível neste terminal. Prefira rodar via `gradlew` direto num shell.")
        print("Senha do Nokia: ")
        readlnOrNull().orEmpty()
    }

    if (password.isBlank()) {
        println("Senha vazia, abortando.")
        return
    }

    println("Conectando em $ip como \"$username\"...")

    val driver = try {
        NokiaOntDriver(ip)
    } catch (e: IllegalArgumentException) {
        println("Host recusado: ${e.message}")
        return
    }

    val result = runBlocking { driver.readSnapshot(username, password) }

    when (result) {
        is NokiaDriverResult.Success -> {
            val snapshot = result.snapshot
            println()
            println("--- GPON ---")
            println(snapshot.gpon?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- WAN ---")
            println(snapshot.wan?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- PPP ---")
            println(snapshot.ppp?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- Device Info ---")
            println(snapshot.deviceInfo?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- Evidência de fingerprint (Tela de login) ---")
            val evidence = snapshot.loginPageEvidence
            println("Título HTML: ${evidence?.httpTitle ?: "(não capturado)"}")
            println("Header Server: ${evidence?.serverHeader ?: "(não capturado / ausente na resposta)"}")
            println("(dados não sensíveis — sem credencial; copie estes dois valores para o catálogo de compatibilidade)")
        }
        is NokiaDriverResult.Failure -> {
            println("Falha: ${result.reason} — ${result.message}")
        }
    }
}
