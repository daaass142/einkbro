package info.plateaukao.einkbro.proxy

import java.net.URI

internal enum class DashboardNavigationAction {
    ALLOW_INTERNAL,
    BLOCK_LOOPBACK,
    OPEN_EXTERNAL,
}

internal object DashboardNavigationPolicy {
    const val APP_ASSET_HOST = "appassets.androidplatform.net"
    private const val DASHBOARD_PATH_PREFIX = "/zashboard/"

    fun classify(rawUrl: String): DashboardNavigationAction {
        if (rawUrl == "about:blank") return DashboardNavigationAction.ALLOW_INTERNAL

        val uri = runCatching { URI(rawUrl) }.getOrNull()
            ?: return DashboardNavigationAction.OPEN_EXTERNAL

        val host = uri.host.orEmpty()
        if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
            return DashboardNavigationAction.BLOCK_LOOPBACK
        }

        if (
            uri.scheme.equals("http", ignoreCase = true) &&
            host.equals(APP_ASSET_HOST, ignoreCase = true) &&
            uri.path.orEmpty().startsWith(DASHBOARD_PATH_PREFIX)
        ) {
            return DashboardNavigationAction.ALLOW_INTERNAL
        }

        return DashboardNavigationAction.OPEN_EXTERNAL
    }
}
