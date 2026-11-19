import java.util.Properties
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

// =====================================================================================
// SalonLink — app module build file
// -------------------------------------------------------------------------------------
// Note for reviewers: with AGP 9 the Android plugin compiles Kotlin itself ("built-in
// Kotlin"), so the separate `org.jetbrains.kotlin.android` plugin is no longer applied.
// =====================================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)        // Jetpack Compose compiler
    alias(libs.plugins.kotlin.serialization)  // @Serializable navigation routes
    alias(libs.plugins.ksp)                   // Annotation processing for Hilt
    alias(libs.plugins.hilt)                  // Dependency injection
    alias(libs.plugins.google.services)       // Reads app/google-services.json (Firebase config)
    alias(libs.plugins.firebase.appdistribution) // Uploads test builds to testers (see docs/DISTRIBUTION.md)
}

// WHY versionCode comes from git: each commit gets a higher number automatically, so testers'
// phones recognise every new build as an update. Falls back to 1 outside a git checkout.
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)

// Release notes shown to testers: the latest commit's subject line.
val gitLastCommitSubject: String = providers.exec {
    commandLine("git", "log", "-1", "--pretty=%s")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("")

android {
    namespace = "com.group12.salonlink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.group12.salonlink"   // Must match the app registered in Firebase
        minSdk = 26                               // Android 8.0 — proposal NFR-08
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "0.1.$gitCommitCount"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: read from keystore.properties in the project root (git-ignored).
    // Without that file the release APK is built unsigned, so the build never needs secrets.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties()
        keystoreFile.inputStream().use { props.load(it) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            // Developer switch: build with `-Psalonlink.useEmulators=true` to talk to the LOCAL
            // Firebase emulators (Auth + Firestore) instead of the real project. Safe testing with
            // throwaway accounts. Off by default, and always off in release builds.
            val useEmulators = providers.gradleProperty("salonlink.useEmulators").orNull == "true"
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useEmulators.toString())

            // Firebase App Distribution: `./gradlew assembleDebug appDistributionUploadDebug`.
            // Auth comes from `firebase login` (no key file in the repo). Testers are managed as a
            // group in the Firebase console; override with -Psalonlink.testerGroups=a,b.
            firebaseAppDistribution {
                artifactType = "APK"
                groups = providers.gradleProperty("salonlink.testerGroups").getOrElse("testers")
                releaseNotes = gitLastCommitSubject
            }
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            signingConfig = releaseSigning
            // Shrink + obfuscate the release APK (smaller download, harder to reverse-engineer).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // WHY: Sinhala and Tamil cover the customer app (values-si, values-ta). Staff and owner
        // screens are deliberately English-only for now, so their strings fall back to English.
        disable += "MissingTranslation"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // --- AndroidX & Compose ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)

    // --- Dependency injection ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // --- Firebase (Spark plan: Auth + Firestore only) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)   // lets us `await()` Firebase Tasks

    // --- Images: barber portraits and portfolio photos ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // --- Reminders (WorkManager + Hilt workers) ---
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // --- Unit tests (run on the JVM, no device needed) ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // --- Instrumented / UI tests (run on an emulator or device) ---
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // --- Debug-only tooling (Compose previews / layout inspector) ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
