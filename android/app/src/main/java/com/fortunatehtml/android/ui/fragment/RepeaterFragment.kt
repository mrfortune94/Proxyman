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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager
import com.fortunatehtml.android.network.OkHttpInspector
import com.fortunatehtml.android.ui.viewmodel.InspectorViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Manual request replay / crafting panel.
 *
 * Sends requests only to URLs the user explicitly enters. Captures responses through
 * the in-app OkHttp interceptor (same-process, first-party only).
 *
 * Supports optional self-signed cert acceptance and optional external forward proxy
 * (user-configured in Settings). These settings affect only THIS app's OkHttp client.
 */
class RepeaterFragment : Fragment() {

    private val inspectorViewModel: InspectorViewModel by activityViewModels()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    private lateinit var etUrl: EditText
    private lateinit var spinnerMethod: Spinner
    private lateinit var etHeaders: EditText
    private lateinit var etBody: EditText
    private lateinit var tvResponse: TextView
    private lateinit var tvTiming: TextView
    private lateinit var tvDiff: TextView
    private lateinit var btnSend: Button
    private lateinit var btnLoad: Button

    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Stores the first response for diff comparison
    private var originalResponse: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_repeater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etUrl         = view.findViewById(R.id.repeaterUrl)
        spinnerMethod = view.findViewById(R.id.repeaterMethod)
        etHeaders     = view.findViewById(R.id.repeaterHeaders)
        etBody        = view.findViewById(R.id.repeaterBody)
        tvResponse    = view.findViewById(R.id.repeaterResponse)
        tvTiming      = view.findViewById(R.id.repeaterTiming)
        tvDiff        = view.findViewById(R.id.repeaterDiff)
        btnSend       = view.findViewById(R.id.repeaterSend)
        btnLoad       = view.findViewById(R.id.btnLoadFromSelected)

        spinnerMethod.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Load selected entry from Live/History tab
        btnLoad.setOnClickListener {
            inspectorViewModel.selectedEntry.value?.let { entry ->
                etUrl.setText(entry.url)
                val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
                spinnerMethod.setSelection(methods.indexOf(entry.method).coerceAtLeast(0))
                etHeaders.setText(
                    entry.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                )
                etBody.setText(entry.requestBody ?: "")
                originalResponse = entry.responseBody
                Toast.makeText(requireContext(), "Loaded from selected entry", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(requireContext(), "No entry selected in Live tab", Toast.LENGTH_SHORT).show()
        }

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
        tvTiming.text     = ""
        tvDiff.text       = ""

        val app   = requireActivity().application as FortunateHtmlApp
        val prefs = PreferencesManager(requireContext())

        // Build OkHttp client with user-configured options
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(prefs.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(prefs.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .addInterceptor(OkHttpInspector(app.database.trafficDao(), ioScope))

        // Accept self-signed / private CA certs for the in-app OkHttp client only.
        // This only affects requests made by THIS app's Repeater feature – it does
        // NOT affect system-wide TLS or any other app's network connections.
        if (prefs.acceptSelfSignedCerts) {
            runCatching {
                val trustAll = arrayOf<javax.net.ssl.TrustManager>(
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, java.security.SecureRandom())
                clientBuilder
                    .sslSocketFactory(sslContext.socketFactory,
                        trustAll[0] as javax.net.ssl.X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            }
        }

        // Optional: route in-app requests through a user-configured forward proxy
        // (e.g. user's own Burp Suite / Charles Proxy instance on their laptop).
        // This sends traffic to a proxy the USER controls – not a MITM interception.
        val proxyUrl = prefs.externalProxyUrl.trim()
        if (proxyUrl.isNotEmpty()) {
            runCatching {
                val uri  = java.net.URI(proxyUrl)
                val host = uri.host ?: "localhost"
                val port = if (uri.port > 0) uri.port else 8080
                clientBuilder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
            }
        }

        // Apply user-agent override if set
        val ua = prefs.userAgentOverride
        if (ua.isNotEmpty()) {
            clientBuilder.addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build())
            }
        }

        val client = clientBuilder.build()

        ioScope.launch {
            val startTime = System.currentTimeMillis()
            val result = runCatching {
                val reqBuilder = Request.Builder().url(url)
                var detectedContentType: String? = null
                headers.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        reqBuilder.addHeader(key, value)
                        if (key.equals("Content-Type", ignoreCase = true)) {
                            detectedContentType = value
                        }
                    }
                }
                val mediaType = (detectedContentType ?: "application/json").toMediaTypeOrNull()
                val bodyObj = if (body.isNotEmpty()) body.toRequestBody(mediaType) else null
                reqBuilder.method(method, bodyObj)
                val response = client.newCall(reqBuilder.build()).execute()
                val respText = response.body?.string() ?: "(empty body)"
                val elapsed  = System.currentTimeMillis() - startTime
                Triple(response.code, respText, elapsed)
            }.getOrElse { e -> Triple(-1, "Error: ${e.message}", System.currentTimeMillis() - startTime) }

            val (statusCode, respText, elapsed) = result
            val formattedBody = if (statusCode >= 0) {
                runCatching {
                    val el = JsonParser.parseString(respText)
                    prettyGson.toJson(el)
                }.getOrElse { respText }
            } else {
                respText
            }

            val diffText = if (originalResponse != null && statusCode >= 0) {
                buildDiff(originalResponse!!, formattedBody)
            } else {
                ""
            }

            withContext(Dispatchers.Main) {
                tvResponse.text   = if (statusCode >= 0) "HTTP $statusCode\n\n$formattedBody" else formattedBody
                tvTiming.text     = if (elapsed > 0) "⏱ ${elapsed} ms" else ""
                tvDiff.text       = diffText
                btnSend.isEnabled = true

                if (originalResponse == null && statusCode >= 0) {
                    originalResponse = formattedBody
                }
            }
        }
    }

    /** Produce a simple line-level diff summary (added/removed lines). */
    private fun buildDiff(original: String, current: String): String {
        if (original == current) return "✓ Response identical to original"
        val origLines = original.lines()
        val currLines = current.lines()
        val origSet   = origLines.toHashSet()
        val currSet   = currLines.toHashSet()
        val removed   = origLines.count { it !in currSet }
        val added     = currLines.count { it !in origSet }
        return "Δ Diff vs original: +$added lines / -$removed lines"
    }
}
