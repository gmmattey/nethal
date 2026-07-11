plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nethal.feature.devices"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    // Reaproveita o Discovery Engine existente (SsdpDiscoverer pure-JVM + contrato
    // NetworkEnvironmentReader) em vez de duplicar heurística de SSDP — ver decisão de
    // arquitetura no PR desta issue (#105).
    implementation(project(":core:discovery"))
    // PrivateIpRanges — mesma guarda de RFC 1918 usada em todo o resto do NetHAL como defesa em
    // profundidade (nunca varre fora de sub-rede privada conhecida).
    implementation(project(":core:protocol"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
