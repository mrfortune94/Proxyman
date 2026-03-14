package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.network.OkHttpInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Manual request replay / crafting panel.
 * Only sends requests to URLs the user explicitly enters.
 */
class RepeaterFragment : Fragment() {

    private lateinit var etUrl: EditText
    private lateinit var spinnerMethod: Spinner
    private lateinit var etHeaders: EditText
    private lateinit var etBody: EditText
    private lateinit var tvResponse: TextView
    private lateinit var btnSend: Button

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_repeater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etUrl         = view.findViewById(R.id.repeaterUrl)
        spinnerMethod = view.findViewById(R.id.repeaterMethod)
        etHeaders     = view.findViewById(R.id.repeaterHeaders)
        etBody        = view.findViewById(R.id.repeaterBody)
        tvResponse    = view.findViewById(R.id.repeaterResponse)
        btnSend       = view.findViewById(R.id.repeaterSend)

        spinnerMethod.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnSend.setOnClickListener { sendRequest() }
    }

    private fun sendRequest() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            tvResponse.text = getString(R.string.repeater_url_required)
            return
        }

        val method  = spinnerMethod.selectedItem.toString()
        val headers = etHeaders.text.toString()
        val body    = etBody.text.toString()

        btnSend.isEnabled = false
        tvResponse.text   = getString(R.string.repeater_sending)

        val app = requireActivity().application as FortunateHtmlApp
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(OkHttpInspector(app.database.trafficDao(), ioScope))
            .build()

        ioScope.launch {
            val result = runCatching {
                val reqBuilder = Request.Builder().url(url)
                headers.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) reqBuilder.addHeader(parts[0].trim(), parts[1].trim())
                }
                val bodyObj = if (body.isNotEmpty())
                    body.toRequestBody("application/json".toMediaTypeOrNull())
                else
                    null
                reqBuilder.method(method, bodyObj)
                val response  = client.newCall(reqBuilder.build()).execute()
                val respText  = response.body?.string() ?: "(empty body)"
                "HTTP ${response.code}\n\n$respText"
            }.getOrElse { e -> "Error: ${e.message}" }

            withContext(Dispatchers.Main) {
                tvResponse.text   = result
                btnSend.isEnabled = true
            }
        }
    }
}
