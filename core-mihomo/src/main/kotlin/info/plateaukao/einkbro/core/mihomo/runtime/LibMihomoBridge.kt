package info.plateaukao.einkbro.core.mihomo.runtime

import io.github.oviron.libmihomo.Clash

internal interface LibMihomoBridge {
    fun load(nativeLibDir: String)
    fun isLoaded(): Boolean
    fun bridgeAbi(): Int
    fun invokeAction(data: String, callback: (String?) -> Unit)
    fun quickSetup(
        initParams: String,
        setupParams: String,
        callback: (String?) -> Unit,
    )
}

internal object ClashLibMihomoBridge : LibMihomoBridge {
    override fun load(nativeLibDir: String) = Clash.load(nativeLibDir)
    override fun isLoaded(): Boolean = Clash.isLoaded()
    override fun bridgeAbi(): Int = Clash.bridgeABI()

    override fun invokeAction(
        data: String,
        callback: (String?) -> Unit,
    ) = Clash.invokeAction(data, callback)

    override fun quickSetup(
        initParams: String,
        setupParams: String,
        callback: (String?) -> Unit,
    ) = Clash.quickSetup(initParams, setupParams, callback)
}
