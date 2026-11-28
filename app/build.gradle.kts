import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val baseApplicationId = "com.spectra.app"
val applicationIdOverride = System.getenv("SPECTRA_APPLICATION_ID")?.takeIf { it.isNotBlank() }
val appNameOverride = System.getenv("SPECTRA_APP_NAME")?.takeIf { it.isNotBlank() }
val appVersion =
    rootProject
        .file("VERSION")
        .takeIf { it.exists() }
        ?.readText()
        ?.trim()
        ?.takeIf { it.matches(Regex("\\d+(\\.\\d+){1,4}")) }
        ?: "1.0.0.0.0"
val appVersionCode =
    appVersion
        .split(".")
        .map { it.toInt() }
        .let { parts -> parts + List(5 - parts.size) { 0 } }
        .let { p ->
            1_100_000_000 + p[0].coerceIn(0, 99) * 10_000_000 + p[1].coerceIn(0, 99) * 100_000 +
                p[2].coerceIn(0, 99) * 1_000 + p[3].coerceIn(0, 9) * 100 + p[4].coerceIn(0, 99)
        }
val spectraUpdateRepo =
    System.getenv("GITHUB_REPOSITORY")
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) }
        ?: "sanz-dev-x/spectra"
val buildCommit =
    System.getenv("SPECTRA_BUILD_COMMIT")
        ?.trim()
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
        ?.take(7)
        ?.lowercase()
val debugKeystorePathOverride = System.getenv("SPECTRA_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val debugKeystorePassword = System.getenv("SPECTRA_DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "Sp3ctra-7QmX2026"
val debugKeyAlias = System.getenv("SPECTRA_DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "spectra"
val debugKeyPassword = System.getenv("SPECTRA_DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "Sp3ctra-7QmX2026"
val persistentDebugKeystoreFile = file("keystore/spectra.jks")
val workflowDebugKeystoreFile = debugKeystorePathOverride?.let(::file)

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

// MD5 sertifikat dari signingConfig sebuah build; dipakai AppIntegrityGuard (AndroidTamperingProtection).
fun certMd5(cfg: com.android.build.api.dsl.ApkSigningConfig?): String = runCatching {
    val f = cfg?.storeFile?.takeIf { it.exists() } ?: return@runCatching ""
    val ks = KeyStore.getInstance(f, requireNotNull(cfg.storePassword).toCharArray())
    val cert = ks.getCertificate(requireNotNull(cfg.keyAlias)) ?: return@runCatching ""
    MessageDigest.getInstance("MD5").digest(cert.encoded)
        .joinToString(":") { "%02X".format(it) }
}.getOrDefault("")

android {
    namespace = "com.spectra.app"
    compileSdk = 37

    defaultConfig {
        applicationId = applicationIdOverride ?: baseApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        val baseVersionName = requireNotNull(versionName)
        buildConfigField("String", "BASE_VERSION_NAME", "\"$baseVersionName\"")
        buildConfigField("String", "UPDATE_REPO", "\"$spectraUpdateRepo\"")
        buildCommit?.let { versionName = "$baseVersionName+$it" }
        resValue("string", "app_name", appNameOverride ?: "Spectra")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // LastFM API keys from GitHub Secrets
        val lastFmKey = localProperties.getProperty("LASTFM_API_KEY") ?: System.getenv("LASTFM_API_KEY") ?: ""
        val lastFmSecret = localProperties.getProperty("LASTFM_SECRET") ?: System.getenv("LASTFM_SECRET") ?: ""

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastFmSecret\"")
        val spotifyId = localProperties.getProperty("SPOTIFY_CLIENT_ID") ?: System.getenv("SPOTIFY_CLIENT_ID") ?: ""
        val spotifySecret = localProperties.getProperty("SPOTIFY_CLIENT_SECRET") ?: System.getenv("SPOTIFY_CLIENT_SECRET") ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyId\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"$spotifySecret\"")
        buildConfigField("String", "ARCHITECTURE", "\"arm64-v8a\"")
        buildConfigField("Long", "DISCORD_APP_ID", "1447278780795064401L")
        buildConfigField("String", "TAMPER_SIGNATURES", "\"\"")
    }

    flavorDimensions += listOf("variant")
    productFlavors {
        // FOSS - Updater, but no gcast
        create("foss") {
            dimension = "variant"
            isDefault = true
            buildConfigField("Boolean", "CAST_AVAILABLE", "false")
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "true")
        }

        // GMS - Updater and gcast
        create("gms") {
            dimension = "variant"
            buildConfigField("Boolean", "CAST_AVAILABLE", "true")
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "true")
        }

        // IzzyOnDroid - no gcast, no updater - the ONLY F-droid compliant build
        create("izzy") {
            dimension = "variant"
            buildConfigField("Boolean", "CAST_AVAILABLE", "false")
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "false")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentDebugKeystoreFile
            storePassword = "Sp3ctra-7QmX2026"
            keyAlias = "spectra"
            keyPassword = "Sp3ctra-7QmX2026"
        }
        create("workflowDebug") {
            storeFile = workflowDebugKeystoreFile ?: persistentDebugKeystoreFile
            storePassword = debugKeystorePassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
        }
        create("release") {
            storeFile = persistentDebugKeystoreFile
            storePassword = System.getenv("STORE_PASSWORD") ?: "Sp3ctra-7QmX2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "spectra"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "Sp3ctra-7QmX2026"
        }
        getByName("debug") {
            keyAlias = "spectra"
            keyPassword = "Sp3ctra-7QmX2026"
            storePassword = "Sp3ctra-7QmX2026"
            storeFile = persistentDebugKeystoreFile
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Tanpa applicationIdSuffix: debug dan rilis memakai id yang sama (com.spectra.app),
            // jadi tidak pernah muncul klon com.spectra.app.debug.
            isDebuggable = true
            if (appNameOverride == null) {
                resValue("string", "app_name", "Spectra")
            }
            signingConfig =
                if (workflowDebugKeystoreFile != null) {
                    signingConfigs.getByName("workflowDebug")
                } else {
                    signingConfigs.getByName("persistentDebug")
                }
        }
        create("spectra") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig =
                if (workflowDebugKeystoreFile != null) {
                    signingConfigs.getByName("workflowDebug")
                } else {
                    signingConfigs.getByName("persistentDebug")
                }
            buildConfigField("String", "TAMPER_SIGNATURES", "\"${certMd5(signingConfig as? com.android.build.api.dsl.ApkSigningConfig)}\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        // Lint never gated anything here (abortOnError = false), so the
        // lintVital pass that assembleRelease implicitly triggers was pure
        // build time. Run lint on demand with ./gradlew :app:lintGmsRelease.
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols +=
                listOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libdatastore_shared_counter.so",
                )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}

