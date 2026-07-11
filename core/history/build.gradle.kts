plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nethal.core.history"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // com.android.library (não kotlin.jvm, ao contrário da maioria dos outros core:*) porque
    // depende de :core:scheduling, que por sua vez é android.library (WorkManager/SQLiteOpenHelper
    // exigem Context). Um módulo kotlin.jvm puro não resolve dependência de projeto contra um
    // android.library (variantes incompatíveis) — ver decisão registrada no PR da issue #104.
    // Nenhuma classe aqui usa API Android de verdade; é só para casar o variant do Gradle.
    implementation(project(":core:scheduling"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
