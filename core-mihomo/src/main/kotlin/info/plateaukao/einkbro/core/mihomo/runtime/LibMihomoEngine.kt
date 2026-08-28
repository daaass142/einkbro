package info.plateaukao.einkbro.core.mihomo.runtime

import android.content.Context
import info.plateaukao.einkbro.core.mihomo.api.MihomoEngine
import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.api.MihomoState
import info.plateaukao.einkbro.core.mihomo.api.MihomoTunConfig
import info.plateaukao.einkbro.core.mihomo.api.MihomoTunController
import info.plateaukao.einkbro.core.mihomo.api.ProxyCatalog
import info.plateaukao.einkbro.core.mihomo.api.ProxyConnection
import info.plateaukao.einkbro.core.mihomo.api.TrafficSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LibMihomoEngine private constructor(
    private val runtimeManager: MihomoRuntimeManager,
    private val actions: LibMihomoActionClient,
    private val bridge: LibMihomoBridge,
) : MihomoEngine, MihomoTunController {
    override val state: StateFlow<MihomoState> = runtimeManager.state

    override suspend fun load() = runtimeManager.load()

    override suspend fun start(
        homeDir: String,
        platformVersion: Int,
        selectedMap: Map<String, String>,
    ) = runtimeManager.start(homeDir, platformVersion, selectedMap)

    override suspend fun stop() = runtimeManager.stop()

    override suspend fun getProxies(): ProxyCatalog {
        val data = actions.invoke("getProxies")
        return LibMihomoMapper.proxyCatalog(actions.parseNestedJson("getProxies", data))
    }

    override suspend fun changeProxy(
        groupName: String,
        proxyName: String,
    ) {
        val params = buildJsonObject {
            put("group-name", groupName)
            put("proxy-name", proxyName)
        }.toString()
        actions.invoke("changeProxy", JsonPrimitive(params))
    }

    override suspend fun testDelay(
        proxyName: String,
        testUrl: String,
        timeoutMs: Long,
    ): Int {
        val params = buildJsonObject {
            put("proxy-name", proxyName)
            put("test-url", testUrl)
            put("timeout", timeoutMs)
        }.toString()
        return actions.invoke("testDelay", JsonPrimitive(params))
            .jsonPrimitive
            .intOrNull
            ?: -1
    }

    override suspend fun getTraffic(): TrafficSnapshot {
        val data = actions.invoke("getTraffic")
        return LibMihomoMapper.traffic(actions.parseNestedJson("getTraffic", data))
    }

    override suspend fun getTotalTraffic(): TrafficSnapshot {
        val data = actions.invoke("getTotalTraffic")
        return LibMihomoMapper.traffic(actions.parseNestedJson("getTotalTraffic", data))
    }

    override suspend fun getConnections(): List<ProxyConnection> {
        val data = actions.invoke("getConnections")
        return LibMihomoMapper.connections(actions.parseNestedJson("getConnections", data))
    }

    override suspend fun closeConnection(id: String): Boolean =
        actions.invoke("closeConnection", JsonPrimitive(id))
            .jsonPrimitive
            .booleanOrNull
            ?: false

    override suspend fun closeAllConnections(): Boolean =
        actions.invoke("closeAllConnections")
            .jsonPrimitive
            .booleanOrNull
            ?: false

    override suspend fun queryProxyGroupOrder(): List<String> {
        val data = actions.invoke("queryProxyGroupOrder")
        return actions.parseNestedJson("queryProxyGroupOrder", data)
            .jsonArray
            .mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    override suspend fun validateConfig(path: String) {
        val result = actions.invoke("validateConfig", JsonPrimitive(path))
        val error = (result as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (error.isNotEmpty()) throw MihomoException.InvalidProfile(error)
    }

    override suspend fun setupConfig(selectedMap: Map<String, String>) {
        val setup = buildJsonObject {
            put(
                "selected-map",
                JsonObject(selectedMap.mapValues { JsonPrimitive(it.value) }),
            )
        }.toString()
        val result = actions.invoke("setupConfig", JsonPrimitive(setup))
        val error = (result as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (error.isNotEmpty()) {
            throw MihomoException.RuntimeFailure("mihomo setupConfig failed: " + error)
        }
    }

    override suspend fun updateConfig(updateJson: String) {
        val result = actions.invoke("updateConfig", JsonPrimitive(updateJson))
        val error = (result as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (error.isNotEmpty()) {
            throw MihomoException.RuntimeFailure("mihomo updateConfig failed: " + error)
        }
    }

    override suspend fun startListener() {
        actions.invoke("startListener", JsonNull)
    }

    override suspend fun stopListener() {
        actions.invoke("stopListener", JsonNull)
    }

    override fun startTun(
        fd: Int,
        config: MihomoTunConfig,
        protect: (Int) -> Unit,
        resolverProcess: (Int, String, String, Int) -> String,
    ) {
        bridge.startTun(
            fd = fd,
            protect = protect,
            resolverProcess = resolverProcess,
            device = config.device,
            stack = config.stack,
            address = config.address,
            dns = config.dns,
            mtu = config.mtu,
        )
    }

    override fun stopTun() = bridge.stopTun()

    override fun setSuspended(suspended: Boolean) = bridge.suspended(suspended)

    companion object {
        fun create(context: Context): LibMihomoEngine {
            val bridge = ClashLibMihomoBridge
            val loader = LibMihomoLoader(
                bridge = bridge,
                nativeLibraryDir = { context.applicationInfo.nativeLibraryDir },
            )
            val actions = LibMihomoActionClient(bridge)
            val manager = MihomoRuntimeManager(loader, actions)
            return LibMihomoEngine(manager, actions, bridge)
        }
    }
}
