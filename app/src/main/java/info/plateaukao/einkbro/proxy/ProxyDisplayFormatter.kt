package info.plateaukao.einkbro.proxy

import java.net.URI

internal object ProxyDisplayFormatter {
    /**
     * Returns only the host portion of a subscription URL.
     *
     * Query strings, fragments, user info and paths are intentionally discarded
     * so access tokens never become part of normal profile UI.
     */
    fun subscriptionHost(sourceUrl: String?): String? {
        if (sourceUrl.isNullOrBlank()) return null
        return runCatching { URI(sourceUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
