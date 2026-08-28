package info.plateaukao.einkbro.core.mihomo.api

data class MihomoTunConfig(
    val device: String = "einkbro-vpn",
    val stack: String = "mixed",
    val address: String,
    val dns: String,
    val mtu: Int = 1400,
)

interface MihomoTunController {
    fun startTun(
        fd: Int,
        config: MihomoTunConfig,
        protect: (fd: Int) -> Unit,
        resolverProcess: (
            protocol: Int,
            source: String,
            target: String,
            uid: Int,
        ) -> String,
    )

    fun stopTun()
    fun setSuspended(suspended: Boolean)
}
