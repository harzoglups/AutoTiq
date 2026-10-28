package com.cussou.autotiq.ui.eventlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cussou.autotiq.domain.model.EventLogEntry
import com.cussou.autotiq.domain.repository.EventLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventLogViewModel @Inject constructor(
    private val eventLogRepository: EventLogRepository
) : ViewModel() {

    val events: StateFlow<List<EventLogEntry>> = eventLogRepository.getRecentEvents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clear() {
        viewModelScope.launch {
            eventLogRepository.clear()
        }
    }
}
