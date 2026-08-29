package info.plateaukao.einkbro.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyNodeFilterTest {
    @Test
    fun blankQueryReturnsOriginalLargeListWithoutCopy() {
        val nodes = (1..600).map { "Node-$it" }

        val result = ProxyNodeFilter.filter(nodes, "   ")

        assertSame(nodes, result)
        assertEquals(600, result.size)
    }

    @Test
    fun searchIsCaseInsensitiveAndPreservesOrder() {
        val nodes = listOf(
            "JP-Tokyo-01",
            "SG-01",
            "jp-Osaka-02",
            "US-LA-01",
        )

        val result = ProxyNodeFilter.filter(nodes, " JP- ")

        assertEquals(listOf("JP-Tokyo-01", "jp-Osaka-02"), result)
    }

    @Test
    fun largeListFilteringReturnsOnlyMatches() {
        val nodes = buildList {
            repeat(500) { add("Regular-$it") }
            repeat(120) { add("Streaming-$it") }
        }

        val result = ProxyNodeFilter.filter(nodes, "streaming")

        assertEquals(120, result.size)
        assertTrue(result.all { it.startsWith("Streaming-") })
    }
}
