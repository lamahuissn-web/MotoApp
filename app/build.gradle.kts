import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Imported rather than written as java.util.Properties: inside a Gradle script `java` is the Java
// plugin's extension, so the fully qualified name resolves to the extension's members, not the JDK's.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The key this office signs its own app with, read from OUTSIDE the working tree.
 *
 * A keystore in the repository is a keystore anybody who can clone can sign with, and this app's
 * signature is the only thing that lets an update be installed over an existing one. The path comes
 * from an environment variable, or from ~/.motoapp/keystore.properties — never from the project.
 */
val keystorePropertiesFile: File = System.getenv("MOTOAPP_KEYSTORE_PROPERTIES")
    ?.let { file(it) }
    ?: File(System.getProperty("user.home"), ".motoapp/keystore.properties")

val keystoreProperties: Properties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { stream -> keystoreProperties.load(stream) }
}

/** Set inside the android block; the release guard below reads it. */
var hasReleaseSigning = false

android {
    namespace = "com.lamahuissn.motoapp"

    // Compose 1.12 and core-ktx 1.19 are compiled against API 37, so the app must be too — while
    // TARGET stays 36: the office's phones do not need the newest runtime behaviour, and a lower
    // target is a smaller surface to have verified.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lamahuissn.motoapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.2"

        // The office reaches the panel through the Cloudflare tunnel, and the phone reaches the same
        // one: one host, one door, no second network to keep alive. A build that pointed somewhere
        // else would need the address compiled in, which is why it is a build field and not a screen.
        buildConfigField("String", "API_BASE_URL", "\"https://gen.ispnet.online/\"")
    }

    if (!keystoreProperties.isEmpty) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }

        hasReleaseSigning = true
    }

    buildTypes {
        debug {
            // Left unminified on purpose: a debug build is for reading stack traces, and the office
            // keeps one installed only while something is being chased.
            isMinifyEnabled = false
        }

        release {
            // R8 and shrunk resources are most of the difference between the app feeling slow and
            // feeling native: a debug Compose build carries instrumentation through every frame, and
            // the baseline profiles the Compose libraries ship are only applied to an optimized build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// A release artifact that is unsigned — or signed with a key other than this office's — must never be
// delivered quietly, so a release task says what is missing instead.
tasks.matching { it.name.contains("Release") }.configureEach {
    doFirst {
        require(hasReleaseSigning) {
            "لا يوجد مفتاح إصدار: ضع ملف الخصائص في $keystorePropertiesFile (storeFile/storePassword/keyAlias/keyPassword)."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.okhttp)
    // The queue draining while the phone is in the collector's pocket: a system-managed job, not a
    // service we keep alive (spec 010, phase B).
    implementation(libs.androidx.work)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json is part of the Android platform, but a JVM unit test runs against a stub of it that
    // throws. The real jar on the TEST classpath is what lets the parsing above be tested at all.
    testImplementation(libs.json)
}
