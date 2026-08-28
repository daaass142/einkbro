package info.plateaukao.einkbro.core.mihomo.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConfigBuilderTest {
    private val builder = RuntimeConfigBuilder()

    @Test
    fun stripsUntrustedInboundAndControllerSettings() {
        val source = """
            allow-lan: true
            bind-address: 0.0.0.0
            mixed-port: 7890
            external-controller: 0.0.0.0:9090
            secret: ""
            listeners:
              - name: dangerous
                type: mixed
                port: 9999
                listen: 0.0.0.0
            proxies:
              - name: test
                type: direct
            rules:
              - MATCH,test
        """.trimIndent()

        val result = builder.build(source, 32123, 32124, "abcdefghijklmnopqrstuvwxyz123456")

        assertFalse(result.contains("0.0.0.0"))
        assertFalse(result.contains("mixed-port: 7890"))
        assertFalse(result.contains("name: dangerous"))
        assertTrue(result.contains("proxies:"))
        assertTrue(result.contains("socks-port: 32123"))
        assertTrue(result.contains("external-controller: \"127.0.0.1:32124\""))
        assertTrue(result.contains("allow-lan: false"))
    }

    @Test
    fun quotedSecurityKeysCannotBypassSanitizer() {
        val source = """
            "allow-lan": true
            'external-controller': 0.0.0.0:9090
            "<<": *unsafe
            proxies: []
        """.trimIndent()

        val result = builder.build(source, 30001, 30002, "abcdefghijklmnopqrstuvwxyz123456")

        assertFalse(result.contains("0.0.0.0"))
        assertFalse(result.contains("*unsafe"))
        assertTrue(result.contains("allow-lan: false"))
    }

    @Test
    fun forcesTunOffForBrowserProxyMode() {
        val source = """
            tun:
              enable: true
              auto-route: true
            proxies: []
        """.trimIndent()

        val result = builder.build(source, 30001, 30002, "abcdefghijklmnopqrstuvwxyz123456")

        assertFalse(result.contains("auto-route: true"))
        assertTrue(result.contains("tun:\n  enable: false"))
    }
}
