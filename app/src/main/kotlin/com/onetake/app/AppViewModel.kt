package com.onetake.app

import androidx.lifecycle.ViewModel
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.SampleData
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val auditions: List<Audition> = SampleData.auditions,
    val takes: List<Take> = SampleData.takes,
    val practice: List<PracticePiece> = SampleData.practice,
    val slate: SlateInfo = SlateInfo(),
    val slateAtStart: Boolean = true,
    val cleanNoise: Boolean = true,
    val readerVolume: String = "Quieter",
    val exportSpec: String = "1080p, landscape"
)

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun getAudition(id: String): Audition? {
        return _uiState.value.auditions.firstOrNull { it.id == id }
    }

    val nextPriority: Audition?
        get() = _uiState.value.auditions
            .filter { it.status != AuditionStatus.SENT }
            .minByOrNull { it.dueEpochMs }

    val activeAuditions: List<Audition>
        get() = _uiState.value.auditions
            .filter { it.status != AuditionStatus.SENT }
            .sortedBy { it.dueEpochMs }

    val sentAuditions: List<Audition>
        get() = _uiState.value.auditions
            .filter { it.status == AuditionStatus.SENT }

    fun addAudition(audition: Audition) {
        _uiState.update { current ->
            current.copy(auditions = listOf(audition) + current.auditions)
        }
    }

    fun deleteAudition(id: String) {
        _uiState.update { current ->
            current.copy(auditions = current.auditions.filterNot { it.id == id })
        }
    }

    fun markDone(auditionId: String, kind: PieceKind, title: String? = null) {
        _uiState.update { current ->
            val updated = current.auditions.map { a ->
                if (a.id == auditionId) {
                    val updatedPieces = a.pieces.map { piece ->
                        if (piece.kind == kind && !piece.isDone && (title == null || piece.title == title)) {
                            piece.copy(isDone = true)
                        } else {
                            piece
                        }
                    }
                    val allDone = updatedPieces.all { it.isDone }
                    a.copy(
                        pieces = updatedPieces,
                        status = if (allDone) AuditionStatus.READY else a.status
                    )
                } else a
            }
            current.copy(auditions = updated)
        }
    }

    fun markSent(auditionId: String) {
        _uiState.update { current ->
            val updated = current.auditions.map { a ->
                if (a.id == auditionId) {
                    a.copy(
                        status = AuditionStatus.SENT,
                        sentDateEpochMs = System.currentTimeMillis()
                    )
                } else a
            }
            current.copy(auditions = updated)
        }
    }

    fun circleTake(takeId: String) {
        _uiState.update { current ->
            val updated = current.takes.map { t ->
                t.copy(isCircled = t.id == takeId)
            }
            current.copy(takes = updated)
        }
    }

    fun addTake(take: Take) {
        _uiState.update { current ->
            current.copy(takes = current.takes + take)
        }
    }

    fun updateSlate(slate: SlateInfo) {
        _uiState.update { it.copy(slate = slate) }
    }

    fun updateSettings(
        slateAtStart: Boolean = _uiState.value.slateAtStart,
        cleanNoise: Boolean = _uiState.value.cleanNoise,
        readerVolume: String = _uiState.value.readerVolume,
        exportSpec: String = _uiState.value.exportSpec
    ) {
        _uiState.update {
            it.copy(
                slateAtStart = slateAtStart,
                cleanNoise = cleanNoise,
                readerVolume = readerVolume,
                exportSpec = exportSpec
            )
        }
    }
}
