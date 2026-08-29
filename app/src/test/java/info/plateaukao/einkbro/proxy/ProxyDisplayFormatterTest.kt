package info.plateaukao.einkbro.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyDisplayFormatterTest {
    @Test
    fun subscriptionHostDropsPathQueryFragmentAndUserInfo() {
        val source =
            "https://user:password@example.com/sub/path?token=super-secret#fragment"

        val displayed = ProxyDisplayFormatter.subscriptionHost(source)

        assertEquals("example.com", displayed)
        assertFalse(displayed.orEmpty().contains("super-secret"))
        assertFalse(displayed.orEmpty().contains("password"))
        assertFalse(displayed.orEmpty().contains("/sub"))
    }

    @Test
    fun invalidOrBlankSubscriptionHasNoDisplayHost() {
        assertNull(ProxyDisplayFormatter.subscriptionHost(null))
        assertNull(ProxyDisplayFormatter.subscriptionHost(""))
        assertNull(ProxyDisplayFormatter.subscriptionHost("not a url"))
    }

    @Test
    fun ipv6HostIsKeptWithoutSensitiveUrlParts() {
        val displayed = ProxyDisplayFormatter.subscriptionHost(
            "https://[2001:db8::1]/subscription?key=secret"
        )

        assertEquals("[2001:db8::1]", displayed)
    }
}
