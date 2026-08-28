package info.plateaukao.einkbro.core.network

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BrowserNetworkGateway {
    val state: StateFlow<BrowserNetworkState>
    val proxySupported: Boolean

    suspend fun prepare()
    suspend fun enableProxy(endpoint: BrowserProxyEndpoint)
    suspend fun enableDirect()
    suspend fun block()
    suspend fun shutdown()
}

class BrowserNetworkGatewayImpl(
    private val proxyAdapter: WebViewProxyAdapter,
) : BrowserNetworkGateway {
    private val mutableState =
        kotlinx.coroutines.flow.MutableStateFlow<BrowserNetworkState>(BrowserNetworkState.Stopped)

    override val state: StateFlow<BrowserNetworkState> =
        mutableState.asStateFlow()

    override val proxySupported: Boolean
        get() = proxyAdapter.isSupported()

    override suspend fun prepare() {
        mutableState.value = BrowserNetworkState.Starting
        try {
            proxyAdapter.requireSupported()
            block()
        } catch (error: Throwable) {
            mutableState.value = BrowserNetworkState.Error(error)
            throw error
        }
    }

    override suspend fun enableProxy(endpoint: BrowserProxyEndpoint) {
        try {
            proxyAdapter.requireSupported()
            proxyAdapter.setProxy(endpoint)
            mutableState.value = BrowserNetworkState.Ready(
                mode = BrowserNetworkMode.MIHOMO_SOCKS,
                endpoint = endpoint,
            )
        } catch (error: Throwable) {
            mutableState.value = BrowserNetworkState.Error(error)
            throw error
        }
    }

    override suspend fun enableDirect() {
        try {
            proxyAdapter.requireSupported()
            proxyAdapter.clearProxy()
            mutableState.value = BrowserNetworkState.Ready(BrowserNetworkMode.DIRECT)
        } catch (error: Throwable) {
            mutableState.value = BrowserNetworkState.Error(error)
            throw error
        }
    }

    override suspend fun block() {
        try {
            proxyAdapter.requireSupported()
            // Port 1 on loopback is privileged and cannot be opened by an ordinary
            // Android app. Keeping WebView pointed at this dead SOCKS endpoint gives
            // the browser a fail-closed state without changing device-wide networking.
            val blocked = BrowserProxyEndpoint("127.0.0.1", 1)
            proxyAdapter.setProxy(blocked)
            mutableState.value = BrowserNetworkState.Blocked
        } catch (error: Throwable) {
            mutableState.value = BrowserNetworkState.Error(error)
            throw error
        }
    }

    override suspend fun shutdown() {
        proxyAdapter.clearProxy()
        mutableState.value = BrowserNetworkState.Stopped
    }
}
