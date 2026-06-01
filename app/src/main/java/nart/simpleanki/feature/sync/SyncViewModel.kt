package nart.simpleanki.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.sync.SyncManager

/** Triggers a two-way sync for the signed-in user. Safe to call repeatedly. */
class SyncViewModel(
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun sync(uid: String) {
        if (uid.isBlank() || _syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching { syncManager.sync(uid) }
            _syncing.value = false
        }
    }
}
