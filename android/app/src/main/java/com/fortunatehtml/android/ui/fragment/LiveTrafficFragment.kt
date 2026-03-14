package com.fortunatehtml.android.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.FortunateHtmlApp
import com.fortunatehtml.android.R
import com.fortunatehtml.android.ui.TrafficAdapter
import com.fortunatehtml.android.ui.TrafficDetailActivity

/** Displays real-time traffic entries captured from WebView, OkHttp, SDK, or imported sources. */
class LiveTrafficFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                               savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_live_traffic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.liveRecyclerView)
        val emptyView    = view.findViewById<TextView>(R.id.liveEmptyView)

        val adapter = TrafficAdapter { entry ->
            val intent = Intent(requireContext(), TrafficDetailActivity::class.java)
            intent.putExtra(TrafficDetailActivity.EXTRA_ENTRY_ID, entry.id)
            startActivity(intent)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        val app = requireActivity().application as FortunateHtmlApp
        app.trafficRepository.trafficEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            emptyView.visibility    = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}
