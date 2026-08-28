package info.plateaukao.einkbro.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.core.mihomo.api.MihomoEngine
import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile
import info.plateaukao.einkbro.core.mihomo.api.MihomoSessionState
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.api.RoutingMode
import info.plateaukao.einkbro.core.mihomo.api.TrafficSnapshot
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRecord
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRepository
import info.plateaukao.einkbro.core.mihomo.profile.ProfileSourceType
import info.plateaukao.einkbro.core.mihomo.profile.SubscriptionUpdater
import info.plateaukao.einkbro.core.mihomo.runtime.MihomoSessionManager
import info.plateaukao.einkbro.core.mihomo.security.SensitiveValueRedactor
import info.plateaukao.einkbro.core.network.BrowserNetworkGateway
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.ProxyTransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProxyAction {
    data object StartingRuntime : ProxyAction
    data object StoppingRuntime : ProxyAction
    data object SwitchingTransport : ProxyAction
    data object ImportingProfile : ProxyAction
    data object AddingSubscription : ProxyAction
    data class ActivatingProfile(val profileId: String) : ProxyAction
    data class RefreshingSubscription(val profileId: String) : ProxyAction
    data class DeletingProfile(val profileId: String) : ProxyAction
    data object ChangingRouting : ProxyAction
    data class SwitchingNode(val groupName: String) : ProxyAction
    data class TestingDelay(val proxyName: String) : ProxyAction
    data object RefreshingRuntime : ProxyAction
    data object RetryingRuntime : ProxyAction
    data object EnteringTemporaryDirect : ProxyAction
}

sealed interface RuntimeUiStatus {
    data object Off : RuntimeUiStatus
    data object Starting : RuntimeUiStatus
    data class ProtectedBrowserProxy(val profileName: String) : RuntimeUiStatus
    data class ProtectedStrictVpn(val profileName: String) : RuntimeUiStatus
    data class Blocked(val reason: String?) : RuntimeUiStatus
    data object TemporaryDirect : RuntimeUiStatus
    data class Error(val reason: String?) : RuntimeUiStatus
}

enum class ProxyErrorCategory {
    PROFILE_REQUIRED,
    PROFILE,
    SUBSCRIPTION,
    APP_INCOMPATIBLE,
    VPN_PERMISSION,
    RUNTIME,
    UNKNOWN,
}

data class ProxyUiError(
    val category: ProxyErrorCategory,
    val message: String,
)

data class ProxyUiState(
    val enabled: Boolean = false,
    val failClosed: Boolean = true,
    val transportMode: ProxyTransportMode = ProxyTransportMode.BROWSER_PROXY,
    val runtimeStatus: RuntimeUiStatus = RuntimeUiStatus.Off,
    val activeProfileId: String = "",
    val profiles: List<ProfileRecord> = emptyList(),
    val routingMode: RoutingMode = RoutingMode.RULE,
    val groups: List<ProxyGroup> = emptyList(),
    val traffic: TrafficSnapshot = TrafficSnapshot(0, 0),
    val delays: Map<String, Int> = emptyMap(),
    val currentAction: ProxyAction? = null,
    val error: ProxyUiError? = null,
    val webViewProxySupported: Boolean = false,
    val socksReady: Boolean = false,
    val controllerReady: Boolean = false,
) {
    val activeProfile: ProfileRecord?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    val primarySelectedProxy: String?
        get() = groups.firstOrNull()?.selected
}

