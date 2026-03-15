package com.fortunatehtml.android.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fortunatehtml.android.R
import com.fortunatehtml.android.model.TrafficEntry
import com.fortunatehtml.android.ui.viewmodel.InspectorViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * Shows the full request details (method, URL, query params, headers, body)
 * for the entry selected in the Live tab.
 *
 * Uses the shared InspectorViewModel – no MITM or interception involved.
 */
class RequestDetailFragment : Fragment() {

    private val inspectorViewModel: InspectorViewModel by activityViewModels()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_request_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvPlaceholder = view.findViewById<TextView>(R.id.requestPlaceholder)
        val tvMethod      = view.findViewById<TextView>(R.id.reqMethod)
        val tvUrl         = view.findViewById<TextView>(R.id.reqUrl)
        val tvQuery       = view.findViewById<TextView>(R.id.reqQuery)
        val tvHeaders     = view.findViewById<TextView>(R.id.reqHeaders)
        val tvBody        = view.findViewById<TextView>(R.id.reqBody)
        val btnCopyReq    = view.findViewById<Button>(R.id.btnCopyRequest)
        val detailGroup   = view.findViewById<View>(R.id.requestDetailGroup)

        inspectorViewModel.selectedEntry.observe(viewLifecycleOwner) { entry ->
            if (entry == null) {
                tvPlaceholder.visibility = View.VISIBLE
                detailGroup.visibility   = View.GONE
                return@observe
            }
            tvPlaceholder.visibility = View.GONE
            detailGroup.visibility   = View.VISIBLE

            tvMethod.text  = entry.method
            tvUrl.text     = entry.url

            // Extract query string from URL
            val query = runCatching {
                java.net.URI(entry.url).query ?: ""
            }.getOrElse { "" }
            tvQuery.text = query.ifEmpty { "(none)" }

            // Request headers
            tvHeaders.text = entry.requestHeaders.entries
                .joinToString("\n") { "${it.key}: ${it.value}" }
                .ifEmpty { "(none)" }

            // Request body – pretty-print JSON if possible
            tvBody.text = formatBody(entry.requestBody)

            btnCopyReq.setOnClickListener {
                copyToClipboard(buildRaw(entry), "Request")
            }
        }
    }

    private fun formatBody(body: String?): String {
        if (body.isNullOrEmpty()) return "(empty)"
        return runCatching {
            val el = JsonParser.parseString(body)
            prettyGson.toJson(el)
        }.getOrElse { body }
    }

    private fun buildRaw(entry: TrafficEntry): String {
        val sb = StringBuilder()
        sb.appendLine("${entry.method} ${entry.url}")
        entry.requestHeaders.forEach { (k, v) -> sb.appendLine("$k: $v") }
        if (!entry.requestBody.isNullOrEmpty()) {
            sb.appendLine()
            sb.append(entry.requestBody)
        }
        return sb.toString()
    }

    private fun copyToClipboard(text: String, label: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
