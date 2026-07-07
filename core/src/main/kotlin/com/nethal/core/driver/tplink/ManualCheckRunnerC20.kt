package com.nethal.core.driver.tplink

import kotlinx.coroutines.runBlocking

/**
 * Diagnóstico manual do driver TP-Link Archer C20 contra hardware real na LAN. Não é chamado por
 * nenhum outro código do produto e nunca roda em `test`/CI — só via
 * `./gradlew :core:tplinkC20ManualCheck`, disparado manualmente pelo próprio usuário no terminal
 * dele.
 *
 * Protocolo real confirmado por captura via DevTools contra unidade física do Luiz (2026-07-06,
 * ver SIG-337/SIG-338) — não há mais um passo de "login" separado: a primeira leitura
 * (IGD_DEV_INFO) já valida a credencial via cookie `Authorization: Basic <base64>`. Ver
 * `TplinkC20AuthenticationClient`.
 *
 * A senha nunca deve ser passada como argumento de linha de comando nem digitada numa sessão do
 * Claude Code — sempre via prompt interativo, num terminal próprio, fora de qualquer transcript de
 * conversa.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Uso: gradlew :core:tplinkC20ManualCheck --args=\"<ip> <usuario>\"")
        println("A senha é pedida depois, de forma interativa — nunca via argumento.")
        return
    }

    val ip = args[0]
    val username = args[1]

    val console = System.console()
    val password = if (console != null) {
        String(console.readPassword("Senha do TP-Link Archer C20 (não aparece na tela): "))
    } else {
        println("Aviso: console interativo não detectado (comum ao rodar via IDE/Gradle Daemon).")
        println("A senha pode ficar visível neste terminal. Prefira rodar via `gradlew` direto num shell.")
        print("Senha do TP-Link Archer C20: ")
        readlnOrNull().orEmpty()
    }

    if (password.isBlank()) {
        println("Senha vazia, abortando.")
        return
    }

    println("Conectando em $ip como \"$username\"...")

    val driver = try {
        TplinkC20OntDriver(ip)
    } catch (e: IllegalArgumentException) {
        println("Host recusado: ${e.message}")
        return
    }

    val result = runBlocking { driver.readSnapshot(username, password) }

    when (result) {
        is TplinkC20DriverResult.Success -> {
            val snapshot = result.snapshot
            println()
            println("--- Device Info ---")
            println(snapshot.deviceInfo?.toString() ?: "(não disponível / falha ao interpretar resposta)")
            println("--- WAN ---")
            println("(READ_WAN_STATUS ainda é UNKNOWN — seção real não capturada, não implementada)")
            println("--- Wi-Fi ---")
            if (snapshot.wifi.isEmpty()) println("(nenhuma banda interpretada)") else snapshot.wifi.forEach(::println)
            println("--- Clientes conectados ---")
            if (snapshot.connectedClients.isEmpty()) println("(nenhum cliente interpretado)") else snapshot.connectedClients.forEach(::println)
            println("(copie estes valores, e quaisquer diferenças de seção/campo observadas, para o catálogo de compatibilidade — profile tplink_archer_c20_v1)")
        }
        is TplinkC20DriverResult.Failure -> {
            println("Falha: ${result.reason} — ${result.message}")
            println("Se a falha for de credencial/resposta inesperada, capture com uma ferramenta de rede (DevTools do navegador contra a WebUI real) o corpo de resposta e reporte para corrigir o catálogo/driver — não tente contornar autenticação manualmente.")
        }
    }
}
