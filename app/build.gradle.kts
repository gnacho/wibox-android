import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Credenciales locales del timbre: nunca en el repo. Se leen de local.properties
// (ignorado por git) o de variables de entorno, y si faltan quedan vacias.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localOrEnv(property: String, env: String): String =
    localProps.getProperty(property) ?: System.getenv(env) ?: ""

val wiboxAccount = localOrEnv("wibox.account", "WIBOX_ACCOUNT")
val wiboxPassword = localOrEnv("wibox.password", "WIBOX_PASSWORD")
val wiboxClientId = localOrEnv("wibox.clientId", "WIBOX_CLIENT_ID")
val wiboxUnlockPassword = localOrEnv("wibox.unlockPassword", "WIBOX_UNLOCK_PASSWORD")
val wiboxApiToken = localOrEnv("wibox.apiToken", "WIBOX_API_TOKEN")
val wiboxApiPort = localOrEnv("wibox.apiPort", "WIBOX_API_PORT").ifEmpty { "8087" }

android {
    namespace = "dev.nacho.wibox"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.nacho.wibox"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WIBOX_ACCOUNT", "\"$wiboxAccount\"")
        buildConfigField("String", "WIBOX_PASSWORD", "\"$wiboxPassword\"")
        buildConfigField("String", "WIBOX_CLIENT_ID", "\"$wiboxClientId\"")
        buildConfigField("String", "WIBOX_UNLOCK_PASSWORD", "\"$wiboxUnlockPassword\"")
        buildConfigField("String", "WIBOX_API_TOKEN", "\"$wiboxApiToken\"")
        buildConfigField("int", "WIBOX_API_PORT", wiboxApiPort)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:cloud"))
    implementation(project(":external-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
