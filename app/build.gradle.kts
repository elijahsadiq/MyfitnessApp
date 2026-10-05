plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.elijah.fitness"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.elijah.fitness"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Two build flavors on the `env` dimension. BASE_URL is exposed through
    // BuildConfig; NetworkModule reads it at runtime. The `local` flavor
    // targets the docker-compose stack from the emulator (10.0.2.2 is the
    // host's loopback as seen from an Android emulator).
    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            buildConfigField(
                "String",
                "BASE_URL",
                "\"http://localhost:8080/\""
            )
        }
        create("cloud") {
            dimension = "env"
            // Placeholder — replaced once a real deployment exists. Kept as
            // HTTPS so the release network security config stays HTTPS-only.
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://fitness-api.example.com/\""
            )
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    // Phase 8a — geolocation.
    //   play-services-location: FusedLocationProviderClient.
    //   osmdroid-android: OpenStreetMap tile MapView — no API key, so the
    //     marker's machine renders it exactly as mine does.
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
