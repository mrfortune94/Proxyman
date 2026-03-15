package com.fortunatehtml.android.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.util.UrlUtils
import com.fortunatehtml.android.data.TrafficRepository
import com.fortunatehtml.android.model.TrafficEntry

/**
 * In-app browser activity backed by an Android WebView.
 *
 * Traffic visibility note:
 * Android's WebView does not expose full request/response bodies to the host app.
 * We can observe:
 *   - page navigations via shouldOverrideUrlLoading / onPageStarted / onPageFinished
 *   - resource loads via onLoadResource (URL only, no headers or body)
 *
 * Full request/response inspection requires using the DevTools Protocol via
 * a debug build of Chrome – that is outside the scope of this app's WebView capture.
 *
 * Standard TLS validation is enforced; there is no SSL bypass.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var trafficRepository: TrafficRepository
    
    // Track current URL for state retention
    private var currentUrl: String = DEFAULT_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        trafficRepository = (application as FortunateHtmlApp).trafficRepository

        webView     = findViewById(R.id.webView)
        urlBar      = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupWebView()
        setupUrlBar()
        setupBackHandler()

        // Restore URL from saved state (rotation/config change) or get from Intent
        val urlFromState = savedInstanceState?.getString(KEY_CURRENT_URL)
        val urlFromIntent = intent.getStringExtra(EXTRA_URL)
        currentUrl = urlFromState ?: urlFromIntent ?: DEFAULT_URL

        // Restore WebView state if available (preserves scroll position, form data, etc.)
        // Otherwise load the URL
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(currentUrl)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current URL to preserve state during rotation/config change
        outState.putString(KEY_CURRENT_URL, currentUrl)
        // Save WebView state for full restoration (scroll position, form data, etc.)
        webView.saveState(outState)
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.browserToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.browser_title)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Enable mixed content mode for compatibility (allows audio/video/images over HTTP)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Enable additional settings for better browser experience
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Allow content and file access (file URLs are blocked by UrlUtils)
            allowContentAccess = true
            allowFileAccess = false  // Blocked for security
            // Enable caching
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            // Enable media playback
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String,
                                       favicon: android.graphics.Bitmap?) {
                currentUrl = url  // Track URL for state retention
                // Record page navigation as a WebView-sourced traffic entry (URL only)
                val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return
                trafficRepository.addEntry(
                    TrafficEntry(
                        method  = "GET",
                        url     = url,
                        host    = uri.host ?: "",
                        path    = uri.path ?: "/",
                        scheme  = uri.scheme ?: "https",
                        isHttps = uri.scheme == "https"
                    )
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url  // Track URL for state retention
                urlBar.setText(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupUrlBar() {
        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateTo(urlBar.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun navigateTo(url: String) {
        // UrlUtils.sanitise() blocks dangerous schemes (file://, javascript:, data:, blob:, content:)
        val formattedUrl = UrlUtils.sanitise(url) ?: return
        webView.loadUrl(formattedUrl)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val DEFAULT_URL = "about:blank"
        private const val KEY_CURRENT_URL = "current_browser_url"
        const val EXTRA_URL = "extra_url"  // Intent extra key for URL
    }
}
