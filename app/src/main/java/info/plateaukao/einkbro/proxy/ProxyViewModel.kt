package info.plateaukao.einkbro.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.core.mihomo.api.MihomoEngine
import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.api.RoutingMode
import info.plateaukao.einkbro.core.mihomo.api.TrafficSnapshot
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRecord
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRepository
import info.plateaukao.einkbro.core.mihomo.profile.ProfileSourceType
import info.plateaukao.einkbro.core.mihomo.profile.SubscriptionUpdater
import info.plateaukao.einkbro.core.mihomo.runtime.MihomoSessionManager
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.ProxyTransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ProxyUiState(
    val enabled: Boolean = false,
    val failClosed: Boolean = true,
    val transportMode: ProxyTransportMode = ProxyTransportMode.BROWSER_PROXY,
    val activeProfileId: String = "",
    val profiles: List<ProfileRecord> = emptyList(),
    val routingMode: RoutingMode = RoutingMode.RULE,
    val groups: List<ProxyGroup> = emptyList(),
    val traffic: TrafficSnapshot = TrafficSnapshot(0, 0),
    val delays: Map<String, Int> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
)

class ProxyViewModel(
    private val config: ConfigManager,
    private val profiles: ProfileRepository,
    private val subscriptions: SubscriptionUpdater,
    private val engine: MihomoEngine,
    private val sessionManager: MihomoSessionManager,
    private val coordinator: MihomoBrowserCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ProxyUiState(
            enabled = config.proxy.enabled,
            failClosed = config.proxy.failClosed,
            transportMode = config.proxy.transportMode,
            activeProfileId = config.proxy.activeProfileId,
        )
    )
    val state: StateFlow<ProxyUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profiles.collectLatest { records ->
                mutableState.value = mutableState.value.copy(
                    profiles = records,
                    enabled = config.proxy.enabled,
                    failClosed = config.proxy.failClosed,
                    transportMode = config.proxy.transportMode,
                    activeProfileId = config.proxy.activeProfileId,
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) = launchBusy {
        if (enabled) {
            require(config.proxy.activeProfileId.isNotBlank()) {
                "Import or select a profile before enabling Mihomo"
            }
            config.proxy.enabled = true
            coordinator.restartProxy()
        } else {
            config.proxy.enabled = false
            coordinator.disableProxy()
        }
        mutableState.value = mutableState.value.copy(enabled = enabled)
        if (enabled) refreshRuntimeInternal()
    }

    fun setTransportMode(mode: ProxyTransportMode) = launchBusy {
        config.proxy.transportMode = mode
        mutableState.value = mutableState.value.copy(transportMode = mode)
        if (config.proxy.enabled) coordinator.restartProxy()
    }

    fun setFailClosed(value: Boolean) {
        config.proxy.failClosed = value
        mutableState.value = mutableState.value.copy(failClosed = value)
    }

    fun importLocal(name: String, yaml: String) = launchBusy {
        val profile = profiles.importLocal(name, yaml)
        activateInternal(profile)
    }

    fun addSubscription(name: String, url: String) = launchBusy {
        val profile = subscriptions.create(name, url)
        activateInternal(profile)
    }

    fun activate(profile: ProfileRecord) = launchBusy {
        activateInternal(profile)
    }

    fun delete(profile: ProfileRecord) = launchBusy {
        if (config.proxy.activeProfileId == profile.id) {
            config.proxy.enabled = false
            config.proxy.activeProfileId = ""
            config.proxy.activeProfilePath = ""
            coordinator.disableProxy()
        }
        profiles.delete(profile.id)
        mutableState.value = mutableState.value.copy(
            enabled = config.proxy.enabled,
            activeProfileId = config.proxy.activeProfileId,
        )
    }

    fun refreshSubscription(profile: ProfileRecord) = launchBusy {
        require(profile.sourceType == ProfileSourceType.SUBSCRIPTION)
        val staged = subscriptions.stageRefresh(profile.id)
        val isActive = config.proxy.activeProfileId == profile.id

        if (!isActive || !config.proxy.enabled) {
            profiles.commit(staged)
            return@launchBusy
        }

        try {
            // Test the candidate as a real embedded mihomo session before it can
            // replace source.yaml. The current WebView proxy remains on the old
            // endpoint and therefore fails closed during the short switch.
            sessionManager.reload(
                MihomoProfile(
                    id = profile.id,
                    name = profile.name,
                    path = staged.candidatePath,
                )
            )
            val committed = profiles.commit(staged)
            config.proxy.activeProfilePath = committed.filePath
            coordinator.restartProxy()
            refreshRuntimeInternal()
        } catch (error: Throwable) {
            profiles.discard(staged)
            profiles.markError(profile.id, error.message)
            // source.yaml is untouched, so restore the known profile/session.
            runCatching { coordinator.restartProxy() }
            throw error
        }
    }

    fun setRoutingMode(mode: RoutingMode) = launchBusy {
        engine.updateConfig(
            "{\"mode\":\"${mode.name.lowercase()}\"}"
        )
        mutableState.value = mutableState.value.copy(routingMode = mode)
    }

    fun selectProxy(group: ProxyGroup, proxyName: String) = launchBusy {
        engine.changeProxy(group.name, proxyName)
        refreshRuntimeInternal()
    }

    fun testDelay(proxyName: String) = launchBusy {
        val delay = engine.testDelay(
            proxyName = proxyName,
            testUrl = DEFAULT_DELAY_URL,
            timeoutMs = 5_000,
        )
        mutableState.value = mutableState.value.copy(
            delays = mutableState.value.delays + (proxyName to delay)
        )
    }

    fun refreshRuntime() = launchBusy {
        refreshRuntimeInternal()
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private suspend fun activateInternal(profile: ProfileRecord) {
        config.proxy.activeProfileId = profile.id
        config.proxy.activeProfilePath = profile.filePath
        mutableState.value = mutableState.value.copy(activeProfileId = profile.id)
        if (config.proxy.enabled) {
            coordinator.restartProxy()
            refreshRuntimeInternal()
        }
    }

    private suspend fun refreshRuntimeInternal() {
        if (!config.proxy.enabled) {
            mutableState.value = mutableState.value.copy(groups = emptyList())
            return
        }

        val catalog = engine.getProxies()
        val order = runCatching { engine.queryProxyGroupOrder() }.getOrDefault(emptyList())
        val rank = order.withIndex().associate { it.value to it.index }
        val groups = catalog.groups.sortedWith(
            compareBy<ProxyGroup> { rank[it.name] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
        mutableState.value = mutableState.value.copy(
            groups = groups,
            traffic = engine.getTraffic(),
        )
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            try {
                block()
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(
                    error = error.message ?: error.javaClass.simpleName
                )
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    private companion object {
        const val DEFAULT_DELAY_URL = "https://www.gstatic.com/generate_204"
    }
}
