import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0-dev"
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

// Identyfikatory klientów OAuth (logowanie przez Google / Microsoft). Puste = tylko hasła aplikacji.
fun config(name: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name).orEmpty()
val googleClientId = config("GOOGLE_CLIENT_ID")
val microsoftClientId = config("MICROSOFT_CLIENT_ID")
// Przekierowanie Google dla aplikacji natywnych: odwrócony identyfikator klienta.
val googleScheme = if (googleClientId.isNotEmpty()) {
    "com.googleusercontent.apps." + googleClientId.removeSuffix(".apps.googleusercontent.com")
} else {
    "com.blacksingularities.openmail.google"
}

android {
    namespace = "com.blacksingularities.openmail"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blacksingularities.openmail"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_REPO", "\"BlackSingularities/OpenMail\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        buildConfigField("String", "GOOGLE_REDIRECT_SCHEME", "\"$googleScheme\"")
        manifestPlaceholders["googleRedirectScheme"] = googleScheme
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null && file(keystorePath).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/NOTICE.md",
            "/META-INF/mailcap",
            "/META-INF/mimetypes.default",
            "/META-INF/javamail.*",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Synchronizacja w tle i powiadomienia o nowej poczcie.
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // Treść HTML wiadomości: ciemny motyw w WebView.
    implementation("androidx.webkit:webkit:1.12.1")
    // Logowanie OAuth w karcie przeglądarki.
    implementation("androidx.browser:browser:1.8.0")
    // IMAP / SMTP (Jakarta Mail dla Androida, EPL 2.0 / GPL+CE).
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    // Oczyszczanie HTML i podgląd tekstu (MIT).
    implementation("org.jsoup:jsoup:1.18.3")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
