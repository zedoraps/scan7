import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// ---- Versioning (docs/RELEASE.md) ------------------------------------------------------------
// versionCode: -Pscan7.versionCode=N, else the commit count of HEAD (grows with every commit on the
// release branch; needs a full clone, not a shallow one), else 1. versionName: scan7.versionName
// in gradle.properties (bump it per release), overridable with -Pscan7.versionName=...
val gitCommitCount: Int? = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull()
val scan7VersionCode: Int = providers.gradleProperty("scan7.versionCode").orNull?.toInt() ?: gitCommitCount ?: 1
val scan7VersionName: String = providers.gradleProperty("scan7.versionName").orNull ?: "0.0.0-dev"

// Public source repository (AGPL-3.0: the app links to its source) and the privacy policy page.
val scan7SourceUrl: String = providers.gradleProperty("scan7.sourceUrl").get()
val scan7PrivacyUrl: String = providers.gradleProperty("scan7.privacyUrl").get()

// ---- Release signing (docs/RELEASE.md) --------------------------------------------------------
// The upload key never lives in the repo: set these in ~/.gradle/gradle.properties or as
// environment variables. Without all four, release builds are left unsigned.
fun secret(property: String, env: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull?.takeIf { it.isNotBlank() }
val uploadStoreFile = secret("scan7.upload.storeFile", "SCAN7_UPLOAD_STORE_FILE")
val uploadStorePassword = secret("scan7.upload.storePassword", "SCAN7_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = secret("scan7.upload.keyAlias", "SCAN7_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = secret("scan7.upload.keyPassword", "SCAN7_UPLOAD_KEY_PASSWORD")
val hasUploadKey = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { it != null }

// ---- Multiplayer flavors (docs/MULTI_DEVICE.md §6.4) -----------------------------------------
// Opt-in: -Pscan7.flavors=true adds `foss` (default, no Play services) and `play` (Google Nearby
// Connections, proprietary). Without it the build stays flavorless and Play-free, so every
// existing task (assembleDebug, installDebug, testDebugUnitTest, …) keeps its name.
val scan7Flavors: Boolean = providers.gradleProperty("scan7.flavors").orNull == "true"

android {
    namespace = "net.moesli.scan7"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "net.moesli.scan7"
        minSdk = 29
        targetSdk = 37
        versionCode = scan7VersionCode
        versionName = scan7VersionName
        buildConfigField("String", "SOURCE_URL", "\"$scan7SourceUrl\"")
        buildConfigField("String", "PRIVACY_URL", "\"$scan7PrivacyUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Parallel agents install side by side on one emulator: ./gradlew -Pscan7.idSuffix=.b installDebug
            providers.gradleProperty("scan7.idSuffix").orNull?.let { applicationIdSuffix = it }
        }
        release {
            // Same for testing a release build on the shared emulator (-Pscan7.idSuffix=.p assembleRelease).
            providers.gradleProperty("scan7.idSuffix").orNull?.let { applicationIdSuffix = it }
            // Signed with the upload key when it is configured (see above), else unsigned.
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    if (scan7Flavors) {
        flavorDimensions += "distribution"
        productFlavors {
            create("foss") {
                dimension = "distribution"
                isDefault = true
            }
            create("play") {
                dimension = "distribution"
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
    if (scan7Flavors) add("playImplementation", libs.play.services.nearby)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---- Detector model (docs/RELEASE.md "Model") -------------------------------------------------
// assets/model.tflite is not in git (~10 MB per version). `./gradlew downloadModel` fetches the
// released model named in app/model.properties (a GitHub release asset) and checks its SHA-256;
// alternatively export your own with ml/scripts/export.py.
val modelProps = Properties().apply { rootProject.file("model.properties").inputStream().use(::load) }
val modelAssetFile: RegularFile = layout.projectDirectory.file("src/main/assets/model.tflite")

abstract class DownloadModel : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputFile abstract val target: RegularFileProperty

    @TaskAction
    fun download() {
        val out = target.get().asFile
        val tmp = File(out.parentFile, out.name + ".part")
        logger.lifecycle("Downloading ${url.get()}")
        URI(url.get()).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
        if (!actual.equals(sha256.get(), ignoreCase = true)) {
            tmp.delete()
            throw GradleException("model.tflite SHA-256 mismatch: expected ${sha256.get()}, got $actual")
        }
        out.delete()
        check(tmp.renameTo(out)) { "Could not move $tmp to $out" }
        logger.lifecycle("Installed ${out.path} (SHA-256 ok)")
    }
}

tasks.register<DownloadModel>("downloadModel") {
    description = "Downloads the released detector model (app/model.properties) into assets/model.tflite."
    group = "scan7"
    url = providers.gradleProperty("scan7.modelUrl").orElse(modelProps.getProperty("model.url"))
    sha256 = providers.gradleProperty("scan7.modelSha256").orElse(modelProps.getProperty("model.sha256"))
    target = modelAssetFile
}

// Fail early with a hint instead of building an APK whose camera screens can't detect anything.
val checkModelAsset = tasks.register("checkModelAsset") {
    val modelAsset = modelAssetFile.asFile
    description = "Checks that the detector model (assets/model.tflite) is present."
    inputs.property("modelPresent", modelAsset.exists())
    doLast {
        if (!modelAsset.exists()) {
            throw GradleException(
                "Missing app/src/main/assets/model.tflite (not in git). Download the released model: " +
                    "cd app && ./gradlew downloadModel  (URL + SHA-256 in app/model.properties), " +
                    "or export your own: cd ml && uv run scripts/export.py runs/<run>/weights/best.pt",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkModelAsset) }

// ---- License texts for the in-app About screen ------------------------------------------------
// Copies the repo's LICENSE (AGPL-3.0) and docs/third_party/*.txt into generated assets
// (licenses/<id>.txt, id = lower-case file stem; LICENSE -> agpl-3.0), so the texts have one source.
abstract class CopyLicenseTexts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val license: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val thirdParty: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        license.get().asFile.copyTo(dir.resolve("agpl-3.0.txt"))
        thirdParty.get().asFile.listFiles { f -> f.extension == "txt" }.orEmpty().forEach { f ->
            f.copyTo(dir.resolve(f.nameWithoutExtension.lowercase() + ".txt"))
        }
    }
}

val copyLicenseTexts = tasks.register<CopyLicenseTexts>("copyLicenseTexts") {
    license = rootProject.layout.projectDirectory.file("../LICENSE")
    thirdParty = rootProject.layout.projectDirectory.dir("../docs/third_party")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicenseTexts, CopyLicenseTexts::outputDir)
        // Flavorless default build = foss: no radio (src/foss holds the Play-free `Radio`).
        if (!scan7Flavors) variant.sources.kotlin?.addStaticSourceDirectory("src/foss/java")
    }
}

// Say so at the end of every unsigned release build (also when the configuration cache is reused).
if (!hasUploadKey) {
    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
        doLast {
            logger.warn("scan7: no upload key configured (scan7.upload.* / SCAN7_UPLOAD_*): $name output is UNSIGNED.")
        }
    }
}
