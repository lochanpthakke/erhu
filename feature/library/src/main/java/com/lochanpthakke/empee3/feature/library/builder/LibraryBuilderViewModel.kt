package com.lochanpthakke.empee3.feature.library.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.database.builder.LibraryBuilderAnalyzer
import com.lochanpthakke.empee3.core.database.builder.ProposedMove
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryBuilderViewModel @Inject constructor(
    private val analyzer: LibraryBuilderAnalyzer,
    private val safFileMover: SafFileMover,
    private val integrator: LibraryBuilderIntegrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryBuilderState>(LibraryBuilderState.Idle)
    val uiState: StateFlow<LibraryBuilderState> = _uiState.asStateFlow()

    fun startScanning(customUriString: String = "") {
        _uiState.value = LibraryBuilderState.Scanning
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val analysisResult = analyzer.analyze(customUriString)
                _uiState.update { LibraryBuilderState.Review(analysisResult.moves, analysisResult.rootUriString) }
            } catch (e: Exception) {
                _uiState.update { LibraryBuilderState.Idle }
            }
        }
    }

    fun skipMove(sourcePath: String) {
        _uiState.update { state ->
            if (state is LibraryBuilderState.Review) {
                val updatedMoves = state.moves.filterNot { it.sourcePath == sourcePath }
                state.copy(moves = updatedMoves)
            } else {
                state
            }
        }
    }

    fun resolveManually(sourcePath: String, newDestination: String) {
        _uiState.update { state ->
            if (state is LibraryBuilderState.Review) {
                val updatedMoves = state.moves.map { move ->
                    if (move.sourcePath == sourcePath) {
                        move.copy(
                            destinationPath = newDestination,
                            state = ProposedMove.State.VALID
                        )
                    } else {
                        move
                    }
                }
                state.copy(moves = updatedMoves)
            } else {
                state
            }
        }
    }

    fun executeMoves() {
        _uiState.update { state ->
            if (state is LibraryBuilderState.Review) {
                LibraryBuilderState.Executing(state.moves, state.rootUri)
            } else {
                state
            }
        }
        
        val currentState = _uiState.value
        if (currentState is LibraryBuilderState.Executing) {
            viewModelScope.launch(Dispatchers.IO) {
                val result = safFileMover.executeMoves(
                    moves = currentState.movesToExecute,
                    rootUriString = currentState.rootUri
                ) { currentProgress ->
                    _uiState.update { state ->
                        if (state is LibraryBuilderState.Executing) {
                            state.copy(progress = currentProgress)
                        } else {
                            state
                        }
                    }
                }
                
                // Phase 4: Integration - Cleanup empty folders and trigger media scan
                integrator.integrate(currentState.rootUri)
                
                _uiState.update { LibraryBuilderState.Complete(result) }
            }
        }
    }
}

sealed interface LibraryBuilderState {
    data object Idle : LibraryBuilderState
    data object Scanning : LibraryBuilderState
    data class Review(val moves: List<ProposedMove>, val rootUri: String) : LibraryBuilderState
    data class Executing(
        val movesToExecute: List<ProposedMove>,
        val rootUri: String,
        val progress: Int = 0,
        val total: Int = movesToExecute.size
    ) : LibraryBuilderState
    data class Complete(val result: ExecutionResult) : LibraryBuilderState
}
