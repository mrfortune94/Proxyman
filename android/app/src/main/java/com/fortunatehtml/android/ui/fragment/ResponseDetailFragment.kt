package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fortunatehtml.android.R

/** Displays the response details of the currently selected traffic entry. */
class ResponseDetailFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_response_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.responsePlaceholder).text =
            "Select a traffic entry from the Live tab to view its response details."
    }
}
