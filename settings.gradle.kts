pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven { url = uri("https://jitpack.io") } // sherpa-onnx (TTS offline)
  }
}

rootProject.name = "VisualAssist"

include(":app")
