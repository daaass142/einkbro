package info.plateaukao.einkbro.proxy.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class StrictVpnController(
    context: Context,
    private val runtime: StrictVpnRuntime,
) {
    private val app = context.applicationContext
    val state: StateFlow<StrictVpnState> = runtime.state

    fun start() {
        if (state.value == StrictVpnState.Running || state.value == StrictVpnState.Starting) return
        runtime.starting()
        ContextCompat.startForegroundService(
            app,
            Intent(app, MihomoVpnService::class.java).setAction(MihomoVpnService.ACTION_START),
        )
    }

    fun stop() {
        app.stopService(Intent(app, MihomoVpnService::class.java))
    }

    suspend fun awaitRunning(timeoutMs: Long = 8_000) {
        val resolved = withTimeout(timeoutMs) {
            state.first { it is StrictVpnState.Running || it is StrictVpnState.Failed }
        }
        if (resolved is StrictVpnState.Failed) throw resolved.error
    }
}
