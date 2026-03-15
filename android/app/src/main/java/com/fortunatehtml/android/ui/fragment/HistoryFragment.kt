package com.fortunatehtml.android.ui.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity
import com.fortunatehtml.android.model.TrafficEntry
import com.fortunatehtml.android.network.HarExporter
import com.fortunatehtml.android.network.HarImporter
import com.fortunatehtml.android.ui.TrafficAdapter
import com.fortunatehtml.android.ui.TrafficDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chronological list of all captured traffic.
 * Supports HAR import, HAR export, search by host/URL, and clear.
 */
class HistoryFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrafficAdapter
    private var allEntries: List<TrafficEntry> = emptyList()

    private val harImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importHar(uri) }
        }
    }

    private val harExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportHar(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etSearch         = view.findViewById(R.id.historySearch)
        recyclerView     = view.findViewById(R.id.historyRecyclerView)
        val btnImportHar = view.findViewById<Button>(R.id.btnImportHar)
        val btnExportHar = view.findViewById<Button>(R.id.btnExportHar)
        val btnClear     = view.findViewById<Button>(R.id.btnClearHistory)

        adapter = TrafficAdapter(
            onItemClick = { entry ->
                val intent = Intent(requireContext(), TrafficDetailActivity::class.java)
                intent.putExtra(TrafficDetailActivity.EXTRA_ENTRY_ID, entry.id)
                startActivity(intent)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        val app = requireActivity().application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(viewLifecycleOwner) { entries ->
            allEntries = entries
            applyFilter()
        }

        etSearch.doAfterTextChanged { applyFilter() }

        btnImportHar.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            harImportLauncher.launch(intent)
        }

        btnExportHar.setOnClickListener {
            harExportLauncher.launch("traffic_export.har.json")
        }

        btnClear.setOnClickListener {
            app.trafficRepository.clear()
            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFilter() {
        val query = etSearch.text.toString().trim().lowercase()
        val filtered = if (query.isEmpty()) allEntries
        else allEntries.filter { e ->
            // Pre-lowercase each field once per entry to avoid repeated conversions
            val urlLc    = e.url.lowercase()
            val hostLc   = e.host.lowercase()
            val methodLc = e.method.lowercase()
            val statusStr = e.statusCode?.toString() ?: ""
            urlLc.contains(query) || hostLc.contains(query) ||
            methodLc.contains(query) || statusStr.contains(query)
        }
        adapter.submitList(filtered)
    }

    private fun importHar(uri: Uri) {
        val app = requireActivity().application as FortunateHtmlApp
        CoroutineScope(Dispatchers.IO).launch {
            val stream = requireContext().contentResolver.openInputStream(uri)
            if (stream == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Could not open file", Toast.LENGTH_SHORT).show()
                }
                // Log the error
                app.database.logDao().insert(
                    com.fortunatehtml.android.data.db.entity.LogEntry(
                        category = "import",
                        message = "HAR import failed",
                        detail = "Could not open file stream"
                    )
                )
                return@launch
            }

            val result = HarImporter().importWithResult(stream)
            
            withContext(Dispatchers.Main) {
                if (!result.success && result.entries.isEmpty()) {
                    // Complete failure
                    val errorMsg = result.errors.firstOrNull() ?: "Unknown error"
                    Toast.makeText(requireContext(), "Import failed: $errorMsg", Toast.LENGTH_LONG).show()
                } else {
                    // Success or partial success
                    result.entries.forEach { e ->
                        app.trafficRepository.addEntry(
                            TrafficEntry(
                                method          = e.method,
                                url             = e.url,
                                host            = e.host,
                                path            = e.path,
                                scheme          = e.scheme,
                                requestHeaders  = e.requestHeaders,
                                requestBody     = e.requestBody,
                                statusCode      = e.statusCode,
                                responseHeaders = e.responseHeaders,
                                responseBody    = e.responseBody,
                                isHttps         = e.isHttps
                            )
                        )
                    }
                    
                    val message = if (result.errors.isEmpty()) {
                        "Imported ${result.entries.size} entries"
                    } else {
                        "Imported ${result.entries.size} entries (${result.errors.size} failed)"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
            
            // Log the import result
            val detailMessage = buildString {
                append("Imported ${result.entries.size} entries")
                if (result.errors.isNotEmpty()) {
                    append(", ${result.errors.size} errors")
                }
            }
            app.database.logDao().insert(
                com.fortunatehtml.android.data.db.entity.LogEntry(
                    category = "import",
                    message = if (result.success) "HAR import successful" else "HAR import with errors",
                    detail = detailMessage
                )
            )
        }
    }

    private fun exportHar(uri: Uri) {
        val entries = allEntries
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val entityList = entries.map { e ->
                    TrafficEntryEntity(
                        id              = e.id,
                        method          = e.method,
                        url             = e.url,
                        host            = e.host,
                        path            = e.path,
                        scheme          = e.scheme,
                        requestHeaders  = e.requestHeaders,
                        requestBody     = e.requestBody,
                        statusCode      = e.statusCode,
                        responseHeaders = e.responseHeaders,
                        responseBody    = e.responseBody,
                        timestamp       = e.timestamp,
                        duration        = e.duration,
                        responseSize    = e.responseSize,
                        isHttps         = e.isHttps,
                        state           = e.state.name,
                        contentType     = e.responseHeaders["Content-Type"]
                            ?: e.responseHeaders["content-type"]
                    )
                }
                val json = HarExporter().export(entityList)
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Exported ${entries.size} entries", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
