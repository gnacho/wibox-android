plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.nacho.wibox.core.cloud"
    compileSdk = 37
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("standard_out", "standard_error", "failed")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
