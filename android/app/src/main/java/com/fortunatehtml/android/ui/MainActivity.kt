package com.fortunatehtml.android.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager
import com.fortunatehtml.android.model.TrafficEntry
import com.fortunatehtml.android.ui.fragment.*

/**
 * Main split-screen activity:
 * - Top half: embedded WebView browser
 * - Bottom half: tabbed API inspector panels
 */
class MainActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var currentUrlText: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        // Apply screenshot restriction if enabled in settings
        if (preferencesManager.disableScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        setupWebView()
        setupInspectorTabs()
    }

    private fun setupWebView() {
        webView        = findViewById(R.id.webView)
        urlBar         = findViewById(R.id.urlBar)
        progressBar    = findViewById(R.id.webProgressBar)
        btnBack        = findViewById(R.id.btnBack)
        btnForward     = findViewById(R.id.btnForward)
        btnRefresh     = findViewById(R.id.btnRefresh)
        currentUrlText = findViewById(R.id.currentUrl)

        val trafficRepository = (application as FortunateHtmlApp).trafficRepository

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String,
                                       favicon: android.graphics.Bitmap?) {
                currentUrlText.text  = url
                btnBack.isEnabled    = webView.canGoBack()
                btnForward.isEnabled = webView.canGoForward()
                // Record navigation event (URL only – WebView does not expose
                // full request/response data to the host app)
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
                urlBar.setText(url)
                currentUrlText.text  = url
                btnBack.isEnabled    = webView.canGoBack()
                btnForward.isEnabled = webView.canGoForward()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress   = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        urlBar.setOnEditorActionListener { _, _, _ ->
            navigateWebView(urlBar.text.toString())
            true
        }

        btnBack.setOnClickListener    { if (webView.canGoBack())    webView.goBack()    }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnRefresh.setOnClickListener { webView.reload() }

        webView.loadUrl(DEFAULT_URL)
    }

    private fun setupInspectorTabs() {
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        val tabTitles = listOf(
            R.string.tab_live,
            R.string.tab_request,
            R.string.tab_response,
            R.string.tab_repeater,
            R.string.tab_history,
            R.string.tab_scope,
            R.string.tab_projects,
            R.string.tab_logs,
            R.string.tab_settings
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabTitles.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> LiveTrafficFragment()
                1 -> RequestDetailFragment()
                2 -> ResponseDetailFragment()
                3 -> RepeaterFragment()
                4 -> HistoryFragment()
                5 -> ScopeFragment()
                6 -> ProjectsFragment()
                7 -> LogsFragment()
                8 -> SettingsFragment()
                else -> LiveTrafficFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(tabTitles[position])
        }.attach()
    }

    private fun navigateWebView(url: String) {
        val trimmed = url.trim()
        // Allow only http and https to prevent injection via file://, javascript:,
        // data:, blob:, content:, or other potentially dangerous URI schemes.
        val formatted = when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("file://")   ||
            trimmed.startsWith("javascript:") ||
            trimmed.startsWith("data:")    ||
            trimmed.startsWith("blob:")    ||
            trimmed.startsWith("content:") -> return
            else -> "https://$trimmed"
        }
        webView.loadUrl(formatted)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_har -> {
                // HAR import handled by HistoryFragment
                viewPager.currentItem = 4 // jump to History tab
                true
            }
            R.id.action_clear -> {
                (application as FortunateHtmlApp).trafficRepository.clear()
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_message)
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val DEFAULT_URL = "about:blank"
    }

}
