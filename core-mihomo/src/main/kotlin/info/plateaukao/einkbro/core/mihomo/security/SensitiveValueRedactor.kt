package info.plateaukao.einkbro.core.mihomo.security

import java.net.URI

object SensitiveValueRedactor {
    private val secretKeys = setOf(
        "token",
        "secret",
        "key",
        "password",
        "passwd",
        "auth",
        "authorization",
    )

    fun redactUrl(value: String): String {
        return try {
            val uri = URI(value)
            val query = uri.rawQuery ?: return value
            val redacted = query.split("&").joinToString("&") { entry ->
                val key = entry.substringBefore("=")
                if (secretKeys.any { key.contains(it, ignoreCase = true) }) {
                    "$key=REDACTED"
                } else {
                    entry
                }
            }
            URI(
                uri.scheme,
                uri.rawAuthority,
                uri.rawPath,
                redacted,
                uri.rawFragment,
            ).toASCIIString()
        } catch (_: Throwable) {
            value.replace(
                Regex("(?i)(token|secret|password|passwd|auth|key)=([^&\\s]+)"),
                "$1=REDACTED",
            )
        }
    }
}
