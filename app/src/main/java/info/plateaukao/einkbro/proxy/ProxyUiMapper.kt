package info.plateaukao.einkbro.proxy

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.security.SensitiveValueRedactor
import info.plateaukao.einkbro.preference.ProxyTransportMode

internal object ProxyUiMapper {
    fun runtimeStatus(
        enabled: Boolean,
        failClosed: Boolean,
        transportMode: ProxyTransportMode,
        browserState: MihomoBrowserState,
    ): RuntimeUiStatus =
        when (browserState) {
            MihomoBrowserState.Unprepared ->
                if (enabled) RuntimeUiStatus.Starting else RuntimeUiStatus.Off

            MihomoBrowserState.Preparing ->
                RuntimeUiStatus.Starting

            MihomoBrowserState.Direct ->
                if (enabled) RuntimeUiStatus.TemporaryDirect else RuntimeUiStatus.Off

            is MihomoBrowserState.Proxied ->
                when (transportMode) {
                    ProxyTransportMode.BROWSER_PROXY ->
                        RuntimeUiStatus.ProtectedBrowserProxy(browserState.session.profile.name)

                    ProxyTransportMode.STRICT_VPN ->
                        RuntimeUiStatus.ProtectedStrictVpn(browserState.session.profile.name)
                }

            is MihomoBrowserState.Failed -> {
                val reason = when (browserState.error) {
                    is MihomoException.NativeLoadFailure,
                    is MihomoException.BridgeAbiMismatch -> null
                    else -> SensitiveValueRedactor.redactUrl(
                        browserState.error.message ?: browserState.error.javaClass.simpleName
                    )
                }
                if (enabled && failClosed) {
                    RuntimeUiStatus.Blocked(reason)
                } else {
                    RuntimeUiStatus.Error(reason)
                }
            }
        }

    fun error(
        error: Throwable,
        defaultCategory: ProxyErrorCategory,
    ): ProxyUiError {
        val category = when (error) {
            is MihomoException.NativeLoadFailure,
            is MihomoException.BridgeAbiMismatch -> ProxyErrorCategory.APP_INCOMPATIBLE

            is MihomoException.InvalidProfile -> ProxyErrorCategory.PROFILE
            is SecurityException -> ProxyErrorCategory.VPN_PERMISSION

            is IllegalArgumentException,
            is IllegalStateException -> if (
                error.message.orEmpty().contains("profile", ignoreCase = true) &&
                error.message.orEmpty().contains("enabl", ignoreCase = true)
            ) {
                ProxyErrorCategory.PROFILE_REQUIRED
            } else {
                defaultCategory
            }

            else -> defaultCategory
        }

        return ProxyUiError(
            category = category,
            message = when {
                category == ProxyErrorCategory.PROFILE_REQUIRED -> ""
                error is MihomoException.NativeLoadFailure ||
                    error is MihomoException.BridgeAbiMismatch -> ""
                else -> SensitiveValueRedactor.redactUrl(
                    error.message ?: error.javaClass.simpleName
                )
            },
        )
    }
}
