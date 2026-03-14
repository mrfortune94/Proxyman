package com.fortunatehtml.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager
import com.fortunatehtml.android.proxy.CertificateManager
import com.fortunatehtml.android.proxy.ProxyVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var trafficAdapter: TrafficAdapter
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private var isProxyRunning = false
    private lateinit var preferencesManager: PreferencesManager

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startProxyService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "Fortunate HTML"

        recyclerView = findViewById(R.id.trafficRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        fab = findViewById(R.id.fab)

        trafficAdapter = TrafficAdapter { entry ->
            val intent = Intent(this, TrafficDetailActivity::class.java)
            intent.putExtra(TrafficDetailActivity.EXTRA_ENTRY_ID, entry.id)
            startActivity(intent)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = trafficAdapter
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }

        val app = application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(this) { entries ->
            trafficAdapter.submitList(entries)
            emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }

        fab.setOnClickListener {
            if (isProxyRunning) {
                stopProxyService()
            } else {
                requestVpnPermission()
            }
        }

        updateFabState()
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startProxyService()
        }
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        isProxyRunning = true
        updateFabState()
    }

    private fun stopProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        startService(intent)
        isProxyRunning = false
        updateFabState()
    }

    private fun updateFabState() {
        if (isProxyRunning) {
            fab.setImageResource(R.drawable.ic_stop)
            fab.contentDescription = "Stop Proxy"
        } else {
            fab.setImageResource(R.drawable.ic_play)
            fab.contentDescription = "Start Proxy"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_browser -> {
                startActivity(Intent(this, BrowserActivity::class.java))
                true
            }
            R.id.action_clear -> {
                (application as FortunateHtmlApp).trafficRepository.clear()
                true
            }
            R.id.action_export_cert -> {
                exportCertificate()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportCertificate() {
        val certManager = CertificateManager(this)
        certManager.initialize()
        val pem = certManager.exportCACertificatePem()
        if (pem != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-pem-file"
                putExtra(Intent.EXTRA_TEXT, pem)
                putExtra(Intent.EXTRA_SUBJECT, "Fortunate HTML CA Certificate")
            }
            startActivity(Intent.createChooser(shareIntent, "Export CA Certificate"))
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Fortunate HTML")
            .setMessage("Fortunate HTML for Android v1.0.0\n\nA network debugging proxy with MITM capability.\n\nThis tool is for authorized use only.")
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
}
