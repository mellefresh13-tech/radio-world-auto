import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val appVersionProperties = Properties().apply {
    val versionFile = rootProject.file("../version.properties")
    if (!versionFile.isFile) {
        throw GradleException("Missing version.properties at repository root")
    }
    versionFile.inputStream().use { load(it) }
}
val appVersionCode = appVersionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: throw GradleException("VERSION_CODE in version.properties must be an integer")
val appVersionName = appVersionProperties.getProperty("VERSION_NAME")
    ?.takeIf { it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) }
    ?: throw GradleException("VERSION_NAME in version.properties must use MAJOR.MINOR.PATCH format")

android {
    namespace = "com.mellefresh13.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mellefresh13.radio"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("int", "APP_VERSION_CODE", appVersionCode.toString())
        buildConfigField("long", "BUILD_TIME_EPOCH_MS", "${System.currentTimeMillis()}L")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}


dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.github.murgupluoglu:flagkit-android:1.2.0")
    // FlagKit brings an old Material Components version transitively; pin a current version
    // to avoid Android 15 edge-to-edge calls from the bundled library.
    implementation("com.google.android.material:material:1.14.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    testImplementation("junit:junit:4.13.2")
}
