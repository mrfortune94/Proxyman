package com.fortunatehtml.android.ui

import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.fortunatehtml.android.R
import com.fortunatehtml.android.proxy.CertificateManager

/**
 * In-app browser activity that routes traffic through the local MITM proxy.
 *
 * The WebView is configured to trust certificates signed by the proxy CA so that
 * HTTPS connections intercepted by the proxy do not produce a
 * net::ERR_CERT_AUTHORITY_INVALID error. Only SSL errors with error code
 * [SslError.SSL_UNTRUSTED] and an issuer matching the proxy CA are accepted;
 * all other SSL errors (expired, hostname mismatch, etc.) are rejected.
 *
 * Because the VPN service restricts its tunnel to this app's package, only
 * traffic from this in-app browser is routed through the proxy.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var certManager: CertificateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        certManager = CertificateManager(this)
        certManager.initialize()

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupWebView()
        setupUrlBar()
        setupBackHandler()

        webView.loadUrl(DEFAULT_URL)
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
        }

        webView.webViewClient = object : WebViewClient() {
            /**
             * Accept SSL errors that originate from the proxy's CA-signed certificate.
             * This resolves net::ERR_CERT_AUTHORITY_INVALID for in-app browser traffic
             * flowing through the MITM proxy. All other SSL errors are rejected.
             */
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                if (isTrustedByProxyCa(error)) {
                    handler.proceed()
                } else {
                    handler.cancel()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
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
        val trimmed = url.trim()
        // Only allow http and https schemes to prevent file:// and javascript: injection.
        val formattedUrl = when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("file://") || trimmed.startsWith("javascript:") -> return
            else -> "https://$trimmed"
        }
        webView.loadUrl(formattedUrl)
    }

    /**
     * Returns true when the SSL error is solely [SslError.SSL_UNTRUSTED] and the
     * certificate was issued by the proxy CA. This is the expected error for every
     * HTTPS host whose certificate was dynamically generated and signed by the proxy.
     */
    fun isTrustedByProxyCa(error: SslError): Boolean {
        return isTrustedByProxyCa(error.primaryError, error.certificate.issuedBy?.cName)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val DEFAULT_URL = "https://www.google.com"

        /** Common name used in the subject of the proxy's self-signed CA certificate. */
        const val PROXY_CA_CN = "Fortunate HTML CA"

        /**
         * Core trust-check logic, parameterised so it can be exercised by unit tests
         * without depending on Android's [android.net.http.SslError] stubs.
         *
         * @param primaryError the value of [SslError.primaryError]
         * @param issuerCn     the CN from [SslError.certificate]'s [SslCertificate.issuedBy]
         */        internal fun isTrustedByProxyCa(primaryError: Int, issuerCn: String?): Boolean {
            return primaryError == SslError.SSL_UNTRUSTED && issuerCn == PROXY_CA_CN
        }
    }
}
