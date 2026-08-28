package info.plateaukao.einkbro.proxy

internal object ProxyNodeFilter {
    fun filter(
        nodes: List<String>,
        query: String,
    ): List<String> {
        val normalized = query.trim()
        if (normalized.isBlank()) return nodes
        return nodes.filter { it.contains(normalized, ignoreCase = true) }
    }
}
