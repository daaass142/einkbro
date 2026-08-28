package info.plateaukao.einkbro.core.mihomo.api

data class ProxyEndpoint(
    val host: String,
    val port: Int,
)

data class ControllerEndpoint(
    val host: String,
    val port: Int,
    val secret: String,
)

data class ProxyNode(
    val name: String,
    val type: String,
    val udp: Boolean?,
    val alive: Boolean?,
    val delayMs: Int?,
)

data class ProxyGroup(
    val name: String,
    val type: String,
    val selected: String?,
    val proxies: List<String>,
)

data class ProxyCatalog(
    val groups: List<ProxyGroup>,
    val nodes: List<ProxyNode>,
)

data class TrafficSnapshot(
    val uploadBytes: Long,
    val downloadBytes: Long,
)

data class ProxyConnection(
    val id: String,
    val host: String?,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val chains: List<String>,
)

enum class RoutingMode {
    RULE,
    GLOBAL,
    DIRECT,
}

data class MihomoProfile(
    val id: String,
    val name: String,
    val path: String,
)
