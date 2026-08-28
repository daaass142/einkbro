package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.ProxyCatalog
import info.plateaukao.einkbro.core.mihomo.api.ProxyConnection
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.api.ProxyNode
import info.plateaukao.einkbro.core.mihomo.api.TrafficSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object LibMihomoMapper {
    fun proxyCatalog(root: JsonElement): ProxyCatalog {
        val proxies = root.jsonObject["proxies"]?.jsonObject.orEmpty()
        val groups = mutableListOf<ProxyGroup>()
        val nodes = mutableListOf<ProxyNode>()

        for ((fallbackName, value) in proxies) {
            val item = value as? JsonObject ?: continue
            val name = item.string("name") ?: fallbackName
            val type = item.string("type") ?: "Unknown"
            val all = item["all"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()

            if (all.isNotEmpty()) {
                groups += ProxyGroup(
                    name = name,
                    type = type,
                    selected = item.string("now"),
                    proxies = all,
                )
            } else {
                val history = item["history"]?.jsonArray
                val delay = history
                    ?.lastOrNull()
                    ?.jsonObject
                    ?.get("delay")
                    ?.jsonPrimitive
                    ?.intOrNull

                nodes += ProxyNode(
                    name = name,
                    type = type,
                    udp = item["udp"]?.jsonPrimitive?.booleanOrNull,
                    alive = item["alive"]?.jsonPrimitive?.booleanOrNull,
                    delayMs = delay,
                )
            }
        }

        return ProxyCatalog(groups = groups, nodes = nodes)
    }

    fun traffic(root: JsonElement): TrafficSnapshot {
        val obj = root.jsonObject
        return TrafficSnapshot(
            uploadBytes = obj["up"]?.jsonPrimitive?.longOrNull ?: 0L,
            downloadBytes = obj["down"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    fun connections(root: JsonElement): List<ProxyConnection> {
        val array = root.jsonObject["connections"]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val metadata = obj["metadata"] as? JsonObject
            ProxyConnection(
                id = id,
                host = metadata?.string("host")
                    ?: metadata?.string("destinationIP"),
                uploadBytes = obj["upload"]?.jsonPrimitive?.longOrNull ?: 0L,
                downloadBytes = obj["download"]?.jsonPrimitive?.longOrNull ?: 0L,
                chains = obj["chains"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty(),
            )
        }
    }

    private fun Map<String, JsonElement>.orEmpty(): Map<String, JsonElement> = this

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull
}
