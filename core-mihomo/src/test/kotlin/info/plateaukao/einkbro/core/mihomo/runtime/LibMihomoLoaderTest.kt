package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibMihomoLoaderTest {
    @Test
    fun loadIsIdempotentWhenBridgeAbiMatches() = runTest {
        val bridge = FakeBridge(abi = 3)
        val loader = LibMihomoLoader(bridge, nativeLibraryDir = { "/native" })

        loader.load()
        loader.load()

        assertEquals(1, bridge.loadCalls)
        assertTrue(bridge.isLoaded())
    }

    @Test
    fun bridgeAbiMismatchFailsClosed() = runTest {
        val bridge = FakeBridge(abi = 99)
        val loader = LibMihomoLoader(bridge, nativeLibraryDir = { "/native" })

        val error = runCatching { loader.load() }.exceptionOrNull()

        assertTrue(error is MihomoException.BridgeAbiMismatch)
    }

    private class FakeBridge(
        private val abi: Int,
    ) : LibMihomoBridge {
        var loadCalls = 0
        private var loaded = false

        override fun load(nativeLibDir: String) {
            loadCalls += 1
            loaded = true
        }

        override fun isLoaded(): Boolean = loaded
        override fun bridgeAbi(): Int = abi
        override fun invokeAction(data: String, callback: (String?) -> Unit) = Unit
        override fun quickSetup(
            initParams: String,
            setupParams: String,
            callback: (String?) -> Unit,
        ) = callback("")

        override fun startTun(
            fd: Int,
            protect: (Int) -> Unit,
            resolverProcess: (Int, String, String, Int) -> String,
            device: String,
            stack: String,
            address: String,
            dns: String,
            mtu: Int,
        ) = Unit

        override fun stopTun() = Unit

        override fun suspended(suspended: Boolean) = Unit
    }
}
