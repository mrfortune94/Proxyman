package com.fortunatehtml.android.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager
import com.fortunatehtml.android.ui.DisclaimerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Application settings panel. */
class SettingsFragment : Fragment() {

    private lateinit var prefs: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PreferencesManager(requireContext())

        val etTimeout       = view.findViewById<EditText>(R.id.settingsTimeout)
        val etBodyLimit     = view.findViewById<EditText>(R.id.settingsBodyLimit)
        val etUserAgent     = view.findViewById<EditText>(R.id.settingsUserAgent)
        val cbPrettyJson    = view.findViewById<CheckBox>(R.id.settingsPrettyJson)
        val cbScreenshots   = view.findViewById<CheckBox>(R.id.settingsDisableScreenshots)
        val cbBiometric     = view.findViewById<CheckBox>(R.id.settingsBiometric)
        val cbSelfSigned    = view.findViewById<CheckBox>(R.id.settingsSelfSigned)
        val etExternalProxy = view.findViewById<EditText>(R.id.settingsExternalProxy)
        val btnSave         = view.findViewById<Button>(R.id.btnSaveSettings)
        val btnClearData    = view.findViewById<Button>(R.id.btnClearData)
        val btnReviewAgreement = view.findViewById<Button>(R.id.btnReviewAgreement)

        // Populate fields from saved prefs
        etTimeout.setText(prefs.timeoutSeconds.toString())
        etBodyLimit.setText(prefs.bodyPreviewLimit.toString())
        etUserAgent.setText(prefs.userAgentOverride)
        cbPrettyJson.isChecked   = prefs.prettyPrintJson
        cbScreenshots.isChecked  = prefs.disableScreenshots
        cbBiometric.isChecked    = prefs.biometricLock
        cbSelfSigned.isChecked   = prefs.acceptSelfSignedCerts
        etExternalProxy.setText(prefs.externalProxyUrl)

        btnSave.setOnClickListener {
            prefs.timeoutSeconds        = etTimeout.text.toString().toIntOrNull() ?: 30
            prefs.bodyPreviewLimit      = etBodyLimit.text.toString().toIntOrNull() ?: 65536
            prefs.userAgentOverride     = etUserAgent.text.toString()
            prefs.prettyPrintJson       = cbPrettyJson.isChecked
            prefs.disableScreenshots    = cbScreenshots.isChecked
            prefs.biometricLock         = cbBiometric.isChecked
            prefs.acceptSelfSignedCerts = cbSelfSigned.isChecked
            prefs.externalProxyUrl      = etExternalProxy.text.toString().trim()
            Toast.makeText(requireContext(), R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
        }

        btnClearData.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_data_title)
                .setMessage(R.string.dialog_clear_data_message)
                .setPositiveButton(R.string.dialog_clear_data_positive) { _, _ ->
                    val app = requireActivity().application as FortunateHtmlApp
                    app.trafficRepository.clear()
                    CoroutineScope(Dispatchers.IO).launch {
                        app.database.trafficDao().deleteAll()
                        app.database.logDao().deleteAll()
                    }
                    Toast.makeText(requireContext(),
                        R.string.toast_data_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        btnReviewAgreement.setOnClickListener {
            prefs.disclaimerAccepted = false
            startActivity(Intent(requireContext(), DisclaimerActivity::class.java))
        }
    }
}
