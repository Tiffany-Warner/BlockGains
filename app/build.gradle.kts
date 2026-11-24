import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tiffles.blockgains"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "com.tiffles.blockgains"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 124
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes only from the owner's user-level ~/.gradle/gradle.properties, never from
    // a file in this repo (README → Building). Without all four values the release APK is unsigned.
    val signing = releaseSigningProperties()
    if (signing != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signing.getValue("BLOCKGAINS_RELEASE_STORE_FILE"))
                storePassword = signing.getValue("BLOCKGAINS_RELEASE_STORE_PASSWORD")
                keyAlias = signing.getValue("BLOCKGAINS_RELEASE_KEY_ALIAS")
                keyPassword = signing.getValue("BLOCKGAINS_RELEASE_KEY_PASSWORD")
            }
        }
    } else {
        logger.lifecycle("Release signing not configured (see README → Building); release APK will be unsigned.")
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signing?.let { signingConfig = signingConfigs.getByName("release") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        // Exposes the merged manifest to unit tests (NoInternetPermissionTest).
        unitTests.isIncludeAndroidResources = true
        // Instrumented tests run on this Gradle-created emulator, never on the owner's own devices:
        // ./gradlew pixel6Api36DebugAndroidTest (ai-guidelines → Workflow).
        managedDevices {
            localDevices {
                create("pixel6Api36") {
                    device = "Pixel 6"
                    apiLevel = 36 // matches targetSdk
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64" // the ATD image has no arm64 translation; AGP 10 changes the default
                }
            }
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // targetSdk is pinned to 36 by coding-standards → Toolchain; raise it deliberately, not via lint.
        disable += "OldTargetApi"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * The four release-signing values from the Gradle user home's gradle.properties only (not the
 * project's gradle.properties, -P flags or environment), so they can't come from the repo.
 * Null unless all four are set.
 */
fun releaseSigningProperties(): Map<String, String>? {
    val keys = listOf(
        "BLOCKGAINS_RELEASE_STORE_FILE",
        "BLOCKGAINS_RELEASE_STORE_PASSWORD",
        "BLOCKGAINS_RELEASE_KEY_ALIAS",
        "BLOCKGAINS_RELEASE_KEY_PASSWORD",
    )
    val file = gradle.gradleUserHomeDir.resolve("gradle.properties")
    if (!file.isFile) return null
    val props = Properties().apply { file.inputStream().use { load(it) } }
    val values = keys.associateWith { props.getProperty(it)?.trim().orEmpty() }
    return values.takeIf { map -> map.values.all { it.isNotEmpty() } }
}
