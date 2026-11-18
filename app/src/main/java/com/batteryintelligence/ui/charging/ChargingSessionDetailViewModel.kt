package com.batteryintelligence.ui.charging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.ui.navigation.ChargingSessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChargingSessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    chargingSessionRepository: ChargingSessionRepository
) : ViewModel() {

    private val route: ChargingSessionDetailRoute = savedStateHandle.toRoute()

    val session: StateFlow<ChargingSession?> = chargingSessionRepository
        .observeSessionById(route.sessionId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val samples: StateFlow<List<ChargingSessionSample>> = chargingSessionRepository
        .observeSessionSamples(route.sessionId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
