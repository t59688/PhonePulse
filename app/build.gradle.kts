plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

fun deriveVersionCode(versionName: String): Int {
  val match =
    Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$""").matchEntire(versionName)
      ?: error("versionName must be X.Y.Z or X.Y.Z-beta.N, got: $versionName")
  val major = match.groupValues[1].toInt()
  val minor = match.groupValues[2].toInt()
  val patch = match.groupValues[3].toInt()
  val beta = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
  require(minor in 0..99 && patch in 0..99) { "minor/patch out of range: $versionName" }
  if (beta != null) require(beta in 1..998) { "beta number out of range: $versionName" }
  val core = major * 10000 + minor * 100 + patch
  return core * 1000 + (beta ?: 999)
}

// Single source of truth: root VERSION file (CI may override via VERSION_NAME / VERSION_CODE).
val versionFileVersion =
  rootProject.file("VERSION").takeIf { it.isFile }?.readText()?.trim().orEmpty()
val appVersionName = System.getenv("VERSION_NAME") ?: versionFileVersion.ifEmpty { "1.0.0" }
val appVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: deriveVersionCode(appVersionName)

val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "my-upload-key.jks"
val releaseStorePassword = System.getenv("STORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val releaseKeystoreFile =
  rootProject.file(releaseKeystorePath).takeIf { it.isAbsolute || it.exists() }
    ?: file(releaseKeystorePath)
val hasReleaseSigning =
  releaseKeystoreFile.isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val isReleaseTask =
  gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (isReleaseTask && !hasReleaseSigning) {
  error(
    "Release signing is not configured. Provide KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD " +
      "(and optional KEY_ALIAS)."
  )
}

android {
  namespace = "com.aizeek.phonepulse"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aizeek.phonepulse"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      val debugKeystore = file("${rootDir}/debug.keystore")
      if (debugKeystore.isFile) {
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      val debugSigning = signingConfigs.getByName("debugConfig")
      if (debugSigning.storeFile?.isFile == true) {
        signingConfig = debugSigning
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
