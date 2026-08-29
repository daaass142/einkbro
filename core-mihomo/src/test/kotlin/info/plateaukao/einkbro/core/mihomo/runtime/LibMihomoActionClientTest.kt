package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibMihomoActionClientTest {
    @Test
    fun parsesSuccessfulActionEnvelope() = runTest {
        val bridge = CallbackBridge(
            """{"id":"1","method":"startListener","data":true,"code":0}"""
        )
        val client = LibMihomoActionClient(bridge)

        val result = client.invoke("startListener", JsonNull)

        assertTrue(result.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun actionErrorBecomesTypedFailure() = runTest {
        val bridge = CallbackBridge(
            """{"id":"1","method":"changeProxy","data":"group not found","code":-1}"""
        )
        val client = LibMihomoActionClient(bridge)

        val error = runCatching { client.invoke("changeProxy") }.exceptionOrNull()

        assertTrue(error is MihomoException.ActionFailure)
    }

    @Test
    fun malformedResponseDoesNotEscapeAsJsonException() = runTest {
        val client = LibMihomoActionClient(CallbackBridge("not-json"))

        val error = runCatching { client.invoke("getProxies") }.exceptionOrNull()

        assertTrue(error is MihomoException.MalformedResponse)
    }

    private class CallbackBridge(
        private val result: String,
    ) : LibMihomoBridge {
        override fun load(nativeLibDir: String) = Unit
        override fun isLoaded(): Boolean = true
        override fun bridgeAbi(): Int = 3
        override fun invokeAction(data: String, callback: (String?) -> Unit) = callback(result)
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
