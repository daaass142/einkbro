package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MihomoRuntimeManagerTest {
    @Test
    fun concurrentStartsInitializeOneRuntime() = runTest {
        val bridge = CountingBridge()
        val loader = LibMihomoLoader(bridge, nativeLibraryDir = { "/native" })
        val actions = CountingActionClient()
        val manager = MihomoRuntimeManager(loader, actions)

        List(8) {
            async { manager.start("/home", 36, emptyMap()) }
        }.awaitAll()

        assertEquals(1, bridge.loadCalls)
        assertEquals(1, actions.quickSetupCalls)
        assertSame(MihomoState.Running, manager.state.value)
    }

    @Test
    fun repeatedStopIsSafe() = runTest {
        val bridge = CountingBridge()
        val loader = LibMihomoLoader(bridge, nativeLibraryDir = { "/native" })
        val actions = CountingActionClient()
        val manager = MihomoRuntimeManager(loader, actions)

        manager.start("/home", 36, emptyMap())
        manager.stop()
        manager.stop()

        assertEquals(1, actions.stopCalls)
        assertSame(MihomoState.Stopped, manager.state.value)
    }

    @Test
    fun stopBeforeLoadDoesNotPretendRuntimeWasLoaded() = runTest {
        val bridge = CountingBridge()
        val manager = MihomoRuntimeManager(
            LibMihomoLoader(bridge, nativeLibraryDir = { "/native" }),
            CountingActionClient(),
        )

        manager.stop()

        assertEquals(0, bridge.loadCalls)
        assertSame(MihomoState.Unloaded, manager.state.value)
    }

    @Test
    fun loadAfterStoppedStateRevalidatesLoaderState() = runTest {
        val bridge = CountingBridge()
        val manager = MihomoRuntimeManager(
            LibMihomoLoader(bridge, nativeLibraryDir = { "/native" }),
            CountingActionClient(),
        )

        manager.start("/home", 36, emptyMap())
        manager.stop()
        manager.load()

        assertEquals(1, bridge.loadCalls)
        assertSame(MihomoState.Loaded, manager.state.value)
    }

    private class CountingBridge : LibMihomoBridge {
        var loadCalls = 0
        private var loaded = false

        override fun load(nativeLibDir: String) {
            loadCalls += 1
            loaded = true
        }

        override fun isLoaded(): Boolean = loaded
        override fun bridgeAbi(): Int = 3
        override fun invokeAction(data: String, callback: (String?) -> Unit) = callback(
            """{"id":"1","method":"noop","data":true,"code":0}"""
        )
        override fun quickSetup(
            initParams: String,
            setupParams: String,
            callback: (String?) -> Unit,
        ) = callback("")
    }

    private class CountingActionClient : MihomoActionClient {
        var quickSetupCalls = 0
        var stopCalls = 0

        override suspend fun invoke(method: String, data: JsonElement): JsonElement {
            if (method == "stopListener") stopCalls += 1
            return JsonNull
        }

        override suspend fun quickSetup(
            homeDir: String,
            platformVersion: Int,
            selectedMap: Map<String, String>,
        ) {
            quickSetupCalls += 1
        }
    }
}
