package info.plateaukao.einkbro.core.mihomo.config

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

data class RuntimePorts(
    val socksPort: Int,
    val controllerPort: Int,
)

class PortAllocator {
    fun allocate(): RuntimePorts {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket().use { socks ->
            socks.reuseAddress = false
            socks.bind(InetSocketAddress(loopback, 0), 1)
            ServerSocket().use { controller ->
                controller.reuseAddress = false
                controller.bind(InetSocketAddress(loopback, 0), 1)
                return RuntimePorts(
                    socksPort = socks.localPort,
                    controllerPort = controller.localPort,
                )
            }
        }
    }
}
