package info.plateaukao.einkbro.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardNavigationPolicyTest {
    @Test
    fun bundledDashboardPathStaysInternal() {
        assertEquals(
            DashboardNavigationAction.ALLOW_INTERNAL,
            DashboardNavigationPolicy.classify(
                "http://appassets.androidplatform.net/zashboard/index.html#/setup"
            ),
        )
    }

    @Test
    fun appassetsOutsideDashboardDoesNotBecomePrivileged() {
        assertEquals(
            DashboardNavigationAction.OPEN_EXTERNAL,
            DashboardNavigationPolicy.classify(
                "http://appassets.androidplatform.net/other/index.html"
            ),
        )
    }

    @Test
    fun loopbackControllerNavigationIsBlocked() {
        assertEquals(
            DashboardNavigationAction.BLOCK_LOOPBACK,
            DashboardNavigationPolicy.classify("http://127.0.0.1:9090/configs"),
        )
        assertEquals(
            DashboardNavigationAction.BLOCK_LOOPBACK,
            DashboardNavigationPolicy.classify("http://localhost:9090/ui"),
        )
    }

    @Test
    fun externalNavigationLeavesDedicatedDashboard() {
        assertEquals(
            DashboardNavigationAction.OPEN_EXTERNAL,
            DashboardNavigationPolicy.classify("https://example.com/docs"),
        )
    }

    @Test
    fun aboutBlankIsAllowedForWebViewTeardown() {
        assertEquals(
            DashboardNavigationAction.ALLOW_INTERNAL,
            DashboardNavigationPolicy.classify("about:blank"),
        )
    }

    @Test
    fun httpsAppassetsIsNotSilentlyAcceptedByHttpPolicy() {
        assertEquals(
            DashboardNavigationAction.OPEN_EXTERNAL,
            DashboardNavigationPolicy.classify(
                "https://appassets.androidplatform.net/zashboard/index.html"
            ),
        )
    }
}
