plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nethal.feature.pairingauth"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // Regra ADR 0002: nunca depende de outro :feature:*. `:core:catalog` e `:core:protocol` não
    // estavam na lista original do plano (Rafael), mas são inevitáveis aqui: é este módulo que
    // resolve `DriverFamily`/`DriverFamilyRegistry` (catalog) e monta o `HttpTransport` (protocol)
    // usados por `CapabilityEngine.testCredentials()` — mesma dependência que `AuthenticationViewModel`
    // já tinha dentro de `:app` antes desta extração.
    api(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    implementation(project(":core:capability"))
    implementation(project(":core:catalog"))
    implementation(project(":core:protocol"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.ui.tooling)

    // `CompatibilityProfile` (catalog) tem `driverConfig: JsonElement` — mesma necessidade já
    // documentada em `feature/pairing-discovery/build.gradle.kts`.
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
