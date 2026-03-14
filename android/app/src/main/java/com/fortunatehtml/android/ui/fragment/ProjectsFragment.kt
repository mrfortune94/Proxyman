package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.data.db.entity.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Project management panel for organising traffic into named workspaces. */
class ProjectsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_projects, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etName     = view.findViewById<EditText>(R.id.projectName)
        val etDesc     = view.findViewById<EditText>(R.id.projectDescription)
        val btnCreate  = view.findViewById<Button>(R.id.btnCreateProject)
        val tvProjects = view.findViewById<TextView>(R.id.projectsList)

        val app = requireActivity().application as FortunateHtmlApp
        val db  = app.database

        db.projectDao().getAllLive().observe(viewLifecycleOwner) { projects ->
            tvProjects.text = if (projects.isEmpty()) "No projects yet."
            else projects.joinToString("\n\n") { p ->
                "\uD83D\uDCC1 ${p.name}\n${p.description.ifEmpty { "No description" }}"
            }
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                db.projectDao().insert(Project(name = name, description = desc))
                withContext(Dispatchers.Main) {
                    etName.text.clear()
                    etDesc.text.clear()
                    Toast.makeText(requireContext(), "Project created", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
