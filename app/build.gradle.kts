plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.f3.workouttimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.f3.workouttimer"
        minSdk = 26
        targetSdk = 35
        // Bump both of these and update.json together when publishing a build.
        versionCode = 2
        versionName = "1.1"
    }

    // The signing key comes from the environment — CI decodes it from an
    // encrypted secret, and it is never committed. See README > Signing.
    val signingStore = System.getenv("SIGNING_STORE_FILE")
    val hasSigningKey = !signingStore.isNullOrBlank() && file(signingStore).exists()

    signingConfigs {
        if (hasSigningKey) {
            create("shared") {
                storeFile = file(signingStore!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "f3timer"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Falling back to the debug key keeps local release builds runnable;
            // only a build with the shared key is fit to hand round, since
            // Android refuses an update whose signature changed.
            signingConfig = if (hasSigningKey) {
                signingConfigs.getByName("shared")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("shared")
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
}
