package com.nethal.core.tooling

import com.nethal.core.catalog.DefaultDriverRegistry
import com.nethal.core.catalog.loadEmbeddedCatalogResource
import com.nethal.core.driver.family.defaultDriverFamilyRegistry
import com.nethal.core.driver.family.tplink.legacycgi.TpLinkLegacyCgiDriverFamily
import com.nethal.core.driver.family.tplink.legacycgi.TpLinkLegacyCgiReadOutcome
import com.nethal.core.driver.family.tplink.stokluci.TpLinkStokLuciDriverFamily
import com.nethal.core.driver.family.tplink.stokluci.TpLinkStokLuciLoginOutcome
import com.nethal.core.driver.family.tplink.stokluci.TpLinkStokLuciStatusOutcome
import com.nethal.core.driver.nokia.NokiaDriverResult
import com.nethal.core.driver.nokia.NokiaOntDriver
import com.nethal.core.driver.tplink.TplinkCipherVariant
import com.nethal.core.driver.tplink.TplinkDriverResult
import com.nethal.core.driver.tplink.TplinkOntDriver
import com.nethal.core.protocol.http.DefaultHttpTransport
import com.nethal.core.protocol.http.HttpTransportConfig
import kotlinx.coroutines.runBlocking
import java.net.URL

/**
 * Diagnóstico manual unificado — roda qualquer um dos drivers de leitura reais do NetHAL contra
 * hardware físico na LAN. Não é chamado por nenhum outro código do produto e nunca roda em
 * `test`/CI — só via `./gradlew :core:tplinkManualCheck`, `:core:nokiaManualCheck` ou
 * `:core:tplinkC20ManualCheck`, disparado manualmente pelo próprio usuário no terminal dele.
 *
 * Consolida os três `ManualCheckRunner` que existiam antes (`driver/tplink/ManualCheckRunner.kt`,
 * `driver/nokia/ManualCheckRunner.kt`, `driver/tplink/ManualCheckRunnerC20.kt`) num único ponto de
 * entrada, parametrizado por um argumento de `profileId` (`tplink_archer_c6_v1`,
 * `nokia_g1425gb_v1` ou `tplink_archer_c20_v1`). Passo 7 do plano de refatoração
 * (`docs/architecture/hal-layering-model.md` §10) — só elimina a duplicação de ferramenta de
 * diagnóstico, não migra nenhum driver de produto.
 *
 * O `profileId` é localizado em qualquer posição de `args` (não assume índice fixo), porque cada
 * task Gradle (`tplinkManualCheck`, `nokiaManualCheck`, `tplinkC20ManualCheck`) já injeta o
 * `profileId` correto via `argumentProviders` — mecanismo do Gradle que sempre anexa **depois** dos
 * argumentos vindos de `--args` do usuário. Continua possível rodar via `java -cp ...` passando o
 * `profileId` em qualquer posição entre `<ip>`/`<usuario>`/`[cbc|gcm]`.
 *
 * ## Assimetria deliberada entre profiles (temporária)
 *
 * Hoje só o TP-Link Archer C20 (`tplink_archer_c20_v1`) foi migrado para o desenho
 * Driver Family/`DriverFamilyRegistry` (passo 4 do plano). O catálogo (`catalog-2026.07.13.json`)
 * já declara `driverFamilyId` também para `tplink_archer_c6_v1`
 * (`tplink-encrypted-web-driver`) e `nokia_g1425gb_v1` (`nokia-ont-gpon-driver`), mas
 * **nenhuma [com.nethal.core.catalog.DriverFamilyFactory] para essas duas foi registrada em
 * [defaultDriverFamilyRegistry] ainda** — `TplinkOntDriver`/`NokiaOntDriver` continuam sendo
 * instanciados diretamente com `host`, exatamente como antes desta consolidação.
 *
 * Ou seja: este runner sabe, por `profileId`, qual dos dois caminhos seguir —
 * não tenta primeiro o caminho via registry e cai para o direto em caso de erro. Isso é
 * intencional: `DriverFamilyRegistry.resolve` lança [com.nethal.core.catalog.UnknownDriverFamilyException]
 * de propósito quando a factory não existe (falha alta e cedo — ver KDoc da classe), então usar
 * try/catch para decidir o caminho esconderia esse sinal de integridade em vez de expressar a
 * assimetria real. **Migrar TP-Link C6 e Nokia para Driver Family de verdade é trabalho futuro,
 * fora de escopo deste passo** — quando isso acontecer, os dois branches `TPLINK_C6`/`NOKIA` abaixo
 * colapsam no mesmo branch do `TPLINK_C20`.
 *
 * A senha nunca deve ser passada como argumento de linha de comando (ficaria no histórico do
 * shell) nem digitada numa sessão do Claude Code — sempre via prompt interativo, num terminal
 * próprio, fora de qualquer transcript de conversa.
 */

