plugins {
    id("com.android.application")
}

android {
    namespace = "com.mellefresh13.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mellefresh13.radio"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.1"
        val apiUrl = project.findProperty("radioApiUrl")?.toString() ?: "https://radio-world-auto-production.up.railway.app/"
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.register<org.gradle.api.tasks.Exec>("applyPlayerMetadataUiPatch") {
    workingDir(rootProject.projectDir.parentFile)
    commandLine("python3", "scripts/apply_player_metadata_ui_patch.py")
}

tasks.register<org.gradle.api.tasks.Exec>("applyFinalMetadataFix") {
    workingDir(rootProject.projectDir.parentFile)
    commandLine("python3", "tools/fix_metadata.py")
    dependsOn("applyPlayerMetadataUiPatch")
}

tasks.named("preBuild") {
    dependsOn("applyFinalMetadataFix")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    testImplementation("junit:junit:4.13.2")
}
