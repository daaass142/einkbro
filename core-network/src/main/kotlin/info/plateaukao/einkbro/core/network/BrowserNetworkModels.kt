package info.plateaukao.einkbro.core.network

data class BrowserProxyEndpoint(
    val host: String,
    val port: Int,
)

enum class BrowserNetworkMode {
    BLOCKED,
    DIRECT,
    MIHOMO_SOCKS,
}

sealed interface BrowserNetworkState {
    data object Stopped : BrowserNetworkState
    data object Starting : BrowserNetworkState
    data object Blocked : BrowserNetworkState
    data class Ready(
        val mode: BrowserNetworkMode,
        val endpoint: BrowserProxyEndpoint? = null,
    ) : BrowserNetworkState
    data class Error(val cause: Throwable) : BrowserNetworkState
}
