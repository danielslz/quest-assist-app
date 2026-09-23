plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.meta.spatial.plugin)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.pesquisa.visualassist"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.pesquisa.visualassist"
    minSdk = 34
    // HorizonOS é Android 14 (API 34)
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // NDK usado pelo Spatial SDK (custom shaders / código nativo)
    ndkVersion = "27.0.12077973"
  }

  packaging { resources.excludes.add("META-INF/LICENSE") }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.android)

  // Compose (UI de configuração 2D/painel)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.material3)

  // Meta Spatial SDK
  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.vr)
  implementation(libs.meta.spatial.sdk.isdk)
  implementation(libs.meta.spatial.sdk.compose)
  implementation(libs.meta.spatial.sdk.spatialaudio)
  implementation(libs.meta.spatial.sdk.hotreload)

  // ML / visão on-device
  implementation(libs.mlkit.text.recognition)   // OCR (Requisito 3)
  implementation(libs.mediapipe.tasks.vision)    // objetos/pessoas (Requisitos 2, 4)
  // NOTA: onnxruntime-android removido — o sherpa-onnx já embarca o ONNX Runtime
  // (evita duplicar libonnxruntime.so). Detecção de objetos usará MediaPipe;
  // se precisar rodar modelos ONNX customizados (YOLO), reavaliar empacotamento.

  // TTS offline (o Quest não tem motor TTS do sistema) — Requisito 5.1
  // Usa o módulo Android (AAR); exclui o módulo -jvm (classes duplicadas).
  implementation(libs.sherpa.onnx) {
    exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
  }

  // Testes
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}

// Configuração do Spatial SDK Gradle plugin.
// O caminho do Spatial Editor CLI é resolvido pela env var META_SPATIAL_EDITOR_CLI_PATH
// (já configurada na distrobox quest-dev e no environment.d do host).
spatial {
  allowUsageDataCollection.set(true)
}
