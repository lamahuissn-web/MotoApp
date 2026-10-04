import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
        versionCode = 1
        versionName = "0.1.0"

        // The office reaches the panel through the Cloudflare tunnel, and the phone reaches the same
        // one: one host, one door, no second network to keep alive. A build that pointed somewhere
        // else would need the address compiled in, which is why it is a build field and not a screen.
        buildConfigField("String", "API_BASE_URL", "\"https://gen.ispnet.online/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json is part of the Android platform, but a JVM unit test runs against a stub of it that
    // throws. The real jar on the TEST classpath is what lets the parsing above be tested at all.
    testImplementation(libs.json)
}
