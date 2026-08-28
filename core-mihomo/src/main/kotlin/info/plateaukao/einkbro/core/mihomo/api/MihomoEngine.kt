package info.plateaukao.einkbro.core.mihomo.api

import kotlinx.coroutines.flow.StateFlow

interface MihomoEngine {
    val state: StateFlow<MihomoState>

    suspend fun load()
    suspend fun start(
        homeDir: String,
        platformVersion: Int,
        selectedMap: Map<String, String> = emptyMap(),
    )
    suspend fun stop()

    suspend fun getProxies(): ProxyCatalog
    suspend fun changeProxy(groupName: String, proxyName: String)
    suspend fun testDelay(
        proxyName: String,
        testUrl: String,
        timeoutMs: Long = 5_000,
    ): Int
    suspend fun getTraffic(): TrafficSnapshot
    suspend fun getTotalTraffic(): TrafficSnapshot
    suspend fun getConnections(): List<ProxyConnection>
    suspend fun closeConnection(id: String): Boolean
    suspend fun closeAllConnections(): Boolean
    suspend fun queryProxyGroupOrder(): List<String>
    suspend fun validateConfig(path: String)
    suspend fun setupConfig(selectedMap: Map<String, String> = emptyMap())
    suspend fun updateConfig(updateJson: String)
    suspend fun startListener()
    suspend fun stopListener()
}