val cleanLegacyProtoSources = tasks.register<Delete>("cleanLegacyProtoSources") {
    delete(layout.projectDirectory.dir("src/main/java/com/spectra/app/listentogether/proto"))
}

tasks.named("preBuild") {
    dependsOn(cleanLegacyProtoSources)
}

// persistent-debug.keystore is gitignored (*.keystore), so it never exists on a fresh
// CI checkout. The `debug` buildType's signingConfig always points at it (or at
// workflowDebugKeystoreFile when SPECTRA_DEBUG_KEYSTORE_PATH is set) regardless of
// whether the file exists yet - that decision is static, made with no I/O, so it is
// safe under the configuration cache. This task creates the missing keystore file at
// *execution* time (running an external process during configuration is disallowed
// under the configuration cache; doing it in a task's execution phase is fine). Only
// plain File/String values are captured into local vals before doLast, so the task
// action holds no reference to the build script object, which is required for the
// configuration cache to serialize this task.
val ensureDebugKeystore = tasks.register("ensureDebugKeystore") {
    val keystoreFile = workflowDebugKeystoreFile ?: persistentDebugKeystoreFile
    val storePass = if (workflowDebugKeystoreFile != null) debugKeystorePassword else "Sp3ctra-7QmX2026"
    val alias = if (workflowDebugKeystoreFile != null) debugKeyAlias else "spectra"
    val keyPass = if (workflowDebugKeystoreFile != null) debugKeyPassword else "Sp3ctra-7QmX2026"
    val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath

    outputs.file(keystoreFile)
    onlyIf { !keystoreFile.exists() }
    doLast {
        keystoreFile.parentFile?.mkdirs()
        val process = ProcessBuilder(
            keytool,
            "-genkeypair",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", storePass,
            "-alias", alias,
            "-keypass", keyPass,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=Spectra,O=Spectra,C=ID",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Gagal membuat debug keystore (exit $exitCode):\n$output" }
    }
}

tasks.matching { it.name.startsWith("validateSigning") && (it.name.endsWith("Debug") || it.name.endsWith("Spectra")) }.configureEach {
    dependsOn(ensureDebugKeystore)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
        )
        suppressWarnings.set(false)
    }
}

// Android provides org.json as a platform API (/apex/com.android.art/javalib/core-libart.jar).
// The standalone org.json:json artefact bundles an older Apache Harmony copy of JSONArray that
// contains an internal `myArrayList` field absent from the platform class.  Without obfuscation
// R8 inlines against this internal field; at runtime the platform class is resolved instead,
// producing a NoSuchFieldError.  Excluding the artefact globally ensures only the platform
// class is ever referenced.
configurations.configureEach {
    exclude(group = "org.json", module = "json")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.mlkit.selfie.segmentation)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.browser)

    implementation(libs.ucrop)

    implementation(libs.shimmer)
    implementation(libs.backdrop)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    // Google Cast - only included in GMS flavor (not available in F-Droid/FOSS builds)
    "gmsImplementation"(libs.media3.cast)
    "gmsImplementation"(libs.mediarouter)
    "gmsImplementation"(libs.cast.framework)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    ksp(libs.room.compiler)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)

    // Protobuf for message serialization (lite version for Android)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)
}
