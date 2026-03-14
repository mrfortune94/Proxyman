package com.fortunatehtml.android.ui.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.model.TrafficEntry
import com.fortunatehtml.android.network.HarImporter
import com.fortunatehtml.android.ui.TrafficAdapter
import com.fortunatehtml.android.ui.TrafficDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chronological list of all captured traffic with HAR import support. */
class HistoryFragment : Fragment() {

    private val harImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importHar(uri) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
        val btnImportHar = view.findViewById<Button>(R.id.btnImportHar)
        val btnClear     = view.findViewById<Button>(R.id.btnClearHistory)

        val adapter = TrafficAdapter { entry ->
            val intent = Intent(requireContext(), TrafficDetailActivity::class.java)
            intent.putExtra(TrafficDetailActivity.EXTRA_ENTRY_ID, entry.id)
            startActivity(intent)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        val app = requireActivity().application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
        }

        btnImportHar.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            harImportLauncher.launch(intent)
        }

        btnClear.setOnClickListener {
            app.trafficRepository.clear()
            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importHar(uri: Uri) {
        val app = requireActivity().application as FortunateHtmlApp
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val stream  = requireContext().contentResolver.openInputStream(uri) ?: return@runCatching
                val entries = HarImporter().import(stream)
                withContext(Dispatchers.Main) {
                    entries.forEach { e ->
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
                    Toast.makeText(requireContext(),
                        "Imported ${entries.size} entries", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
