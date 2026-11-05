import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Local settings and API keys come from the .env file at the repository root (git-ignored),
 * or from environment variables with the same names. See .env.example.
 */
val dotEnv: Map<String, String> = rootProject.file("../.env").takeIf { it.exists() }?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim().removeSurrounding("\"") }
    ?: emptyMap()

fun config(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: dotEnv[name].orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.diegopq.hacktv"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.diegopq.hacktv"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.1"

        // Without a key the banner shows the built-in sample recommendations.
        buildConfigField("String", "TMDB_API_KEY", "\"${config("TMDB_API_KEY")}\"")
        // Two-letter country for streaming availability; empty means the TV's own country.
        buildConfigField("String", "TMDB_REGION", "\"${config("TMDB_REGION")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal builds are signed with the local debug key so they install over adb.
            // Replace with a real signing config before publishing a release.
            signingConfig = signingConfigs.getByName("debug")
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
