package info.plateaukao.einkbro.core.mihomo.config

/**
 * Builds the runtime config from an untrusted imported/subscription YAML profile.
 *
 * We intentionally preserve the user's proxy/rule/provider YAML text and remove
 * only top-level inbound/control keys owned by the Android application. The
 * enforced block is appended last. This avoids a short startup window where an
 * imported profile could bind a proxy/controller to the LAN.
 */
class RuntimeConfigBuilder {
    private val appOwnedTopLevelKeys = setOf(
        "<<",
        "allow-lan",
        "bind-address",
        "authentication",
        "skip-auth-prefixes",
        "lan-allowed-ips",
        "lan-disallowed-ips",
        "port",
        "socks-port",
        "mixed-port",
        "redir-port",
        "tproxy-port",
        "listeners",
        "external-controller",
        "external-controller-tls",
        "external-controller-unix",
        "external-controller-pipe",
        "external-controller-cors",
        "external-ui",
        "external-ui-name",
        "external-ui-url",
        "secret",
        "tun",
        "ss-config",
        "vmess-config",
        "tuic-server",
    )

    fun build(
        sourceYaml: String,
        socksPort: Int,
        controllerPort: Int,
        controllerSecret: String,
    ): String {
        require(socksPort in 1..65535)
        require(controllerPort in 1..65535)
        require(socksPort != controllerPort)
        require(controllerSecret.length >= 32)

        val sanitized = stripAppOwnedTopLevelKeys(sourceYaml)
            .trimEnd()

        return buildString {
            if (sanitized.isNotEmpty()) {
                append(sanitized)
                append("\n\n")
            }
            append("# --- EinkBro enforced runtime boundary ---\n")
            append("allow-lan: false\n")
            append("bind-address: \"127.0.0.1\"\n")
            append("port: 0\n")
            append("mixed-port: 0\n")
            append("redir-port: 0\n")
            append("tproxy-port: 0\n")
            append("socks-port: ")
            append(socksPort)
            append("\n")
            append("external-controller: \"127.0.0.1:")
            append(controllerPort)
            append("\"\n")
            append("secret: \"")
            append(escapeDoubleQuoted(controllerSecret))
            append("\"\n")
            append("external-controller-cors:\n")
            append("  allow-origins:\n")
            append("    - \"http://appassets.androidplatform.net\"\n")
            append("  allow-private-network: true\n")
            append("tun:\n")
            append("  enable: false\n")
        }
    }

    internal fun stripAppOwnedTopLevelKeys(sourceYaml: String): String {
        val lines = sourceYaml.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val kept = ArrayList<String>(lines.size)
        var skippingOwnedBlock = false

        for (line in lines) {
            val key = topLevelKey(line)

            if (skippingOwnedBlock) {
                if (line.isBlank() || line.trimStart().startsWith("#")) {
                    continue
                }
                if (line.firstOrNull()?.isWhitespace() == true) {
                    continue
                }
                skippingOwnedBlock = false
            }

            if (key != null && key in appOwnedTopLevelKeys) {
                skippingOwnedBlock = true
                continue
            }

            kept += line
        }

        return kept.joinToString("\n")
    }

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.firstOrNull()?.isWhitespace() == true) return null
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("...")) {
            return null
        }

        if (trimmed.startsWith("\"")) {
            val end = findDoubleQuoteEnd(trimmed)
            if (end <= 0) return null
            if (!trimmed.substring(end + 1).trimStart().startsWith(":")) return null
            return trimmed.substring(1, end)
        }

        if (trimmed.startsWith("'")) {
            var index = 1
            val value = StringBuilder()
            while (index < trimmed.length) {
                if (trimmed[index] == '\'') {
                    if (index + 1 < trimmed.length && trimmed[index + 1] == '\'') {
                        value.append('\'')
                        index += 2
                        continue
                    }
                    if (!trimmed.substring(index + 1).trimStart().startsWith(":")) return null
                    return value.toString()
                }
                value.append(trimmed[index])
                index++
            }
            return null
        }

        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        return trimmed.substring(0, colon).trim()
    }

    private fun findDoubleQuoteEnd(text: String): Int {
        var escaped = false
        for (index in 1 until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '\"') {
                return index
            }
        }
        return -1
    }

    private fun escapeDoubleQuoted(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
