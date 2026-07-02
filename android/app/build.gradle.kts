plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

import java.io.File
import java.util.Properties

/** Resolve Maps SDK key: local secrets first, then Gradle -P / gradle.properties (team defaults may be empty). */
fun org.gradle.api.Project.resolveGoogleMapsApiKey(): String {
    fun File.readKey(): String {
        if (!exists()) return ""
        return runCatching {
            Properties().apply { inputStream().use { load(it) } }
                .getProperty("GOOGLE_MAPS_API_KEY")
                ?.trim()
                ?.removeSurrounding("\"")
                ?.removeSurrounding("'")
                .orEmpty()
        }.getOrElse { "" }
    }
    val fromGradleLocal = rootProject.file("gradle.local.properties").readKey()
    val fromLocalProperties = rootProject.file("local.properties").readKey()
    val fromGradle = (findProperty("GOOGLE_MAPS_API_KEY") as String?)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
    return sequenceOf(fromGradleLocal, fromLocalProperties, fromGradle)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

android {
    namespace = "com.example.recallai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.recallai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val deviceBaseUrl = (project.findProperty("RECALLAI_DEVICE_BASE_URL") as String?)
            ?.trim()
            ?.removeSuffix("/")
            ?: "http://192.168.100.2:3001"
        val tunnelBaseUrl = (project.findProperty("RECALLAI_TUNNEL_BASE_URL") as String?)
            ?.trim()
            ?.removeSuffix("/")
            .orEmpty()
        val mapsApiKey = project.resolveGoogleMapsApiKey()
        buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", if (mapsApiKey.isNotBlank()) "true" else "false")
        buildConfigField("String", "EMULATOR_BASE_URL", "\"http://10.0.2.2:3001/\"")
        buildConfigField("String", "DEVICE_BASE_URL", "\"$deviceBaseUrl/\"")
        buildConfigField("String", "BASE_URL", "\"${if (tunnelBaseUrl.isNotBlank()) "$tunnelBaseUrl/" else ""}\"")
        buildConfigField("boolean", "USE_DYNAMIC_BASE_URL", if (tunnelBaseUrl.isNotBlank()) "false" else "true")
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = mapsApiKey
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
        // KAPT doesn't support Kotlin language version 2.0 yet; keep source level at 1.9 for stability.
        languageVersion = "1.9"
        apiVersion = "1.9"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kapt {
    correctErrorTypes = true
}

/**
 * Hilt injects Dagger-specific AP flags into KAPT for every variant. When there are no
 * androidTest/unitTest sources, KAPT still runs and javac warns that no processor consumed
 * those flags. Skip those tasks unless the matching source tree has something to process.
 */
tasks.matching {
    it.name.startsWith("kapt") && (it.name.contains("AndroidTest") || it.name.contains("UnitTest"))
}.configureEach {
    val androidTestTree = name.contains("AndroidTest")
    val base = if (androidTestTree) "src/androidTest" else "src/test"
    onlyIf {
        fileTree(mapOf("dir" to base, "include" to listOf("**/*.kt", "**/*.java"))).files.any { f -> f.isFile }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    val firebaseBom = platform("com.google.firebase:firebase-bom:33.7.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-messaging-ktx")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.compose.material:material-icons-extended")
}