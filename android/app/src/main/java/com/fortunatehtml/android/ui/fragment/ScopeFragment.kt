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
import com.fortunatehtml.android.data.db.entity.ScopeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Scope domain control – define which hosts are in scope for capture and replay. */
class ScopeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_scope, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etHost  = view.findViewById<EditText>(R.id.scopeHost)
        val etEnv   = view.findViewById<EditText>(R.id.scopeEnvironment)
        val btnAdd  = view.findViewById<Button>(R.id.btnAddScope)
        val tvRules = view.findViewById<TextView>(R.id.scopeRulesList)

        val app = requireActivity().application as FortunateHtmlApp
        val db  = app.database

        db.scopeRuleDao().getAllLive().observe(viewLifecycleOwner) { rules ->
            tvRules.text = if (rules.isEmpty()) "No scope rules defined."
            else rules.joinToString("\n") { "[${it.environment}] ${it.host}" }
        }

        btnAdd.setOnClickListener {
            val host = etHost.text.toString().trim()
            val env  = etEnv.text.toString().trim().ifEmpty { "development" }
            if (host.isEmpty()) {
                Toast.makeText(requireContext(), "Host is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                db.scopeRuleDao().insert(ScopeRule(host = host, environment = env))
                withContext(Dispatchers.Main) {
                    etHost.text.clear()
                    Toast.makeText(requireContext(), "Scope rule added", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
