package com.vaultt.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.database.entity.ExportHistoryEntity
import com.vaultt.domain.repository.ExportHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportHistoryViewModel @Inject constructor(
    private val exportHistoryRepository: ExportHistoryRepository
) : ViewModel() {

    val historyList: StateFlow<List<ExportHistoryEntity>> = exportHistoryRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            exportHistoryRepository.deleteRecord(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            exportHistoryRepository.clearAll()
        }
    }
}

