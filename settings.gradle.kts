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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "einkbro"

include(":adblock-client")
include(":ad-filter")
include(":app")
include(":core-mihomo")
include(":core-network")
