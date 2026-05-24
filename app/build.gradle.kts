plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pos.mzxqwe"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.play.services.code.scanner)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.windowsizeclass)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.play.services.auth)
  implementation(libs.retrofit)
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.vico.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

val buildDirFile = project.layout.buildDirectory.get().asFile
val rootDirFile = rootDir

tasks.register("copyApkToRoot") {
    dependsOn("assembleDebug")
    val src = File(buildDirFile, "outputs/apk/debug/app-debug.apk")
    val destRoot = File(rootDirFile, "Kasirku-Debug.apk")
    val destApp = File(File(rootDirFile, "app"), "Kasirku-Debug.apk")
    val destBuildOutputs = File(rootDirFile, ".build-outputs/app-debug.apk")
    val destVisibleDir = File(rootDirFile, "Download_APK")
    val destVisible = File(destVisibleDir, "Kasirku-Debug.apk")
    val systemBuildOutputsApk = File("/.build-outputs/app-debug.apk")
    val destSystemVisible = File(destVisibleDir, "app-debug.apk")
    
    doLast {
        if (!destVisibleDir.exists()) {
            destVisibleDir.mkdirs()
        }
        
        if (systemBuildOutputsApk.exists()) {
            val systemSizeMb = systemBuildOutputsApk.length() / (1024.0 * 1024.0)
            println("=== SYSTEM BUILD OUTPUTS APK FOUND: " + systemBuildOutputsApk.length() + " bytes (" + systemSizeMb + " MB) ===")
            systemBuildOutputsApk.copyTo(destSystemVisible, overwrite = true)
            println("=== COPIED SYSTEM BUILD OUTPUTS TO: " + destSystemVisible.absolutePath + " (" + (destSystemVisible.length() / (1024.0 * 1024.0)) + " MB) ===")
        } else {
            println("=== SYSTEM BUILD OUTPUTS APK NOT FOUND AT /.build-outputs/app-debug.apk ===")
        }
        
        if (destBuildOutputs.exists()) {
            val sizeMb = destBuildOutputs.length() / (1024.0 * 1024.0)
            println("=== BEFORE: BUILD OUTPUTS APK SIZE: " + destBuildOutputs.length() + " bytes (" + sizeMb + " MB) ===")
        } else {
            println("=== BUILD OUTPUTS APK NOT FOUND BEFORE COPY ===")
        }
        if (src.exists()) {
            src.copyTo(destRoot, overwrite = true)
            src.copyTo(destApp, overwrite = true)
            src.copyTo(destBuildOutputs, overwrite = true)
            src.copyTo(destVisible, overwrite = true)
            val srcSizeMb = src.length() / (1024.0 * 1024.0)
            val destSizeMb = destRoot.length() / (1024.0 * 1024.0)
            val buildOutputsSizeMb = destBuildOutputs.length() / (1024.0 * 1024.0)
            val visibleSizeMb = destVisible.length() / (1024.0 * 1024.0)
            println("=== APK COPIED SUCCESSFULLY ===")
            println("Source: " + src.absolutePath + " (" + srcSizeMb + " MB)")
            println("Root: " + destRoot.absolutePath + " (" + destSizeMb + " MB)")
            println("App Dir: " + destApp.absolutePath)
            println("Build Outputs Destination: " + destBuildOutputs.absolutePath + " (" + buildOutputsSizeMb + " MB)")
            println("Visible Destination: " + destVisible.absolutePath + " (" + visibleSizeMb + " MB)")
        } else {
            throw GradleException("Source APK not found at " + src.absolutePath)
        }
    }
}


