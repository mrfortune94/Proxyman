package com.fortunatehtml.android.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight wrapper around SharedPreferences for app-level settings
 * that do not need Room persistence (e.g., one-time agreement flag, theme choice).
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the user has accepted the Authorized-Use agreement. */
    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, value).apply()

    /** Unix timestamp (ms) when the agreement was last accepted. */
    var disclaimerAcceptedAt: Long
        get() = prefs.getLong(KEY_DISCLAIMER_ACCEPTED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_DISCLAIMER_ACCEPTED_AT, value).apply()

    /** Dark-mode override: true = dark, false = follow system. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    /** OkHttp / Repeater request timeout in seconds. */
    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT, 30)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    /** Maximum bytes shown in body-preview panels. */
    var bodyPreviewLimit: Int
        get() = prefs.getInt(KEY_BODY_LIMIT, 65536)
        set(value) = prefs.edit().putInt(KEY_BODY_LIMIT, value).apply()

    /** Optional User-Agent string for in-app browser and Repeater. */
    var userAgentOverride: String
        get() = prefs.getString(KEY_USER_AGENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_AGENT, value).apply()

    /** Whether to pretty-print JSON bodies. */
    var prettyPrintJson: Boolean
        get() = prefs.getBoolean(KEY_PRETTY_JSON, true)
        set(value) = prefs.edit().putBoolean(KEY_PRETTY_JSON, value).apply()

    /** Whether to disable screenshots (FLAG_SECURE). */
    var disableScreenshots: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_SCREENSHOTS, false)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_SCREENSHOTS, value).apply()

    /** Whether to require biometric authentication to open saved projects. */
    var biometricLock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, value).apply()

    companion object {
        private const val PREFS_NAME = "pocket_api_inspector_prefs"
        private const val KEY_DISCLAIMER_ACCEPTED    = "disclaimer_accepted"
        private const val KEY_DISCLAIMER_ACCEPTED_AT = "disclaimer_accepted_at"
        private const val KEY_DARK_MODE              = "dark_mode"
        private const val KEY_TIMEOUT                = "timeout_seconds"
        private const val KEY_BODY_LIMIT             = "body_preview_limit"
        private const val KEY_USER_AGENT             = "user_agent_override"
        private const val KEY_PRETTY_JSON            = "pretty_print_json"
        private const val KEY_DISABLE_SCREENSHOTS    = "disable_screenshots"
        private const val KEY_BIOMETRIC_LOCK         = "biometric_lock"
    }
}
