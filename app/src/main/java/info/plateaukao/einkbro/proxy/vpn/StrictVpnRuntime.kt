package info.plateaukao.einkbro.proxy.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface StrictVpnState {
    data object Stopped : StrictVpnState
    data object Starting : StrictVpnState
    data object Running : StrictVpnState
    data class Failed(val error: Throwable) : StrictVpnState
}

class StrictVpnRuntime {
    private val mutableState = MutableStateFlow<StrictVpnState>(StrictVpnState.Stopped)
    val state: StateFlow<StrictVpnState> = mutableState.asStateFlow()

    fun starting() { mutableState.value = StrictVpnState.Starting }
    fun running() { mutableState.value = StrictVpnState.Running }
    fun stopped() { mutableState.value = StrictVpnState.Stopped }
    fun failed(error: Throwable) { mutableState.value = StrictVpnState.Failed(error) }
}
