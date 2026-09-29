package de.sanniki.wakesleuth.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One [STATE] the screen draws, [INTENT]s it sends in, and one-shot [EVENT]s
 * (toasts, launchers, navigation) that must not be replayed on rotation.
 */
abstract class MviViewModel<STATE, INTENT, EVENT>(
    initialState: STATE,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<STATE> = _state.asStateFlow()

    private val _events = Channel<EVENT>(Channel.BUFFERED)
    val events: Flow<EVENT> = _events.receiveAsFlow()

    abstract fun onIntent(intent: INTENT)

    protected fun updateState(reduce: (STATE) -> STATE) {
        _state.update(reduce)
    }

    protected fun sendEvent(event: EVENT) {
        viewModelScope.launch { _events.send(event) }
    }
}