class ProxyViewModel(
    private val config: ConfigManager,
    private val profiles: ProfileRepository,
    private val subscriptions: SubscriptionUpdater,
    private val engine: MihomoEngine,
    private val sessionManager: MihomoSessionManager,
    private val coordinator: MihomoBrowserCoordinator,
    private val networkGateway: BrowserNetworkGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ProxyUiState(
            enabled = config.proxy.enabled,
            failClosed = config.proxy.failClosed,
            transportMode = config.proxy.transportMode,
            activeProfileId = config.proxy.activeProfileId,
            webViewProxySupported = networkGateway.proxySupported,
            runtimeStatus = ProxyUiMapper.runtimeStatus(
                enabled = config.proxy.enabled,
                failClosed = config.proxy.failClosed,
                transportMode = config.proxy.transportMode,
                browserState = coordinator.state.value,
            ),
        )
    )

    val state: StateFlow<ProxyUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profiles.collectLatest { records ->
                mutableState.update {
                    it.copy(
                        profiles = records,
                        enabled = config.proxy.enabled,
                        failClosed = config.proxy.failClosed,
                        transportMode = config.proxy.transportMode,
                        activeProfileId = config.proxy.activeProfileId,
                    )
                }
            }
        }

        viewModelScope.launch {
            sessionManager.state.collectLatest { sessionState ->
                val ready = sessionState is MihomoSessionState.Running
                mutableState.update {
                    it.copy(
                        socksReady = ready,
                        controllerReady = ready,
                    )
                }
            }
        }

        viewModelScope.launch {
            coordinator.state.collectLatest { browserState ->
                mutableState.update {
                    it.copy(
                        enabled = config.proxy.enabled,
                        transportMode = config.proxy.transportMode,
                        runtimeStatus = ProxyUiMapper.runtimeStatus(
                            enabled = config.proxy.enabled,
                            failClosed = config.proxy.failClosed,
                            transportMode = config.proxy.transportMode,
                            browserState = browserState,
                        ),
                    )
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) = launchAction(
        action = if (enabled) ProxyAction.StartingRuntime else ProxyAction.StoppingRuntime,
        defaultError = ProxyErrorCategory.RUNTIME,
    ) {
        if (enabled) {
            require(config.proxy.activeProfileId.isNotBlank()) {
                "Import or select a profile before enabling Mihomo"
            }
            config.proxy.enabled = true
            mutableState.update { it.copy(enabled = true, runtimeStatus = RuntimeUiStatus.Starting) }
            coordinator.restartProxy()
            refreshRuntimeInternal()
        } else {
            config.proxy.enabled = false
            coordinator.disableProxy()
            mutableState.update {
                it.copy(
                    enabled = false,
                    groups = emptyList(),
                    runtimeStatus = RuntimeUiStatus.Off,
                )
            }
        }
    }

    fun setTransportMode(mode: ProxyTransportMode) = launchAction(
        ProxyAction.SwitchingTransport,
        ProxyErrorCategory.RUNTIME,
    ) {
        config.proxy.transportMode = mode
        mutableState.update {
            it.copy(
                transportMode = mode,
                runtimeStatus = if (config.proxy.enabled) RuntimeUiStatus.Starting else it.runtimeStatus,
            )
        }
        if (config.proxy.enabled) {
            coordinator.restartProxy()
            refreshRuntimeInternal()
        }
    }

    fun setFailClosed(value: Boolean) {
        config.proxy.failClosed = value
        mutableState.update {
            it.copy(
                failClosed = value,
                runtimeStatus = ProxyUiMapper.runtimeStatus(
                            enabled = config.proxy.enabled,
                            failClosed = config.proxy.failClosed,
                            transportMode = config.proxy.transportMode,
                            browserState = coordinator.state.value,
                        ),
            )
        }
    }

    fun importLocal(name: String, yaml: String) = launchAction(
        ProxyAction.ImportingProfile,
        ProxyErrorCategory.PROFILE,
    ) {
        val profile = profiles.importLocal(name, yaml)
        activateInternal(profile)
    }

    fun addSubscription(name: String, url: String) = launchAction(
        ProxyAction.AddingSubscription,
        ProxyErrorCategory.SUBSCRIPTION,
    ) {
        val profile = subscriptions.create(name, url)
        activateInternal(profile)
    }

    fun activate(profile: ProfileRecord) = launchAction(
        ProxyAction.ActivatingProfile(profile.id),
        ProxyErrorCategory.PROFILE,
    ) {
        activateInternal(profile)
    }

    fun delete(profile: ProfileRecord) = launchAction(
        ProxyAction.DeletingProfile(profile.id),
        ProxyErrorCategory.PROFILE,
    ) {
        if (config.proxy.activeProfileId == profile.id) {
            config.proxy.enabled = false
            config.proxy.activeProfileId = ""
            config.proxy.activeProfilePath = ""
            coordinator.disableProxy()
        }
        profiles.delete(profile.id)
        mutableState.update {
            it.copy(
                enabled = config.proxy.enabled,
                activeProfileId = config.proxy.activeProfileId,
                groups = if (config.proxy.enabled) it.groups else emptyList(),
                runtimeStatus = ProxyUiMapper.runtimeStatus(
                            enabled = config.proxy.enabled,
                            failClosed = config.proxy.failClosed,
                            transportMode = config.proxy.transportMode,
                            browserState = coordinator.state.value,
                        ),
            )
        }
    }

    fun refreshSubscription(profile: ProfileRecord) = launchAction(
        ProxyAction.RefreshingSubscription(profile.id),
        ProxyErrorCategory.SUBSCRIPTION,
    ) {
        require(profile.sourceType == ProfileSourceType.SUBSCRIPTION)
        val staged = subscriptions.stageRefresh(profile.id)
        val isActive = config.proxy.activeProfileId == profile.id

        if (!isActive || !config.proxy.enabled) {
            profiles.commit(staged)
            return@launchAction
        }

        try {
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
            profiles.markError(
                profile.id,
                SensitiveValueRedactor.redactUrl(error.message.orEmpty()),
            )
            runCatching { coordinator.restartProxy() }
            throw error
        }
    }

    fun setRoutingMode(mode: RoutingMode) = launchAction(
        ProxyAction.ChangingRouting,
        ProxyErrorCategory.RUNTIME,
    ) {
        engine.updateConfig(
            "{\"mode\":\"${mode.name.lowercase()}\"}"
        )
        mutableState.update { it.copy(routingMode = mode) }
    }

    fun selectProxy(group: ProxyGroup, proxyName: String) = launchAction(
        ProxyAction.SwitchingNode(group.name),
        ProxyErrorCategory.RUNTIME,
    ) {
        engine.changeProxy(group.name, proxyName)
        refreshRuntimeInternal()
    }

    fun testDelay(proxyName: String) = launchAction(
        ProxyAction.TestingDelay(proxyName),
        ProxyErrorCategory.RUNTIME,
    ) {
        val delay = engine.testDelay(
            proxyName = proxyName,
            testUrl = DEFAULT_DELAY_URL,
            timeoutMs = 5_000,
        )
        mutableState.update {
            it.copy(delays = it.delays + (proxyName to delay))
        }
    }

    fun refreshRuntime() = launchAction(
        ProxyAction.RefreshingRuntime,
        ProxyErrorCategory.RUNTIME,
    ) {
        refreshRuntimeInternal()
    }

    fun retryProxy() = launchAction(
        ProxyAction.RetryingRuntime,
        ProxyErrorCategory.RUNTIME,
    ) {
        check(config.proxy.enabled) { "Enable Mihomo before retrying the proxy" }
        mutableState.update { it.copy(runtimeStatus = RuntimeUiStatus.Starting) }
        coordinator.restartProxy()
        refreshRuntimeInternal()
    }

    fun useDirectOnce() = launchAction(
        ProxyAction.EnteringTemporaryDirect,
        ProxyErrorCategory.RUNTIME,
    ) {
        coordinator.useDirectTemporarily()
        mutableState.update { it.copy(runtimeStatus = RuntimeUiStatus.TemporaryDirect) }
    }

    fun reportExternalError(
        error: Throwable,
        category: ProxyErrorCategory = ProxyErrorCategory.UNKNOWN,
    ) {
        mutableState.update { it.copy(error = ProxyUiMapper.error(error, category)) }
    }

    fun reportVpnPermissionDenied() {
        mutableState.update {
            it.copy(
                error = ProxyUiError(
                    ProxyErrorCategory.VPN_PERMISSION,
                    "",
                )
            )
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private suspend fun activateInternal(profile: ProfileRecord) {
        config.proxy.activeProfileId = profile.id
        config.proxy.activeProfilePath = profile.filePath
        mutableState.update { it.copy(activeProfileId = profile.id) }
        if (config.proxy.enabled) {
            mutableState.update { it.copy(runtimeStatus = RuntimeUiStatus.Starting) }
            coordinator.restartProxy()
            refreshRuntimeInternal()
        }
    }

    private suspend fun refreshRuntimeInternal() {
        if (!config.proxy.enabled) {
            mutableState.update { it.copy(groups = emptyList()) }
            return
        }

        val catalog = engine.getProxies()
        val order = runCatching { engine.queryProxyGroupOrder() }.getOrDefault(emptyList())
        val rank = order.withIndex().associate { it.value to it.index }
        val groups = catalog.groups.sortedWith(
            compareBy<ProxyGroup> { rank[it.name] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
        mutableState.update {
            it.copy(
                groups = groups,
                traffic = engine.getTraffic(),
            )
        }
    }

    private fun launchAction(
        action: ProxyAction,
        defaultError: ProxyErrorCategory,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.update { it.copy(currentAction = action, error = null) }
            try {
                block()
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        error = mapError(error, defaultError),
                        runtimeStatus = ProxyUiMapper.runtimeStatus(
                            enabled = config.proxy.enabled,
                            failClosed = config.proxy.failClosed,
                            transportMode = config.proxy.transportMode,
                            browserState = coordinator.state.value,
                        ),
                    )
                }
            } finally {
                mutableState.update { it.copy(currentAction = null) }
            }
        }
    }

    private companion object {
        const val DEFAULT_DELAY_URL = "https://www.gstatic.com/generate_204"
    }
}
