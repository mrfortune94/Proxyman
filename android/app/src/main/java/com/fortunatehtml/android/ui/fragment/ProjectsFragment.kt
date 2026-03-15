package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.PreferencesManager
import com.fortunatehtml.android.data.db.entity.LogEntry
import com.fortunatehtml.android.data.db.entity.Project
import com.fortunatehtml.android.util.BiometricHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Project management panel for organising traffic into named workspaces.
 * 
 * Projects can be protected with biometric authentication when enabled in settings.
 * This provides an additional layer of security for saved API inspection data.
 */
class ProjectsFragment : Fragment() {

    private lateinit var prefs: PreferencesManager
    private var isUnlocked = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_projects, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PreferencesManager(requireContext())
        
        val etName     = view.findViewById<EditText>(R.id.projectName)
        val etDesc     = view.findViewById<EditText>(R.id.projectDescription)
        val btnCreate  = view.findViewById<Button>(R.id.btnCreateProject)
        val tvProjects = view.findViewById<TextView>(R.id.projectsList)
        val tvLockStatus = view.findViewById<TextView>(R.id.projectsLockStatus)

        val app = requireActivity().application as FortunateHtmlApp
        val db  = app.database

        // Check if biometric lock is enabled and handle authentication
        if (prefs.biometricLock && BiometricHelper.isBiometricAvailable(requireContext())) {
            showLockedState(tvProjects, tvLockStatus, etName, etDesc, btnCreate)
            authenticateForAccess(tvProjects, tvLockStatus, etName, etDesc, btnCreate, app)
        } else {
            isUnlocked = true
            tvLockStatus?.visibility = View.GONE
            setupProjectsList(tvProjects, db)
        }

        btnCreate.setOnClickListener {
            if (!isUnlocked && prefs.biometricLock) {
                Toast.makeText(requireContext(), "Unlock projects first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val name = etName.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                db.projectDao().insert(Project(name = name, description = desc))
                // Log project creation
                db.logDao().insert(LogEntry(
                    category = "project",
                    message = "Project created",
                    detail = "Name: $name"
                ))
                withContext(Dispatchers.Main) {
                    etName.text.clear()
                    etDesc.text.clear()
                    Toast.makeText(requireContext(), R.string.toast_project_created, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLockedState(
        tvProjects: TextView,
        tvLockStatus: TextView?,
        etName: EditText,
        etDesc: EditText,
        btnCreate: Button
    ) {
        tvProjects.text = "🔒 Projects are locked\n\nBiometric authentication required to view saved projects."
        tvLockStatus?.visibility = View.VISIBLE
        tvLockStatus?.text = "🔐 Tap to unlock with biometrics"
        etName.isEnabled = false
        etDesc.isEnabled = false
        btnCreate.isEnabled = false
        
        tvLockStatus?.setOnClickListener {
            authenticateForAccess(tvProjects, tvLockStatus, etName, etDesc, btnCreate,
                requireActivity().application as FortunateHtmlApp)
        }
    }

    private fun authenticateForAccess(
        tvProjects: TextView,
        tvLockStatus: TextView?,
        etName: EditText,
        etDesc: EditText,
        btnCreate: Button,
        app: FortunateHtmlApp
    ) {
        BiometricHelper.authenticate(
            activity = requireActivity(),
            title = "Unlock Projects",
            subtitle = "Authenticate to access saved projects",
            onSuccess = {
                isUnlocked = true
                tvLockStatus?.visibility = View.GONE
                etName.isEnabled = true
                etDesc.isEnabled = true
                btnCreate.isEnabled = true
                setupProjectsList(tvProjects, app.database)
                
                // Log successful authentication
                CoroutineScope(Dispatchers.IO).launch {
                    app.database.logDao().insert(LogEntry(
                        category = "auth",
                        message = "Biometric unlock successful",
                        detail = "Projects access granted"
                    ))
                }
                
                Toast.makeText(requireContext(), "Projects unlocked", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                // Log failed authentication attempt
                CoroutineScope(Dispatchers.IO).launch {
                    app.database.logDao().insert(LogEntry(
                        category = "auth",
                        message = "Biometric unlock failed",
                        detail = error
                    ))
                }
                
                if (error != "Authentication cancelled") {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun setupProjectsList(tvProjects: TextView, db: com.fortunatehtml.android.data.db.AppDatabase) {
        db.projectDao().getAllLive().observe(viewLifecycleOwner) { projects ->
            tvProjects.text = if (projects.isEmpty()) getString(R.string.empty_projects)
            else projects.joinToString("\n\n") { p ->
                "\uD83D\uDCC1 ${p.name}\n${p.description.ifEmpty { "No description" }}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-lock when returning to fragment if biometric lock is enabled
        if (prefs.biometricLock && BiometricHelper.isBiometricAvailable(requireContext())) {
            isUnlocked = false
        }
    }
}
