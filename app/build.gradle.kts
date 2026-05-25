plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.hilt.plugin)
    alias(libs.plugins.kotlin.serialization)
}

fun gradleOrEnv(name: String, fallback: String = ""): String {
    return providers.environmentVariable(name)
        .orElse(providers.gradleProperty(name))
        .orElse(fallback)
        .get()
}

fun buildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.shubham.agentui"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.shubham.agentui"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "boolean",
            "A2UI_USE_OCI_AGENT",
            gradleOrEnv("A2UI_USE_OCI_AGENT", "true").toBooleanStrictOrNull()?.toString() ?: "true"
        )
        buildConfigField(
            "String",
            "A2UI_OCI_OPENAI_API_KEY",
            buildConfigString(gradleOrEnv("A2UI_OCI_OPENAI_API_KEY"))
        )
        buildConfigField(
            "String",
            "A2UI_OCI_LITELLM_BASE_URL",
            buildConfigString(
                gradleOrEnv(
                    "A2UI_OCI_LITELLM_BASE_URL",
                    "https://code-internal.aiservice.us-chicago-1.oci.oraclecloud.com/20250206/app/litellm"
                )
            )
        )
        buildConfigField(
            "String",
            "A2UI_OCI_LITELLM_MODEL",
            buildConfigString(gradleOrEnv("A2UI_OCI_LITELLM_MODEL", "gpt-5.5"))
        )
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
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Local persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)

    // Networking
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
