import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firebase config is YOUR project's file and is never committed (see .gitignore). Without it the app builds and runs with accounts switched off.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ---- Branding / identity: change these in ONE place (see also res/values/strings.xml: app_name) ----
val appId = "com.utilityhub.app"
val appVersionName = "0.1.0"
val appVersionCode = 1

android {
    namespace = "com.utilityhub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Billing is intentionally unconfigured. Flip to true only after real Play Console products exist.
        buildConfigField("boolean", "BILLING_CONFIGURED", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Release signing is read from environment/gradle properties; nothing secret is stored in the repo.
        create("release") {
            val storePath = providers.gradleProperty("UTILITYHUB_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("UTILITYHUB_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("UTILITYHUB_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("UTILITYHUB_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.gradleProperty("UTILITYHUB_STORE_FILE").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core-logic"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)        // Email/password sign-in only; inactive until google-services.json is added
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.pdfbox.android)   // Apache-2.0: merge / split / page extraction / text extraction
    implementation(libs.zxing.core)       // Apache-2.0: QR / barcode encode + decode (no camera needed)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
