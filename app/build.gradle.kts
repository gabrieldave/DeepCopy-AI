import java.util.Properties
import kotlin.math.max

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.blackeye.klipnote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blackeye.klipnote"
        minSdk = 26
        targetSdk = 35

        // Fuente de verdad local; CI nunca baja de este piso.
        val localVersionCode = 108
        val localVersionName = "1.2.0"
        val ciCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it }
        versionCode = max(localVersionCode, ciCode ?: 0)
        versionName = if (ciCode != null && ciCode > localVersionCode) {
            "1.2.${ciCode - 100}"
        } else {
            localVersionName
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // APK comercial: cero llaves quemadas. BYOK solo en almacén cifrado del dispositivo.
        buildConfigField("String", "DEFAULT_DEEPGRAM_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            val ciPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            if (!ciPassword.isNullOrBlank()) {
                val ciPath = System.getenv("ANDROID_KEYSTORE_PATH")
                require(!ciPath.isNullOrBlank()) {
                    "ANDROID_KEYSTORE_PATH requerido cuando ANDROID_KEYSTORE_PASSWORD está definido"
                }
                storeFile = file(ciPath)
                storePassword = ciPassword
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = ciPassword
            } else {
                val propsFile = file(
                    "${System.getProperty("user.home")}/.blackeye-keys/android/keystore.properties"
                )
                if (propsFile.exists()) {
                    val props = Properties()
                    propsFile.inputStream().use { props.load(it) }
                    storeFile = file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
            // Tampoco quemar llaves en debug.
            buildConfigField("String", "DEFAULT_DEEPGRAM_KEY", "\"\"")
        }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.annotation:annotation:1.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
