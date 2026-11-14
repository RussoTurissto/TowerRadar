plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.russoturissto.towerradar"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.russoturissto.towerradar"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "0.27.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
