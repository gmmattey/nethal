plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nethal.feature.pairingdiscovery"
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
    // Regra ADR 0002: nunca depende de outro :feature:*. Só :core:* da capability consumida
    // (:core:fingerprint entra aqui porque a Tela 2b/#75 roda o Fingerprint Engine — não é
    // capability de "discovery" propriamente, mas é exigida pelo EquipmentDetectedViewModel
    // migrado; :core:protocol entra por uso direto de PrivateIpRanges no guard de IP manual).
    api(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:catalog"))
    implementation(project(":core:discovery"))
    implementation(project(":core:fingerprint"))
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

    // `CompatibilityProfile` (catalog) tem `driverConfig: JsonElement` — o construtor precisa do
    // tipo resolvível mesmo usando o default (`JsonNull`), o que os testes de fixture aqui
    // disparam (:core:catalog só expõe essa dependência como `implementation`, não `api`).
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
