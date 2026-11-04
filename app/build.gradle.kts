import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val stableApks = providers.gradleProperty("weiktubeStableApks").map(String::toBoolean).getOrElse(false)
android {
    namespace = "com.weiktube.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.weiktube.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "1.3.11"
        if (!stableApks) ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            // Preserve the signing identity of all previously installed versions.
            // Only the original local key may be used for published updates.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    splits {
        abi {
            isEnable = stableApks
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.register("exportStableApks") {
    dependsOn("assembleRelease")
    doLast {
        check(stableApks) { "Use -PweiktubeStableApks=true for the standalone stable APKs." }
        val destination = rootProject.layout.projectDirectory.dir("dist/stable").asFile
        destination.mkdirs()
        mapOf("arm64-v8a" to "Weiktube.apk", "x86_64" to "Weiktube-x86_64.apk").forEach { (abi, name) ->
            val apk = layout.buildDirectory.file("outputs/apk/release/app-$abi-release.apk").get().asFile
            check(apk.length() in 1..(128L * 1024 * 1024)) { "APK must fit the existing updater download limit: $abi" }
            apk.copyTo(destination.resolve(name), overwrite = true)
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":content-filter"))
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
    testImplementation("org.robolectric:robolectric:4.16.1")
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
