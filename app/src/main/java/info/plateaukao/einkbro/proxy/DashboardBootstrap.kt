package info.plateaukao.einkbro.proxy

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object DashboardBootstrap {
    fun url(
        host: String,
        port: Int,
        secret: String,
    ): String {
        require(host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
            "Zashboard controller must be loopback-only"
        }
        require(port in 1..65535) { "Invalid controller port" }
        require(secret.isNotBlank()) { "Controller secret must not be blank" }

        fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        return "http://${DashboardNavigationPolicy.APP_ASSET_HOST}/zashboard/index.html#/setup" +
            "?protocol=http" +
            "&hostname=${encode(host)}" +
            "&port=$port" +
            "&secret=${encode(secret)}" +
            "&disableUpgradeCore=1" +
            "&disableTunMode=1"
    }
}
