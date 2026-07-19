package com.example.pluck.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pluck.data.repository.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for Pluck's first-run welcome experience. */
data class OnboardingUiState(
    val isLoading: Boolean = true,
    val isComplete: Boolean = false,
    val preferredName: String = ""
)

/** Coordinates the optional local profile name and first-run completion state. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingStore: OnboardingStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            onboardingStore.observeCompleted().collect { completed ->
                _uiState.update { it.copy(isLoading = false, isComplete = completed) }
            }
        }
    }

    /** Updates the unsaved, optional name while the user types. */
    fun updateName(name: String) {
        _uiState.update { it.copy(preferredName = name.take(MAX_NAME_LENGTH)) }
    }

    /** Saves the name (if supplied) and completes onboarding. */
    fun complete() {
        viewModelScope.launch { onboardingStore.complete(_uiState.value.preferredName) }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
