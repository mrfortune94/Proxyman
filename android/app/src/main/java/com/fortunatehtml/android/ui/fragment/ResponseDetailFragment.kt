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
 * Shows the full response details (status, timing, headers, body)
 * for the entry selected in the Live tab.
 */
class ResponseDetailFragment : Fragment() {

    private val inspectorViewModel: InspectorViewModel by activityViewModels()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_response_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvPlaceholder  = view.findViewById<TextView>(R.id.responsePlaceholder)
        val tvStatus       = view.findViewById<TextView>(R.id.respStatus)
        val tvTiming       = view.findViewById<TextView>(R.id.respTiming)
        val tvSize         = view.findViewById<TextView>(R.id.respSize)
        val tvContentType  = view.findViewById<TextView>(R.id.respContentType)
        val tvHeaders      = view.findViewById<TextView>(R.id.respHeaders)
        val tvBody         = view.findViewById<TextView>(R.id.respBody)
        val btnCopyResp    = view.findViewById<Button>(R.id.btnCopyResponse)
        val detailGroup    = view.findViewById<View>(R.id.responseDetailGroup)

        inspectorViewModel.selectedEntry.observe(viewLifecycleOwner) { entry ->
            if (entry == null) {
                tvPlaceholder.visibility = View.VISIBLE
                detailGroup.visibility   = View.GONE
                return@observe
            }
            tvPlaceholder.visibility = View.GONE
            detailGroup.visibility   = View.VISIBLE

            tvStatus.text      = entry.statusText
            tvTiming.text      = if (entry.duration != null) "${entry.duration} ms" else "—"
            tvSize.text        = if (entry.responseSize != null) "${entry.responseSize} B" else "—"
            tvContentType.text = entry.responseHeaders["Content-Type"]
                ?: entry.responseHeaders["content-type"] ?: "—"

            tvHeaders.text = entry.responseHeaders.entries
                .joinToString("\n") { "${it.key}: ${it.value}" }
                .ifEmpty { "(none)" }

            tvBody.text = formatBody(
                entry.responseBody,
                entry.responseHeaders["Content-Type"] ?: ""
            )

            btnCopyResp.setOnClickListener {
                copyToClipboard(buildRaw(entry), "Response")
            }
        }
    }

    private fun formatBody(body: String?, contentType: String): String {
        if (body.isNullOrEmpty()) return "(empty)"
        if (contentType.contains("json", ignoreCase = true)) {
            return runCatching {
                val el = JsonParser.parseString(body)
                prettyGson.toJson(el)
            }.getOrElse { body }
        }
        return body
    }

    private fun buildRaw(entry: TrafficEntry): String {
        val sb = StringBuilder()
        sb.appendLine("HTTP ${entry.statusText}")
        entry.responseHeaders.forEach { (k, v) -> sb.appendLine("$k: $v") }
        if (!entry.responseBody.isNullOrEmpty()) {
            sb.appendLine()
            sb.append(entry.responseBody)
        }
        return sb.toString()
    }

    private fun copyToClipboard(text: String, label: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
