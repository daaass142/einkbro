package info.plateaukao.einkbro.core.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface WebViewProxyAdapter {
    fun isSupported(): Boolean
    fun requireSupported()
    suspend fun setProxy(endpoint: BrowserProxyEndpoint)
    suspend fun clearProxy()
}

class AndroidWebViewProxyAdapter(
    context: Context,
) : WebViewProxyAdapter {
    private val appContext = context.applicationContext
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    override fun isSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    override fun requireSupported() {
        check(isSupported()) {
            "Installed Android System WebView does not support ProxyController"
        }
    }

    override suspend fun setProxy(endpoint: BrowserProxyEndpoint) {
        requireSupported()
        require(endpoint.port in 1..65535) { "Invalid proxy port: ${endpoint.port}" }

        val config = ProxyConfig.Builder()
            .addProxyRule("socks://${endpoint.host}:${endpoint.port}")
            .build()

        awaitCallback { done ->
            ProxyController.getInstance().setProxyOverride(
                config,
                mainExecutor,
                done,
            )
        }
    }

    override suspend fun clearProxy() {
        requireSupported()
        awaitCallback { done ->
            ProxyController.getInstance().clearProxyOverride(
                mainExecutor,
                done,
            )
        }
    }

    private suspend fun awaitCallback(
        operation: (Runnable) -> Unit,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            operation(
                Runnable {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            )
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
