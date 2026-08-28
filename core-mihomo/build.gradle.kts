import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val libmihomoAar = layout.buildDirectory.file(
    "third-party/libmihomo-android-v$libmihomoVersion.aar"
)

val fetchLibmihomo by tasks.registering {
    inputs.property("version", libmihomoVersion)
    inputs.property("sha256", libmihomoSha256)
    outputs.file(libmihomoAar)

    doLast {
        val aar = libmihomoAar.get().asFile
        if (aar.isFile && sha256(aar) == libmihomoSha256) return@doLast

        aar.parentFile.mkdirs()
        val partial = File(aar.parentFile, aar.name + ".part")
        partial.delete()

        val url =
            "https://github.com/daaass142/libmihomo-android/releases/download/" +
                "v$libmihomoVersion/libmihomo-android-v$libmihomoVersion.aar"

        URI.create(url).toURL().openStream().use { input ->
            partial.outputStream().buffered().use { output -> input.copyTo(output) }
        }

        val actual = sha256(partial)
        check(actual == libmihomoSha256) {
            "libmihomo checksum mismatch: expected " + libmihomoSha256 + ", got " + actual
        }

        if (aar.exists()) check(aar.delete()) { "Unable to replace verified libmihomo AAR" }
        check(partial.renameTo(aar)) { "Unable to move verified libmihomo AAR into place" }
    }
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
    implementation(libmihomoFiles)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
