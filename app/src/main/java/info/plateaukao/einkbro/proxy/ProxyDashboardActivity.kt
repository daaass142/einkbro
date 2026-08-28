package info.plateaukao.einkbro.proxy

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import info.plateaukao.einkbro.BuildConfig
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.activity.BrowserActivity
import info.plateaukao.einkbro.core.mihomo.runtime.MihomoSessionManager
import info.plateaukao.einkbro.view.EBToast
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.koin.android.ext.android.inject

class ProxyDashboardActivity : FragmentActivity() {
    private val sessionManager: MihomoSessionManager by inject()
    private var dashboardWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = sessionManager.currentSession()
        if (session == null) {
            EBToast.show(this, R.string.proxy_dashboard_requires_running)
            finish()
            return
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
            .addPathHandler(
                "/zashboard/",
                WebViewAssetLoader.AssetsPathHandler(this),
            )
            .build()

        val webView = WebView(this)
        dashboardWebView = webView
        setContentView(webView)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                if (uri.scheme == "http" && uri.host == APP_ASSET_HOST) return false
                openAsBrowserTab(uri)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                // The setup URL contains the controller secret in its fragment.
                // It never enters EinkBro's history DB; clearing this dedicated
                // WebView history also removes it from back/forward navigation.
                view.clearHistory()
            }
        }

        webView.loadUrl(
            dashboardUrl(
                host = session.controllerEndpoint.host,
                port = session.controllerEndpoint.port,
                secret = session.controllerEndpoint.secret,
            )
        )
    }

    private fun dashboardUrl(
        host: String,
        port: Int,
        secret: String,
    ): String {
        fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        return "http://$APP_ASSET_HOST/zashboard/index.html#/setup" +
            "?protocol=http" +
            "&hostname=${encode(host)}" +
            "&port=$port" +
            "&secret=${encode(secret)}" +
            "&disableUpgradeCore=1" +
            "&disableTunMode=1"
    }

    private fun openAsBrowserTab(uri: Uri) {
        startActivity(
            Intent(this, BrowserActivity::class.java).apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, uri.toString())
            }
        )
    }

    override fun onDestroy() {
        dashboardWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        dashboardWebView = null
        super.onDestroy()
    }

    private companion object {
        const val APP_ASSET_HOST = "appassets.androidplatform.net"
    }
}
