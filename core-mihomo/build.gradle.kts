import buildlogic.VerifiedDownloadTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val mihomoDependencyProperties = Properties().apply {
    rootProject.file("gradle/mihomo-dependencies.properties").inputStream().use(::load)
}

val libmihomoVersion = requireNotNull(
    mihomoDependencyProperties.getProperty("libmihomo.version")
)
val libmihomoSha256 = requireNotNull(
    mihomoDependencyProperties.getProperty("libmihomo.sha256")
)

val libmihomoAar = layout.buildDirectory.file(
    "third-party/libmihomo-android.aar"
)

val fetchLibmihomo = tasks.register<VerifiedDownloadTask>("fetchLibmihomo") {
    sourceUrl.set(
        "https://github.com/daaass142/libmihomo-android/releases/download/" +
            "v$libmihomoVersion/libmihomo-android-v$libmihomoVersion.aar"
    )
    expectedSha256.set(libmihomoSha256)
    outputFile.set(libmihomoAar)
}

val libmihomoFiles = files(libmihomoAar).builtBy(fetchLibmihomo)

android {
    namespace = "info.plateaukao.einkbro.core.mihomo"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        create("releaseDebuggable") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly(libmihomoFiles)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
