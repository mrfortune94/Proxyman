package com.fortunatehtml.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.ui.MainActivity
import com.fortunatehtml.android.ui.TrafficAdapter
import com.fortunatehtml.android.ui.viewmodel.InspectorViewModel

/**
 * Displays real-time traffic entries captured from WebView, OkHttp, or imported sources.
 * Tapping an entry selects it in the shared InspectorViewModel and switches to the Request tab.
 */
class LiveTrafficFragment : Fragment() {

    private val inspectorViewModel: InspectorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_live_traffic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.liveRecyclerView)
        val emptyView    = view.findViewById<TextView>(R.id.liveEmptyView)

        val adapter = TrafficAdapter { entry ->
            // Share the selected entry with Request / Response / Repeater tabs
            inspectorViewModel.select(entry)
            // Switch to the Request tab (index 1)
            (activity as? MainActivity)?.navigateToTab(1)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        val app = requireActivity().application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            emptyView.visibility    = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}
