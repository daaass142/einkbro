package info.plateaukao.einkbro.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBootstrapTest {
    @Test
    fun bootstrapPinsLoopbackControllerAndDisablesPrivilegedControls() {
        val url = DashboardBootstrap.url(
            host = "127.0.0.1",
            port = 29090,
            secret = "a secret/+value",
        )

        assertTrue(url.startsWith(
            "http://appassets.androidplatform.net/zashboard/index.html#/setup?"
        ))
        assertTrue(url.contains("protocol=http"))
        assertTrue(url.contains("hostname=127.0.0.1"))
        assertTrue(url.contains("port=29090"))
        assertTrue(url.contains("disableUpgradeCore=1"))
        assertTrue(url.contains("disableTunMode=1"))
        assertFalse(url.contains("a secret/+value"))
        assertTrue(url.contains("secret=a+secret%2F%2Bvalue"))
    }

    @Test
    fun remoteControllerHostIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardBootstrap.url(
                host = "192.168.1.2",
                port = 9090,
                secret = "secret",
            )
        }
    }

    @Test
    fun blankSecretIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardBootstrap.url(
                host = "127.0.0.1",
                port = 9090,
                secret = "",
            )
        }
    }

    @Test
    fun invalidPortIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardBootstrap.url(
                host = "127.0.0.1",
                port = 0,
                secret = "secret",
            )
        }
    }
}
