package uk.co.tdsstudios.noterly.wear.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.tdsstudios.noterly.wear.data.ItemFields
import uk.co.tdsstudios.noterly.wear.data.WatchView
import uk.co.tdsstudios.noterly.wear.repository
import uk.co.tdsstudios.noterly.wearshared.DartTime
import uk.co.tdsstudios.noterly.wearshared.NoteItem
import uk.co.tdsstudios.noterly.wearshared.NoterlyColours
import uk.co.tdsstudios.noterly.wearshared.Recurrence
import uk.co.tdsstudios.noterly.wearshared.RepetitionData
import java.time.LocalDateTime

/** The reminder being created or edited, shared by the edit, time and repeat screens. */
data class Draft(
    /** Null when creating a new reminder. */
    val itemId: String?,
    val title: String,
    val body: String,
    val dateTime: LocalDateTime?,
    val repetition: RepetitionData?,
    val colour: Long,
) {
    /** The phone moves a weekday repeat onto a selected weekday, and a repeat always needs a time. */
    fun normalised(now: LocalDateTime = LocalDateTime.now()): Draft {
        val repetition = repetition ?: return this
        val start = dateTime ?: now
        return copy(dateTime = Recurrence.alignToWeekdays(repetition, start))
    }

    companion object {
        fun new() = Draft(null, "", "", null, null, NoterlyColours.random())

        fun of(item: NoteItem) = Draft(
            itemId = item.id,
            title = item.title,
            body = item.body,
            dateTime = DartTime.parseOrNull(item.dateTime),
            repetition = item.repetitionData,
            colour = item.colour,
        )
    }
}

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.repository

    val view: StateFlow<WatchView?> = repository.view.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val phoneConnected: StateFlow<Boolean?> = repository.phoneConnected

    var draft by mutableStateOf(Draft.new())
        private set

    fun refresh() = viewModelScope.launch {
        repository.loadStoredSnapshot()
        repository.refreshConnection(requestSnapshot = true)
        repository.refreshOutputs()
    }

    fun markDone(id: String) = viewModelScope.launch { repository.markDone(id) }
    fun snooze(id: String) = viewModelScope.launch { repository.snooze(id) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
    fun restore(id: String) = viewModelScope.launch { repository.restore(id) }

    fun startNew() {
        draft = Draft.new()
    }

    fun startEdit(item: NoteItem) {
        draft = Draft.of(item)
    }

    fun updateDraft(change: (Draft) -> Draft) {
        draft = change(draft)
    }

    fun saveDraft() {
        val draft = draft.normalised()
        viewModelScope.launch {
            if (draft.itemId == null) {
                repository.create(draft.title, draft.dateTime, draft.repetition, draft.colour)
            } else {
                repository.update(draft.itemId, ItemFields(draft.title, draft.body, draft.dateTime, draft.repetition, draft.colour))
            }
        }
    }
}
