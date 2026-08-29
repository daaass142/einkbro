package info.plateaukao.einkbro.core.mihomo.config

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class ConfigStore(
    filesDir: File,
) {
    val homeDir: File = File(filesDir, "mihomo/runtime").apply { mkdirs() }
    val configFile: File = File(homeDir, "config.yaml")
    val lastKnownGoodFile: File = File(homeDir, "last-known-good.yaml")

    fun writeRuntimeConfig(content: String) {
        atomicWrite(configFile, content.toByteArray(StandardCharsets.UTF_8))
    }

    fun markLastKnownGood() {
        check(configFile.isFile) { "Runtime config does not exist" }
        atomicWrite(lastKnownGoodFile, configFile.readBytes())
    }

    fun restoreLastKnownGood(): Boolean {
        if (!lastKnownGoodFile.isFile) return false
        atomicWrite(configFile, lastKnownGoodFile.readBytes())
        return true
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            // Android's Os.rename maps to rename(2): replacement in the same
            // filesystem is atomic, so readers never observe a half-written YAML.
            Os.rename(temp.absolutePath, target.absolutePath)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
