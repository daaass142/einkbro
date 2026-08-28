package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LibMihomoLoader(
    private val bridge: LibMihomoBridge,
    private val nativeLibraryDir: () -> String,
    private val expectedBridgeAbi: Int = EXPECTED_BRIDGE_ABI,
) {
    private val mutex = Mutex()

    @Volatile
    private var ready = false

    suspend fun load() {
        if (ready) return

        mutex.withLock {
            if (ready) return

            try {
                bridge.load(nativeLibraryDir())
                if (!bridge.isLoaded()) {
                    throw IllegalStateException("libmihomo bridge reported not loaded")
                }
                val actual = bridge.bridgeAbi()
                if (actual != expectedBridgeAbi) {
                    throw MihomoException.BridgeAbiMismatch(expectedBridgeAbi, actual)
                }
                ready = true
            } catch (error: MihomoException) {
                throw error
            } catch (error: Throwable) {
                throw MihomoException.NativeLoadFailure(error)
            }
        }
    }

    companion object {
        const val EXPECTED_BRIDGE_ABI = 3
    }
}
