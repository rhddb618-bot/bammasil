import java.util.Properties

// 배포 서명: 저장소 밖 E:/pleos_app/signing/keystore.properties 가 있을 때만 쓴다(키는 커밋하지 않음)
val releaseSigning = rootProject.file("../signing/keystore.properties").takeIf { it.exists() }?.let { f ->
  Properties().apply { f.inputStream().use { load(it) } }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example.pleoscamping"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.example.pleoscamping"
    minSdk = 28
    targetSdk = 36
    versionCode = 8
    versionName = "1.2"
  }

  signingConfigs {
    if (releaseSigning != null) {
      create("release") {
        storeFile = file(releaseSigning.getProperty("storeFile"))
        storePassword = releaseSigning.getProperty("storePassword")
        keyAlias = releaseSigning.getProperty("keyAlias")
        keyPassword = releaseSigning.getProperty("keyPassword")
        // Pleos 배포 요건: 키 로테이션·리니지 지원(APK 서명 v3, API 28+). 모든 빌드에 같은 서명
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = false
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // 천문 계산(MIT): 해·달·행성 위치, 좌표 변환, 월상, 뜨고 짐
  implementation("io.github.cosinekitty:astronomy:2.1.19")
  // Pleos 차량 위치·방위(heading)
  implementation("ai.pleos.playground:FusedLocation:1.1.3")

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Pleos Vehicle SDK
  implementation(libs.vehicle.sdk)
}
