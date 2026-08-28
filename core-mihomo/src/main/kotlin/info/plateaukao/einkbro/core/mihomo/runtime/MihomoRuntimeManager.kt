package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.api.MihomoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull

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
                mutableState.value == MihomoState.Running
            ) {
                return
            }

            mutableState.value = MihomoState.Loading
            try {
                loader.load()
                mutableState.value = MihomoState.Loaded
            } catch (error: Throwable) {
                val typed = error.asMihomoException("Failed to load mihomo runtime")
                mutableState.value = MihomoState.Failed(typed)
                throw typed
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
            } catch (error: Throwable) {
                val typed = error.asMihomoException("Failed to start mihomo runtime")
                mutableState.value = MihomoState.Failed(typed)
                throw typed
            }
        }
    }

    suspend fun stop() {
        lifecycleMutex.withLock {
            when (mutableState.value) {
                MihomoState.Unloaded,
                MihomoState.Stopped,
                -> return

                else -> Unit
            }

            mutableState.value = MihomoState.Stopping
            try {
                actions.invoke("stopListener", JsonNull)
                mutableState.value = MihomoState.Stopped
            } catch (error: Throwable) {
                val typed = error.asMihomoException("Failed to stop mihomo runtime")
                mutableState.value = MihomoState.Failed(typed)
                throw typed
            }
        }
    }

    private fun Throwable.asMihomoException(message: String): MihomoException =
        this as? MihomoException ?: MihomoException.RuntimeFailure(message, this)
}
