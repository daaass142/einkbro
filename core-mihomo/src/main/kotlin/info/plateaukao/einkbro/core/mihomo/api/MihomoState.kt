package info.plateaukao.einkbro.core.mihomo.api

sealed interface MihomoState {
    data object Unloaded : MihomoState
    data object Loading : MihomoState
    data object Loaded : MihomoState
    data object Starting : MihomoState
    data object Running : MihomoState
    data object Stopping : MihomoState
    data object Stopped : MihomoState
    data class Failed(val error: MihomoException) : MihomoState
}
