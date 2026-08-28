package info.plateaukao.einkbro.core.mihomo.api

data class MihomoSession(
    val profile: MihomoProfile,
    val socksEndpoint: ProxyEndpoint,
    val controllerEndpoint: ControllerEndpoint,
)

sealed interface MihomoSessionState {
    data object Stopped : MihomoSessionState
    data class Starting(val profile: MihomoProfile) : MihomoSessionState
    data class Running(val session: MihomoSession) : MihomoSessionState
    data class Failed(
        val profile: MihomoProfile?,
        val error: MihomoException,
    ) : MihomoSessionState
}
