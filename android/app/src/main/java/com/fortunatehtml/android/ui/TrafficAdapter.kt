package com.fortunatehtml.android.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fortunatehtml.android.R
import com.fortunatehtml.android.model.TrafficEntry

/**
 * Adapter for displaying traffic entries with expandable action buttons.
 * Supports highlighting selected items, copy, replay, and save actions.
 */
class TrafficAdapter(
    private val onItemClick: (TrafficEntry) -> Unit,
    private val onCopyClick: ((TrafficEntry) -> Unit)? = null,
    private val onReplayClick: ((TrafficEntry) -> Unit)? = null,
    private val onSaveClick: ((TrafficEntry) -> Unit)? = null
) : ListAdapter<TrafficEntry, TrafficAdapter.ViewHolder>(DiffCallback()) {

    // Track the currently expanded/selected item
    private var expandedPosition: Int = RecyclerView.NO_POSITION
    private var selectedEntryId: String? = null

    fun setSelectedEntry(entryId: String?) {
        val oldSelectedId = selectedEntryId
        selectedEntryId = entryId
        
        // Find and refresh the old and new selected items
        currentList.forEachIndexed { index, entry ->
            if (entry.id == oldSelectedId || entry.id == entryId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_traffic, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemContainer: LinearLayout = itemView.findViewById(R.id.itemContainer)
        private val methodText: TextView = itemView.findViewById(R.id.methodText)
        private val urlText: TextView = itemView.findViewById(R.id.urlText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)
        private val schemeText: TextView = itemView.findViewById(R.id.schemeText)
        private val actionButtonsRow: LinearLayout = itemView.findViewById(R.id.actionButtonsRow)
        private val btnCopy: TextView = itemView.findViewById(R.id.btnCopy)
        private val btnReplay: TextView = itemView.findViewById(R.id.btnReplay)
        private val btnSave: TextView = itemView.findViewById(R.id.btnSave)

        fun bind(entry: TrafficEntry, position: Int) {
            methodText.text = entry.method
            urlText.text = entry.host + entry.path
            statusText.text = entry.statusText
            schemeText.text = if (entry.isHttps) "HTTPS" else "HTTP"

            durationText.text = if (entry.duration != null) "${entry.duration}ms" else ""

            // Color-code status
            val statusColor = when {
                entry.state == TrafficEntry.State.FAILED -> Color.RED
                entry.statusCode != null && entry.statusCode in 200..299 -> Color.parseColor("#4CAF50")
                entry.statusCode != null && entry.statusCode in 300..399 -> Color.parseColor("#FF9800")
                entry.statusCode != null && entry.statusCode >= 400 -> Color.RED
                else -> Color.GRAY
            }
            statusText.setTextColor(statusColor)

            // Color-code method
            val methodColor = when (entry.method) {
                "GET" -> Color.parseColor("#2196F3")
                "POST" -> Color.parseColor("#4CAF50")
                "PUT" -> Color.parseColor("#FF9800")
                "DELETE" -> Color.RED
                "PATCH" -> Color.parseColor("#9C27B0")
                else -> Color.GRAY
            }
            methodText.setTextColor(methodColor)

            // Handle selection/highlighting
            val isSelected = entry.id == selectedEntryId
            val isExpanded = position == expandedPosition
            
            // Apply highlight background for selected items
            if (isSelected) {
                itemContainer.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(itemView.context, R.color.selected_item_background)
                )
            } else {
                // Apply the ripple effect background
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val typedArray = itemView.context.obtainStyledAttributes(attrs)
                val backgroundResource = typedArray.getResourceId(0, 0)
                typedArray.recycle()
                itemContainer.setBackgroundResource(backgroundResource)
            }

            // Show/hide action buttons
            actionButtonsRow.visibility = if (isExpanded || isSelected) View.VISIBLE else View.GONE

            // Main click - select and show details
            itemView.setOnClickListener {
                val previousExpanded = expandedPosition
                expandedPosition = if (expandedPosition == position) RecyclerView.NO_POSITION else position
                
                // Notify changes for animation
                notifyItemChanged(previousExpanded)
                notifyItemChanged(position)
                
                onItemClick(entry)
            }

            // Long press to toggle expansion without navigating
            itemView.setOnLongClickListener {
                val previousExpanded = expandedPosition
                expandedPosition = if (expandedPosition == position) RecyclerView.NO_POSITION else position
                
                notifyItemChanged(previousExpanded)
                notifyItemChanged(position)
                true
            }

            // Action button clicks
            btnCopy.setOnClickListener {
                onCopyClick?.invoke(entry)
            }

            btnReplay.setOnClickListener {
                onReplayClick?.invoke(entry)
            }

            btnSave.setOnClickListener {
                onSaveClick?.invoke(entry)
            }

            // Hide buttons that don't have handlers
            btnCopy.visibility = if (onCopyClick != null) View.VISIBLE else View.GONE
            btnReplay.visibility = if (onReplayClick != null) View.VISIBLE else View.GONE
            btnSave.visibility = if (onSaveClick != null) View.VISIBLE else View.GONE
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<TrafficEntry>() {
        override fun areItemsTheSame(oldItem: TrafficEntry, newItem: TrafficEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrafficEntry, newItem: TrafficEntry): Boolean {
            return oldItem == newItem
        }
    }
}
