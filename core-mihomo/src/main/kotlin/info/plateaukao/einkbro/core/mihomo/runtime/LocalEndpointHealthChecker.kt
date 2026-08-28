package info.plateaukao.einkbro.core.mihomo.runtime

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class LocalEndpointHealthChecker {
    suspend fun awaitListening(
        host: String,
        port: Int,
        timeoutMs: Long = 4_000,
    ) {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000
        var lastError: Throwable? = null

        while (System.nanoTime() < deadlineNanos) {
            try {
                withContext(Dispatchers.IO) {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 250)
                    }
                }
                return
            } catch (error: Throwable) {
                lastError = error
                delay(60)
            }
        }

        throw IllegalStateException(
            "Local endpoint $host:$port did not become ready",
            lastError,
        )
    }
}