private enum class ManualCheckProfile(val profileId: String, val displayName: String) {
    TPLINK_C6("tplink_archer_c6_v1", "TP-Link Archer C6"),
    NOKIA("nokia_g1425gb_v1", "Nokia G-1425G-B"),
    TPLINK_C20("tplink_archer_c20_v1", "TP-Link Archer C20"),
    TPLINK_C6_STOK("tplink_archer_c6_stok_v1", "TP-Link Archer C6 (plataforma stok/luci)"),
    ;

    companion object {
        fun fromArg(arg: String): ManualCheckProfile? = entries.firstOrNull { it.profileId == arg }
    }
}

/**
 * A task Gradle correspondente injeta o `profileId` via `argumentProviders`, que o Gradle sempre
 * anexa *depois* dos argumentos vindos de `--args` do usuário — não antes. Por isso o profile é
 * localizado por valor em qualquer posição de [args] (hoje sempre a última), em vez de assumir uma
 * posição fixa; os demais argumentos, na ordem em que sobraram, continuam sendo `<ip> <usuario>
 * [cbc|gcm]`, exatamente como o usuário já digitava antes desta consolidação.
 */
fun main(args: Array<String>) {
    val profileArgIndex = args.indexOfFirst { ManualCheckProfile.fromArg(it) != null }
    if (profileArgIndex == -1) {
        println("Nenhum profile reconhecido em: ${args.toList()}")
        printUsage()
        return
    }
    val profile = ManualCheckProfile.fromArg(args[profileArgIndex])!!
    val remainingArgs = args.toMutableList().also { it.removeAt(profileArgIndex) }

    if (remainingArgs.size < 2) {
        printUsage()
        return
    }

    val ip = remainingArgs[0]
    val username = remainingArgs[1]

    when (profile) {
        ManualCheckProfile.TPLINK_C6 -> runTplinkC6(ip, username, cipherVariantArg = remainingArgs.getOrNull(2))
        ManualCheckProfile.NOKIA -> runNokia(ip, username)
        ManualCheckProfile.TPLINK_C20 -> runTplinkC20(ip, username)
        ManualCheckProfile.TPLINK_C6_STOK -> runTplinkC6Stok(ip, username)
    }
}

private fun printUsage() {
    println("Uso: gradlew :core:tplinkManualCheck --args=\"<ip> <usuario> [cbc|gcm]\"")
    println("   ou gradlew :core:nokiaManualCheck --args=\"<ip> <usuario>\"")
    println("   ou gradlew :core:tplinkC20ManualCheck --args=\"<ip> <usuario>\"")
    println("   ou gradlew :core:tplinkC6StokManualCheck --args=\"<ip> <usuario>\"")
    println("(cada task já injeta o profileId correto; não é preciso digitá-lo)")
    println("Profiles reconhecidos por este runner: ${ManualCheckProfile.entries.joinToString(", ") { it.profileId }}")
    println("A senha é pedida depois, de forma interativa — nunca via argumento.")
}

/**
 * Lê a senha via `System.console()`. Sem console interativo (comum ao rodar via IDE/Gradle
 * Daemon), cai para `readlnOrNull()` avisando que o valor pode ficar visível no terminal.
 */
