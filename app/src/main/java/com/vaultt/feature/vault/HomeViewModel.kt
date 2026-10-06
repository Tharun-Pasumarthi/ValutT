package com.vaultt.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    val photoCount: StateFlow<Int> = repository.getCountByType(VaultObjectType.PHOTO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val videoCount: StateFlow<Int> = repository.getCountByType(VaultObjectType.VIDEO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val audioCount: StateFlow<Int> = repository.getCountByType(VaultObjectType.AUDIO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val docCount: StateFlow<Int> = repository.getCountByType(VaultObjectType.DOCUMENT.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val trashCount: StateFlow<Int> = repository.getTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
