package info.plateaukao.einkbro.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserNetworkGatewayTest {
    @Test
    fun prepareStartsFailClosed() = runTest {
        val adapter = FakeProxyAdapter()
        val gateway = BrowserNetworkGatewayImpl(adapter)

        gateway.prepare()

        assertEquals(BrowserNetworkState.Blocked, gateway.state.value)
        assertEquals(BrowserProxyEndpoint("127.0.0.1", 1), adapter.endpoint)
    }

    @Test
    fun explicitDirectClearsProxy() = runTest {
        val adapter = FakeProxyAdapter()
        val gateway = BrowserNetworkGatewayImpl(adapter)

        gateway.block()
        gateway.enableDirect()

        assertEquals(
            BrowserNetworkState.Ready(BrowserNetworkMode.DIRECT),
            gateway.state.value,
        )
        assertEquals(null, adapter.endpoint)
    }

    @Test
    fun mihomoEndpointBecomesReady() = runTest {
        val adapter = FakeProxyAdapter()
        val gateway = BrowserNetworkGatewayImpl(adapter)
        val endpoint = BrowserProxyEndpoint("127.0.0.1", 23456)

        gateway.enableProxy(endpoint)

        assertEquals(
            BrowserNetworkState.Ready(BrowserNetworkMode.MIHOMO_SOCKS, endpoint),
            gateway.state.value,
        )
        assertEquals(endpoint, adapter.endpoint)
    }

    private class FakeProxyAdapter : WebViewProxyAdapter {
        var endpoint: BrowserProxyEndpoint? = null

        override fun requireSupported() = Unit

        override suspend fun setProxy(endpoint: BrowserProxyEndpoint) {
            this.endpoint = endpoint
        }

        override suspend fun clearProxy() {
            endpoint = null
        }
    }
}
