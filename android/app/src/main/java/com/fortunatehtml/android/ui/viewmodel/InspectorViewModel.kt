package com.fortunatehtml.android.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fortunatehtml.android.model.TrafficEntry

/**
 * Shared ViewModel that holds the currently-selected traffic entry.
 * Scoped to the MainActivity so all inspector tabs can read it.
 */
class InspectorViewModel : ViewModel() {
    private val _selectedEntry = MutableLiveData<TrafficEntry?>()
    val selectedEntry: LiveData<TrafficEntry?> = _selectedEntry

    fun select(entry: TrafficEntry) {
        _selectedEntry.value = entry
    }

    fun clear() {
        _selectedEntry.value = null
    }
}
