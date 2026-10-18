plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "net.moesli.scan7"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "net.moesli.scan7"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Parallel agents install side by side on one emulator: ./gradlew -Pscan7.idSuffix=.b installDebug
            providers.gradleProperty("scan7.idSuffix").orNull?.let { applicationIdSuffix = it }
        }
        release {
            // Same for testing a release build on the shared emulator (-Pscan7.idSuffix=.p assembleRelease).
            providers.gradleProperty("scan7.idSuffix").orNull?.let { applicationIdSuffix = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // CompiledModel memory-maps the model straight from the APK.
        noCompress += "tflite"
        // UI languages (res/xml/locales_config.xml): drops library translations of other locales.
        localeFilters += listOf("en", "de")
    }
    bundle {
        // In-app language switch (ui/text/AppLanguage.kt): every language must be in the install.
        language { enableSplit = false }
    }
    lint {
        // Strings (AGENTS.md "Strings"): severities in lint.xml (missing translations fail lint).
        lintConfig = file("lint.xml")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Exported schemas are committed: they document the DB and enable migration tests.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // For batches C/D (audit #13, #22, #5): ViewModels, training JSON, Navigation 3.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// The detector model is not in git (~10 MB per version): fail early with a hint instead of
// building an APK whose camera screens can't detect anything.
val checkModelAsset by tasks.registering {
    val modelAsset = layout.projectDirectory.file("src/main/assets/model.tflite").asFile
    description = "Checks that the detector model (assets/model.tflite) is present."
    inputs.property("modelPresent", modelAsset.exists())
    doLast {
        if (!modelAsset.exists()) {
            throw GradleException(
                "Missing app/src/main/assets/model.tflite (not in git). Export it first: " +
                    "cd ml && uv run scripts/export.py runs/<run>/weights/best.pt  (run ml/scripts/export.py)",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkModelAsset) }