private fun readPasswordInteractively(promptLabel: String): String {
    val console = System.console()
    return if (console != null) {
        String(console.readPassword("Senha do $promptLabel (não aparece na tela): "))
    } else {
        println("Aviso: console interativo não detectado (comum ao rodar via IDE/Gradle Daemon).")
        println("A senha pode ficar visível neste terminal. Prefira rodar via `gradlew` direto num shell.")
        print("Senha do $promptLabel: ")
        readlnOrNull().orEmpty()
    }
}

// --- TP-Link Archer C6 (tplink_archer_c6_v1) — caminho direto, ainda não é Driver Family. ---

private fun runTplinkC6(ip: String, username: String, cipherVariantArg: String?) {
    val cipherVariant = when (cipherVariantArg?.lowercase()) {
        "gcm" -> TplinkCipherVariant.AES_GCM
        else -> TplinkCipherVariant.AES_CBC
    }

    val password = readPasswordInteractively("TP-Link Archer C6")
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

// --- Nokia G-1425G-B (nokia_g1425gb_v1) — caminho direto, ainda não é Driver Family. ---

private fun runNokia(ip: String, username: String) {
    val password = readPasswordInteractively("Nokia")
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

// --- TP-Link Archer C20 (tplink_archer_c20_v1) — único caminho via DriverRegistry/DriverFamilyRegistry hoje. ---

private fun runTplinkC20(ip: String, username: String) {
    val password = readPasswordInteractively("TP-Link Archer C20")
    if (password.isBlank()) {
        println("Senha vazia, abortando.")
        return
    }

    // Usa o default de loadEmbeddedCatalogResource() (manifesto embarcado mais recente) — não
    // hardcoda o nome do arquivo aqui de novo para não reintroduzir o drift já corrigido em
    // DriverRegistry.kt (o default chegou a apontar para um manifesto anterior ao profile
    // tplink_archer_c20_v1 existir no catálogo).
    val registry = DefaultDriverRegistry(
        embeddedManifestLoader = { loadEmbeddedCatalogResource() },
    )
    val profile = registry.findProfile(vendor = "TP-Link", model = "Archer C20")
    if (profile == null) {
        println("Profile tplink_archer_c20_v1 não encontrado no catálogo embarcado — catálogo desatualizado?")
        return
    }

    println("Conectando em $ip como \"$username\" (profile=${profile.profileId}, driverFamilyId=${profile.driverFamilyId})...")

    val driverFamilyRegistry = defaultDriverFamilyRegistry()
    // Mesmos parâmetros de DefaultTplinkHttpTransport (driver/tplink/TplinkHttpTransport.kt) —
    // preserva exatamente o transporte usado antes desta reorganização.
    val httpTransport = DefaultHttpTransport(
        HttpTransportConfig(
            connectTimeoutMillis = 10_000,
            getReadTimeoutMillis = 20_000,
            postReadTimeoutMillis = 20_000,
            getAcceptHeader = "application/json, text/html,*/*;q=0.9",
            postAcceptHeader = "application/json, text/plain, */*",
            postContentType = "text/plain",
            postRefererProvider = { url ->
                val base = URL(url)
                "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}/"
            },
            followRedirectsManually = false,
        ),
    )

    val driver = try {
        driverFamilyRegistry.resolve(profile, ip, httpTransport) as TpLinkLegacyCgiDriverFamily
    } catch (e: IllegalArgumentException) {
        println("Host recusado: ${e.message}")
        return
    }

    val result = runBlocking { driver.readSnapshot(username, password) }

    when (result) {
        is TpLinkLegacyCgiReadOutcome.Success -> {
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
        is TpLinkLegacyCgiReadOutcome.Failure -> {
            println("Falha: ${result.reason} — ${result.message}")
            println("Se a falha for de credencial/resposta inesperada, capture com uma ferramenta de rede (DevTools do navegador contra a WebUI real) o corpo de resposta e reporte para corrigir o catálogo/driver — não tente contornar autenticação manualmente.")
        }
    }
}

// --- TP-Link Archer C6, plataforma stok/luci (tplink_archer_c6_stok_v1) — via DriverRegistry/DriverFamilyRegistry. ---
// ATENÇÃO: este mecanismo NUNCA foi testado contra hardware real. O entendimento do protocolo vem
// de pesquisa em código aberto de terceiros (pacote `tplinkrouterc6u`, GPL-3.0) — ver KDoc de
// TpLinkStokLuciAuthenticationClient. O profile continua DISCOVERY_ONLY até este runner produzir
// o primeiro teste real bem-sucedido; qualquer falha aqui deve ser documentada no catálogo
// (fingerprintEvidence[] com confidenceLevel apropriado), nunca contornada manualmente.

private fun runTplinkC6Stok(ip: String, username: String) {
    val password = readPasswordInteractively("TP-Link Archer C6 (plataforma stok/luci)")
    if (password.isBlank()) {
        println("Senha vazia, abortando.")
        return
    }

    val registry = DefaultDriverRegistry(
        embeddedManifestLoader = { loadEmbeddedCatalogResource() },
    )
    val profile = registry.findProfiles(vendor = "TP-Link", model = "Archer C6")
        .firstOrNull { it.profileId == "tplink_archer_c6_stok_v1" }
    if (profile == null) {
        println("Profile tplink_archer_c6_stok_v1 não encontrado no catálogo embarcado — catálogo desatualizado?")
        return
    }

    println("Conectando em $ip como \"$username\" (profile=${profile.profileId}, driverFamilyId=${profile.driverFamilyId})...")
    println("AVISO: este mecanismo nunca foi validado contra hardware real — protocolo entendido só por pesquisa de terceiros (tplinkrouterc6u, GPL-3.0). Reporte o resultado (sucesso ou falha) para atualizar o catálogo.")

    val driverFamilyRegistry = defaultDriverFamilyRegistry()
    val httpTransport = DefaultHttpTransport(
        HttpTransportConfig(
            connectTimeoutMillis = 10_000,
            getReadTimeoutMillis = 20_000,
            postReadTimeoutMillis = 20_000,
            getAcceptHeader = "application/json, text/html,*/*;q=0.9",
            postAcceptHeader = "application/json, text/plain, */*",
            postContentType = "application/x-www-form-urlencoded",
            postRefererProvider = { url ->
                val base = URL(url)
                "${base.protocol}://${base.host}${if (base.port !in listOf(-1, 80, 443)) ":${base.port}" else ""}/webpages/index.html"
            },
            followRedirectsManually = false,
        ),
    )

    val driver = try {
        driverFamilyRegistry.resolve(profile, ip, httpTransport) as TpLinkStokLuciDriverFamily
    } catch (e: IllegalArgumentException) {
        println("Host recusado: ${e.message}")
        return
    }

    val loginResult = runBlocking { driver.login(username, password) }
    when (loginResult) {
        is TpLinkStokLuciLoginOutcome.Success -> {
            println()
            println("--- Login bem-sucedido ---")
            println("stok=${loginResult.session.stok.take(6)}... (truncado, nunca logar o token completo)")
            println("(copie o resultado deste teste — sucesso ou falha — para o catálogo de compatibilidade, profile tplink_archer_c6_stok_v1)")

            println()
            println("Tentando leitura de status geral...")
            val statusResult = runBlocking { driver.readStatusRaw(username, password) }
            when (statusResult) {
                is TpLinkStokLuciStatusOutcome.Success -> {
                    println("--- Status (corpo bruto, JSON) ---")
                    println(statusResult.rawBody)
                    println("(schema ainda não mapeado — antes de colar isso no catálogo, mascare SSID, MAC completo e IP público, mesma regra de sanitização da spec §8.9)")
                }
                is TpLinkStokLuciStatusOutcome.Failure -> {
                    println("Falha na leitura de status: ${statusResult.reason} — ${statusResult.message}")
                }
            }
        }
        is TpLinkStokLuciLoginOutcome.Failure -> {
            println("Falha: ${loginResult.reason} — ${loginResult.message}")
            println("Se a falha for de resposta inesperada, capture com uma ferramenta de rede (DevTools do navegador contra a WebUI real) o corpo de resposta dos endpoints form=keys/form=auth/form=login e reporte para corrigir o catálogo/driver — não tente contornar autenticação manualmente.")
        }
    }
}
