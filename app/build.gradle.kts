plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.hotelremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.hotelremote"
        minSdk = 23
        targetSdk = 36
        versionCode = 8
        versionName = "1.6.1"
    }

    androidResources {
        // res/xml/locales_config.xml wird von Hand gepflegt (Tags zh-CN und id bewusst gewählt);
        // die automatische Variante würde aus den Ordnernamen ableiten.
        generateLocaleConfig = false
        // Nur die 11 App-Sprachen ins APK; AppCompat und Material bringen sonst ~90 Sprachen mit,
        // die der Play Store als „unterstützt“ anzeigen würde.
        localeFilters += listOf("de", "en", "zh-rCN", "hi", "es", "fr", "ar", "bn", "pt", "ru", "in")
    }

    val storeFile = providers.gradleProperty("HOTELREMOTE_STORE_FILE").orNull
    val storePassword = providers.gradleProperty("HOTELREMOTE_STORE_PASSWORD").orNull
    val keyAlias = providers.gradleProperty("HOTELREMOTE_KEY_ALIAS").orNull
    val keyPassword = providers.gradleProperty("HOTELREMOTE_KEY_PASSWORD").orNull
    val canSign = storeFile != null && storePassword != null && keyAlias != null && keyPassword != null

    signingConfigs {
        if (canSign) {
            create("release") {
                this.storeFile = file(storeFile!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("WARNUNG: HOTELREMOTE_* Properties fehlen, Release wird unsigniert gebaut.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Store-Screenshots (Test StoreScreenshots) nur mit -Pscreenshots, Ausgabe nach docs/play-store/.
        val screenshots = providers.gradleProperty("screenshots").isPresent
        val screenshotsDir = rootProject.file("docs/play-store").path
        unitTests.all { test ->
            if (screenshots) {
                test.systemProperty("hotelremote.screenshots.dir", screenshotsDir)
                test.outputs.upToDateWhen { false }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
