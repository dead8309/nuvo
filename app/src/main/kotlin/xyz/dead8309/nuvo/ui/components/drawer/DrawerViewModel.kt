package xyz.dead8309.nuvo.ui.components.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.core.model.ChatSession
import xyz.dead8309.nuvo.data.repository.ChatRepository
import javax.inject.Inject

sealed interface DrawerUiState {
    data object Loading : DrawerUiState
    data class Success(val sessions: List<ChatSession>) : DrawerUiState
    data class Error(val message: String) : DrawerUiState
}


@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    val uiState: StateFlow<DrawerUiState> = chatRepository.getRecentChatSessions()
        .map<List<ChatSession>, DrawerUiState> { sessions ->
            DrawerUiState.Success(sessions.sortedByDescending { it.lastModifiedTime })
        }
        .catch { ex ->
            emit(DrawerUiState.Error(ex.message ?: "Unknown error"))
        }
        .onStart { emit(DrawerUiState.Loading) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DrawerUiState.Loading
        )

    fun deleteChat(chatSessionId: String) {
        TODO("Not yet implemented")
    }
}