package com.fortunatehtml.android.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity
import com.fortunatehtml.android.model.TrafficEntry
import com.fortunatehtml.android.ui.MainActivity
import com.fortunatehtml.android.ui.TrafficAdapter
import com.fortunatehtml.android.ui.viewmodel.InspectorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays real-time traffic entries captured from WebView, OkHttp, or imported sources.
 * Tapping an entry selects it in the shared InspectorViewModel and switches to the Request tab.
 * 
 * Features:
 * - Highlight selected requests
 * - Copy request as cURL command
 * - Send to Repeater for replay/edit
 * - Save to database/project for later inspection
 */
class LiveTrafficFragment : Fragment() {

    private val inspectorViewModel: InspectorViewModel by activityViewModels()
    private lateinit var adapter: TrafficAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_live_traffic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.liveRecyclerView)
        val emptyView    = view.findViewById<TextView>(R.id.liveEmptyView)

        adapter = TrafficAdapter(
            onItemClick = { entry ->
                // Share the selected entry with Request / Response / Repeater tabs
                inspectorViewModel.select(entry)
                adapter.setSelectedEntry(entry.id)
                // Switch to the Request tab (index 1)
                (activity as? MainActivity)?.navigateToTab(1)
            },
            onCopyClick = { entry ->
                copyToCurl(entry)
            },
            onReplayClick = { entry ->
                // Select the entry and navigate to Repeater tab
                inspectorViewModel.select(entry)
                adapter.setSelectedEntry(entry.id)
                (activity as? MainActivity)?.navigateToTab(3) // Repeater tab
                Toast.makeText(requireContext(), "Loaded in Repeater - tap 'Load from Selected Entry'", Toast.LENGTH_SHORT).show()
            },
            onSaveClick = { entry ->
                showSaveDialog(entry)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@LiveTrafficFragment.adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        val app = requireActivity().application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            emptyView.visibility    = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }

        // Observe selected entry to update highlighting
        inspectorViewModel.selectedEntry.observe(viewLifecycleOwner) { entry ->
            adapter.setSelectedEntry(entry?.id)
        }
    }

    /**
     * Copy the request as a cURL command to clipboard.
     */
    private fun copyToCurl(entry: TrafficEntry) {
        val curlCommand = buildCurlCommand(entry)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cURL Command", curlCommand))
        Toast.makeText(requireContext(), "Copied as cURL command", Toast.LENGTH_SHORT).show()
    }

    /**
     * Build a cURL command string from a TrafficEntry.
     */
    private fun buildCurlCommand(entry: TrafficEntry): String {
        val sb = StringBuilder()
        sb.append("curl -X ${entry.method}")
        
        // Add headers
        entry.requestHeaders.forEach { (key, value) ->
            sb.append(" \\\n  -H '${key}: ${value}'")
        }
        
        // Add body if present
        if (!entry.requestBody.isNullOrEmpty()) {
            val escapedBody = entry.requestBody.replace("'", "'\\''")
            sb.append(" \\\n  -d '${escapedBody}'")
        }
        
        sb.append(" \\\n  '${entry.url}'")
        
        return sb.toString()
    }

    /**
     * Show dialog to save entry to database with optional project assignment.
     */
    private fun showSaveDialog(entry: TrafficEntry) {
        val app = requireActivity().application as FortunateHtmlApp
        val db = app.database

        CoroutineScope(Dispatchers.IO).launch {
            val projects = db.projectDao().getAll()
            
            withContext(Dispatchers.Main) {
                val options = mutableListOf("Save without project")
                options.addAll(projects.map { "📁 ${it.name}" })
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Save Request")
                    .setItems(options.toTypedArray()) { _, which ->
                        val projectId = if (which == 0) null else projects[which - 1].id
                        saveEntry(entry, projectId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    /**
     * Save the traffic entry to the database.
     */
    private fun saveEntry(entry: TrafficEntry, projectId: String?) {
        val app = requireActivity().application as FortunateHtmlApp
        val db = app.database

        CoroutineScope(Dispatchers.IO).launch {
            val entity = TrafficEntryEntity(
                id = entry.id,
                projectId = projectId,
                method = entry.method,
                url = entry.url,
                host = entry.host,
                path = entry.path,
                scheme = entry.scheme,
                requestHeaders = entry.requestHeaders,
                requestBody = entry.requestBody,
                statusCode = entry.statusCode,
                responseHeaders = entry.responseHeaders,
                responseBody = entry.responseBody,
                timestamp = entry.timestamp,
                duration = entry.duration,
                responseSize = entry.responseSize,
                isHttps = entry.isHttps,
                source = "webview",
                state = entry.state.name,
                contentType = entry.responseHeaders["Content-Type"] 
                    ?: entry.responseHeaders["content-type"]
            )
            
            db.trafficDao().insert(entity)
            
            withContext(Dispatchers.Main) {
                val projectInfo = if (projectId != null) " to project" else ""
                Toast.makeText(requireContext(), "Request saved$projectInfo", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
