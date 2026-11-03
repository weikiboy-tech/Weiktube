import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.weiktube.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.weiktube.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.2.5-original-home-test"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":home-data"))
    testImplementation("org.json:json:20240303")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.register("exportHomeConnectionApk") {
    dependsOn("assembleDebug")
    val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val exported = rootProject.layout.projectDirectory.file("Weiktube-Home-Test.apk")
    inputs.file(sourceApk)
    outputs.file(exported)
    doLast { sourceApk.get().asFile.copyTo(exported.asFile, overwrite = true) }
}
tasks.register("exportWeiktubeApk") {
    dependsOn("assembleDebug")
    val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val exportedApk = rootProject.layout.projectDirectory.file("Weiktube.apk")
    inputs.file(sourceApk)
    outputs.file(exportedApk)
    doLast { sourceApk.get().asFile.copyTo(exportedApk.asFile, overwrite = true) }
}
