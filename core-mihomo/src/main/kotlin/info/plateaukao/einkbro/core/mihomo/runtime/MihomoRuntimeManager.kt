package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.api.MihomoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MihomoRuntimeManager(
    private val loader: LibMihomoLoader,
    private val actions: MihomoActionClient,
) {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<MihomoState>(MihomoState.Unloaded)

    val state: StateFlow<MihomoState> = mutableState.asStateFlow()

    suspend fun load() {
        lifecycleMutex.withLock {
            if (
                mutableState.value == MihomoState.Loaded ||
                mutableState.value == MihomoState.Running ||
                mutableState.value == MihomoState.Stopped
            ) {
                return
            }

            mutableState.value = MihomoState.Loading
            try {
                loader.load()
                mutableState.value = MihomoState.Loaded
            } catch (error: MihomoException) {
                mutableState.value = MihomoState.Failed(error)
                throw error
            }
        }
    }

    suspend fun start(
        homeDir: String,
        platformVersion: Int,
        selectedMap: Map<String, String>,
    ) {
        lifecycleMutex.withLock {
            if (mutableState.value == MihomoState.Running) return

            mutableState.value = MihomoState.Starting
            try {
                loader.load()
                actions.quickSetup(homeDir, platformVersion, selectedMap)
                mutableState.value = MihomoState.Running
            } catch (error: MihomoException) {
                mutableState.value = MihomoState.Failed(error)
                throw error
            } catch (error: Throwable) {
                val wrapped = MihomoException.RuntimeFailure(
                    "Failed to start mihomo runtime",
                    error,
                )
                mutableState.value = MihomoState.Failed(wrapped)
                throw wrapped
            }
        }
    }

    suspend fun stop() {
        lifecycleMutex.withLock {
            if (
                mutableState.value == MihomoState.Stopped ||
                mutableState.value == MihomoState.Unloaded
            ) {
                mutableState.value = MihomoState.Stopped
                return
            }

            mutableState.value = MihomoState.Stopping
            try {
                actions.invoke("stopListener", kotlinx.serialization.json.JsonNull)
                mutableState.value = MihomoState.Stopped
            } catch (error: MihomoException) {
                mutableState.value = MihomoState.Failed(error)
                throw error
            }
        }
    }
}
