import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun secret(key: String): String =
    (localProperties.getProperty(key) ?: System.getenv(key) ?: "")

val supabaseUrl = secret("SUPABASE_URL")
val supabaseAnonKey = secret("SUPABASE_ANON_KEY")
val googleWebClientId = secret("GOOGLE_WEB_CLIENT_ID")

// Release signing (optional, FASE 7). keystore.properties is gitignored; see keystore.properties.example.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun keystoreValue(key: String): String? =
    keystoreProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

// storeFile may be absolute or relative to the project root directory.
val releaseStoreFile: File? = keystoreValue("storeFile")?.let { rootProject.file(it) }
val hasReleaseSigning: Boolean = releaseStoreFile?.isFile == true &&
    listOf("storePassword", "keyAlias", "keyPassword").all { keystoreValue(it) != null }

android {
    namespace = "com.lucho314.spotter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lucho314.spotter"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreValue("storePassword")
                keyAlias = keystoreValue("keyAlias")
                keyPassword = keystoreValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Baseline ONLY for issues originating in third-party libraries, never app code (MIGRATION_PLAN
        // FASE 7). Used only if the file exists, so a missing baseline never breaks or rewrites the build.
        val lintBaseline = file("lint-baseline.xml")
        if (lintBaseline.exists()) {
            baseline = lintBaseline
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)

    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.tink.android)

    // coil3 3.6.x resolves to compose-foundation 1.12.0, which requires compileSdk 37 (not
    // installed here, see MIGRATION_PLAN.md section 4). coil 3.5.0 instead resolves to
    // foundation 1.11.1, which is compatible with compileSdk 36; sh.calvin.reorderable 3.1.0 is
    // fine as-is. Neither is used by phase 1 code yet (media, image loading, drag & drop are
    // later phases), but both are kept enabled so later phases don't have to re-resolve this.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.reorderable)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
}

val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    doLast {
        check(supabaseUrl.isNotBlank()) { "SUPABASE_URL is empty. Set it in local.properties or the environment." }
        check(supabaseUrl.startsWith("https://")) { "SUPABASE_URL must start with https://." }
        check(supabaseAnonKey.isNotBlank()) { "SUPABASE_ANON_KEY is empty. Set it in local.properties or the environment." }
        if (keystorePropertiesFile.exists()) {
            check(hasReleaseSigning) {
                "keystore.properties is incomplete (storeFile, storePassword, keyAlias, keyPassword) or storeFile " +
                    "does not point to an existing file (relative paths resolve from the project root)."
            }
        } else {
            logger.warn("keystore.properties not found: the release APK will be UNSIGNED (see keystore.properties.example).")
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseConfig)
}
