plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Diagnostico manual do driver Nokia contra hardware real (SIG-333) - nunca roda em CI/test,
// so quando o usuario dispara explicitamente. Ver ManualCheckRunner.kt para o porque de a
// senha ser sempre interativa, nunca argumento de linha de comando.
tasks.register<JavaExec>("nokiaManualCheck") {
    group = "verification"
    description = "Diagnostico manual contra um Nokia G-1425G-B real na LAN (SIG-333). Uso: gradlew :core:nokiaManualCheck --args=\"<ip> <usuario>\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.nethal.core.driver.nokia.ManualCheckRunnerKt")
    standardInput = System.`in`
}
