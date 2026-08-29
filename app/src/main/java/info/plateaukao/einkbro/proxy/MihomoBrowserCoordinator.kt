package info.plateaukao.einkbro.proxy

import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile
import info.plateaukao.einkbro.core.mihomo.api.MihomoSession
import info.plateaukao.einkbro.core.mihomo.runtime.MihomoSessionManager
import info.plateaukao.einkbro.core.network.BrowserNetworkGateway
import info.plateaukao.einkbro.core.network.BrowserProxyEndpoint
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.ProxyTransportMode
import info.plateaukao.einkbro.proxy.vpn.StrictVpnController
import info.plateaukao.einkbro.proxy.vpn.StrictVpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface MihomoBrowserState {
    data object Unprepared : MihomoBrowserState
    data object Preparing : MihomoBrowserState
    data object Direct : MihomoBrowserState
    data class Proxied(val session: MihomoSession) : MihomoBrowserState
    data class Failed(val error: Throwable) : MihomoBrowserState
}

/**
 * Process-wide owner of the transition between WebView networking and mihomo.
 *
 * BrowserActivity only asks this class whether network use is ready. It never
 * starts native code or changes ProxyController directly.
 */
class MihomoBrowserCoordinator(
    private val config: ConfigManager,
    private val sessionManager: MihomoSessionManager,
    private val networkGateway: BrowserNetworkGateway,
    private val strictVpn: StrictVpnController,
    appScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val mutableState =
        MutableStateFlow<MihomoBrowserState>(MihomoBrowserState.Unprepared)

    val state: StateFlow<MihomoBrowserState> = mutableState.asStateFlow()

    private var temporaryDirect = false

    init {
        appScope.launch {
            strictVpn.state.collect { state ->
                if (
                    config.proxy.enabled &&
                    config.proxy.transportMode == ProxyTransportMode.STRICT_VPN &&
                    state !is StrictVpnState.Running &&
                    state !is StrictVpnState.Starting
                ) {
                    runCatching { networkGateway.block() }
                }
            }
        }
    }

    suspend fun ensureReady(): MihomoBrowserState = mutex.withLock {
        if (temporaryDirect) {
            networkGateway.enableDirect()
            return@withLock MihomoBrowserState.Direct.also { mutableState.value = it }
        }

        if (!config.proxy.enabled) {
            networkGateway.enableDirect()
            return@withLock MihomoBrowserState.Direct.also { mutableState.value = it }
        }

        val path = config.proxy.activeProfilePath
        val id = config.proxy.activeProfileId.ifBlank { "active" }

        val current = mutableState.value as? MihomoBrowserState.Proxied
        if (current != null && current.session.profile.path == path) return@withLock current

        mutableState.value = MihomoBrowserState.Preparing

        try {
            // Install the dead SOCKS endpoint before any native/profile work. If
            // anything below throws, WebView remains unable to reach the Internet.
            networkGateway.prepare()

            check(path.isNotBlank()) {
                "Mihomo is enabled but no active profile is configured"
            }

            val profile = MihomoProfile(
                id = id,
                name = java.io.File(path).nameWithoutExtension.ifBlank { "Mihomo" },
                path = path,
            )
            val session = sessionManager.start(profile)
            when (config.proxy.transportMode) {
                ProxyTransportMode.BROWSER_PROXY -> {
                    strictVpn.stop()
                    networkGateway.enableProxy(
                        BrowserProxyEndpoint(
                            host = session.socksEndpoint.host,
                            port = session.socksEndpoint.port,
                        )
                    )
                }
                ProxyTransportMode.STRICT_VPN -> {
                    networkGateway.block()
                    strictVpn.start()
                    strictVpn.awaitRunning()
                    networkGateway.enableDirect()
                }
            }
            MihomoBrowserState.Proxied(session).also { mutableState.value = it }
        } catch (error: Throwable) {
            runCatching { networkGateway.block() }
            mutableState.value = MihomoBrowserState.Failed(error)
            throw error
        }
    }

    suspend fun useDirectTemporarily() = mutex.withLock {
        temporaryDirect = true
        strictVpn.stop()
        networkGateway.enableDirect()
        mutableState.value = MihomoBrowserState.Direct
    }

    suspend fun restartProxy(): MihomoBrowserState {
        mutex.withLock {
            temporaryDirect = false
            runCatching { networkGateway.block() }
            strictVpn.stop()
            runCatching { sessionManager.stop() }
            mutableState.value = MihomoBrowserState.Unprepared
        }
        return ensureReady()
    }

    suspend fun disableProxy() = mutex.withLock {
        temporaryDirect = false
        strictVpn.stop()
        runCatching { sessionManager.stop() }
        networkGateway.enableDirect()
        mutableState.value = MihomoBrowserState.Direct
    }
}
