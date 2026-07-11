import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Diagnostico manual - passo 7 do plano de refatoracao HAL (hal-layering-model.md SS10): as tres
// tasks abaixo agora compartilham o mesmo runner (core/tooling/ManualCheckRunner.kt), que decide o
// caminho certo (Driver Family via registry, hoje so para o C20, ou instanciacao direta para
// C6/Nokia) a partir do profileId fixado por cada task via argumentProviders. Nomes de task e
// forma de uso (--args="<ip> <usuario> [cbc|gcm]") preservados de proposito, para nao quebrar o
// habito de quem ja roda `gradlew :core:tplinkC20ManualCheck` etc. - o profileId e injetado antes
// dos argumentos passados pelo usuario, nunca precisa ser digitado por ele.
tasks.register<JavaExec>("nokiaManualCheck") {
    group = "verification"
    description = "Diagnostico manual contra um Nokia G-1425G-B real na LAN (SIG-333). Uso: gradlew :core:nokiaManualCheck --args=\"<ip> <usuario>\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.nethal.core.tooling.ManualCheckRunnerKt")
    argumentProviders.add(CommandLineArgumentProvider { listOf("nokia_g1425gb_v1") })
    standardInput = System.`in`
}

tasks.register<JavaExec>("tplinkManualCheck") {
    group = "verification"
    description = "Diagnostico manual contra um TP-Link Archer C6 real na LAN. Uso: gradlew :core:tplinkManualCheck --args=\"<ip> <usuario> [cbc|gcm]\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.nethal.core.tooling.ManualCheckRunnerKt")
    argumentProviders.add(CommandLineArgumentProvider { listOf("tplink_archer_c6_v1") })
    standardInput = System.`in`
}

tasks.register<JavaExec>("tplinkC20ManualCheck") {
    group = "verification"
    description = "Diagnostico manual contra um TP-Link Archer C20 real na LAN. Uso: gradlew :core:tplinkC20ManualCheck --args=\"<ip> <usuario>\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.nethal.core.tooling.ManualCheckRunnerKt")
    argumentProviders.add(CommandLineArgumentProvider { listOf("tplink_archer_c20_v1") })
    standardInput = System.`in`
}

tasks.register<JavaExec>("tplinkC6StokManualCheck") {
    group = "verification"
    description = "Diagnostico manual contra um TP-Link Archer C6 real na LAN, plataforma stok/luci (profile tplink_archer_c6_stok_v1, DISCOVERY_ONLY, handshake real ja confirmado; login ainda em refinamento). Uso: gradlew :core:tplinkC6StokManualCheck --args=\"<ip> <usuario>\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.nethal.core.tooling.ManualCheckRunnerKt")
    argumentProviders.add(CommandLineArgumentProvider { listOf("tplink_archer_c6_stok_v1") })
    standardInput = System.`in`
}
