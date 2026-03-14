package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Event log showing app lifecycle, agreement acceptance, capture events, and errors. */
class LogsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_logs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvLogs = view.findViewById<TextView>(R.id.logsText)
        val fmt    = SimpleDateFormat("HH:mm:ss", Locale.US)

        val app = requireActivity().application as FortunateHtmlApp
        app.database.logDao().getAllLive().observe(viewLifecycleOwner) { logs ->
            tvLogs.text = if (logs.isEmpty()) "No log entries."
            else logs.joinToString("\n") { e ->
                "[${fmt.format(Date(e.timestamp))}] [${e.category}] ${e.message}"
            }
        }
    }
}
