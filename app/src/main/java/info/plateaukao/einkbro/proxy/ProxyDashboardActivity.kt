package info.plateaukao.einkbro.proxy

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.webkit.WebResourceErrorCompat
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

        // Keep the appassets page on HTTP because Mihomo's loopback controller
        // is HTTP. Using an HTTPS appassets origin would turn controller XHR into
        // mixed content and WebView would block it under MIXED_CONTENT_NEVER_ALLOW.
        val assetLoader = WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
            .addPathHandler(
                "/zashboard/",
                WebViewAssetLoader.AssetsPathHandler(this),
            )
            .build()

        val webView = WebView(this)
        dashboardWebView = webView
        val loadingView = TextView(this).apply {
            text = getString(R.string.proxy_dashboard_loading)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val root = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                loadingView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(root)

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
                return when (
                    DashboardNavigationPolicy.classify(request.url.toString())
                ) {
                    DashboardNavigationAction.ALLOW_INTERNAL -> false
                    DashboardNavigationAction.BLOCK_LOOPBACK -> true
                    DashboardNavigationAction.OPEN_EXTERNAL -> {
                        openAsBrowserTab(request.url)
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == DashboardNavigationPolicy.APP_ASSET_HOST) {
                    loadingView.visibility = View.GONE
                }
                // The setup URL contains the controller secret in its fragment.
                // It never enters EinkBro's history DB; clearing this dedicated
                // WebView history also removes it from back/forward navigation.
                view.clearHistory()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat,
            ) {
                if (request.isForMainFrame) {
                    loadingView.text = getString(R.string.proxy_dashboard_load_failed)
                    loadingView.visibility = View.VISIBLE
                }
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

        return "http://${DashboardNavigationPolicy.APP_ASSET_HOST}/zashboard/index.html#/setup" +
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

}
