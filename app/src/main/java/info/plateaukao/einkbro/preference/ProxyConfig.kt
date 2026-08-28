package info.plateaukao.einkbro.preference

import android.content.SharedPreferences

enum class ProxyTransportMode {
    BROWSER_PROXY,
    STRICT_VPN,
}

class ProxyConfig(
    private val sp: SharedPreferences,
) {
    var enabled by BooleanPreference(sp, K_ENABLED, false)
    var autoStart by BooleanPreference(sp, K_AUTO_START, true)
    var failClosed by BooleanPreference(sp, K_FAIL_CLOSED, true)
    var activeProfileId by StringPreference(sp, K_ACTIVE_PROFILE_ID, "")
    var activeProfilePath by StringPreference(sp, K_ACTIVE_PROFILE_PATH, "")

    var transportMode: ProxyTransportMode
        get() = runCatching {
            ProxyTransportMode.valueOf(
                sp.getString(K_TRANSPORT_MODE, ProxyTransportMode.BROWSER_PROXY.name)
                    ?: ProxyTransportMode.BROWSER_PROXY.name
            )
        }.getOrDefault(ProxyTransportMode.BROWSER_PROXY)
        set(value) {
            sp.edit().putString(K_TRANSPORT_MODE, value.name).apply()
        }

    companion object {
        const val K_ENABLED = "sp_mihomo_enabled"
        const val K_AUTO_START = "sp_mihomo_auto_start"
        const val K_FAIL_CLOSED = "sp_mihomo_fail_closed"
        const val K_ACTIVE_PROFILE_ID = "sp_mihomo_active_profile_id"
        const val K_ACTIVE_PROFILE_PATH = "sp_mihomo_active_profile_path"
        const val K_TRANSPORT_MODE = "sp_mihomo_transport_mode"
    }
}
