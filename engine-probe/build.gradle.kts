buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") } }
plugins {
    id("com.android.application") version "9.1.1"
}
android {
    namespace = "com.weiktube.probe"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.weiktube.probe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-test"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Direct APK distribution: compress native libraries for a smaller download.
    packaging { jniLibs { useLegacyPackaging = true } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.mozilla.geckoview:geckoview:159.0.20261022150525")
    implementation("androidx.activity:activity-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.register("exportTestApk") {
    dependsOn("assembleDebug")
    val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug")
    val exported = rootProject.layout.projectDirectory.file("../Weiktube-Test.apk")
    inputs.files(apkDirectory.map { it.asFileTree.matching { include("*.apk") } })
    outputs.file(exported)
    doLast {
        val candidates = apkDirectory.get().asFile.listFiles { file -> file.extension == "apk" }.orEmpty()
        check(candidates.size == 1) { "Expected exactly one universal test APK" }
        candidates.single().copyTo(exported.asFile, overwrite = true)
    }
}
