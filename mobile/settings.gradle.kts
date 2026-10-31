pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "signa-mobile"

include(":core")
// Máy không có Android SDK vẫn chạy được test của :core: ./gradlew -PcoreOnly :core:test
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
