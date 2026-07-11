plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nethal.core.scheduling"
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

    // PeriodicWorkRequest.Builder loga via android.util.Log quando o intervalo é clampado ao piso
    // de 15min (androidx.work.impl.model.WorkSpec.setPeriodic) — sem isReturnDefaultValues, o
    // android.jar stub do unit test local derruba esse caminho com RuntimeException("not mocked").
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // WorkManager (issue #112) — único jeito real de rodar uma medição em intervalos mesmo com o
    // app fechado/em background; decisão registrada no KDoc de PeriodicMeasurementScheduler.
    // Sem Room: persistência via SQLiteOpenHelper puro (android.database.sqlite, já embutido no
    // Android) — decisão registrada no KDoc de SqliteMeasurementSampleRepository, evita somar
    // KSP/annotation-processing como toolchain nova só para 2 queries triviais.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
}
