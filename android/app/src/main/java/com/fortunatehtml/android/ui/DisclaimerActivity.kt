package com.fortunatehtml.android.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager

/**
 * Full-screen agreement gate shown on first launch.
 * The user must actively check BOTH checkboxes before the "Agree and Continue" button
 * is enabled. Acceptance timestamp is stored in SharedPreferences.
 */
class DisclaimerActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        // Skip gate if already accepted
        if (preferencesManager.disclaimerAccepted) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_disclaimer)

        val disclaimerText         = findViewById<TextView>(R.id.disclaimerText)
        val checkboxAuthorized     = findViewById<CheckBox>(R.id.checkboxAuthorized)
        val checkboxResponsibility = findViewById<CheckBox>(R.id.checkboxResponsibility)
        val btnAgree               = findViewById<Button>(R.id.btnAgree)
        val btnExit                = findViewById<Button>(R.id.btnExit)

        disclaimerText.text = getString(R.string.disclaimer_body)

        val updateAgreeButton = {
            btnAgree.isEnabled = checkboxAuthorized.isChecked && checkboxResponsibility.isChecked
        }

        checkboxAuthorized.setOnCheckedChangeListener { _, _ -> updateAgreeButton() }
        checkboxResponsibility.setOnCheckedChangeListener { _, _ -> updateAgreeButton() }

        btnAgree.setOnClickListener {
            preferencesManager.disclaimerAccepted = true
            preferencesManager.disclaimerAcceptedAt = System.currentTimeMillis()
            navigateToMain()
        }

        btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
