package com.example.agenticrag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agenticrag.data.ApiClient
import com.example.agenticrag.data.CorpusResponse
import com.example.agenticrag.data.MemoryResponse
import com.example.agenticrag.data.QueryRequest
import com.example.agenticrag.data.QueryResponse
import com.example.agenticrag.data.ResetRequest
import com.example.agenticrag.data.Scenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentUiState(
    val question: String = "",
    val k: Int = 3,
    val maxHops: Int = 3,
    val poisonHopOne: Boolean = false,
    val isLoading: Boolean = false,
    val response: QueryResponse? = null,
    val scenarios: List<Scenario> = emptyList(),
    val selectedScenario: Scenario? = null,
    val memory: MemoryResponse = MemoryResponse(),
    val corpus: CorpusResponse = CorpusResponse(),
    val traces: List<QueryResponse> = emptyList(),
    val error: String? = null,
)

class AgentViewModel : ViewModel() {
    private val api = ApiClient.service
    private val sessionId = "android-default"

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        refreshSupportingData()
    }

    fun refreshSupportingData() {
        viewModelScope.launch {
            runCatching { api.scenarios() }.onSuccess { scenarios ->
                _uiState.update { it.copy(scenarios = scenarios) }
            }
            runCatching { api.corpus() }.onSuccess { corpus ->
                _uiState.update { it.copy(corpus = corpus) }
            }
            runCatching { api.memory(sessionId) }.onSuccess { memory ->
                _uiState.update { it.copy(memory = memory) }
            }
            runCatching { api.traces(sessionId) }.onSuccess { traces ->
                _uiState.update { it.copy(traces = traces) }
            }
        }
    }

    fun onQuestionChange(value: String) = _uiState.update { it.copy(question = value) }

    fun onKChange(value: Int) = _uiState.update { it.copy(k = value) }

    fun onMaxHopsChange(value: Int) = _uiState.update { it.copy(maxHops = value) }

    fun onPoisonChange(value: Boolean) = _uiState.update { it.copy(poisonHopOne = value) }

    fun selectScenario(scenario: Scenario) {
        _uiState.update {
            it.copy(
                selectedScenario = scenario,
                question = scenario.question,
                k = scenario.k,
                maxHops = scenario.maxHops,
                poisonHopOne = scenario.simulateWrongFirstHop,
            )
        }
    }

    fun clearSelectedScenario() = _uiState.update { it.copy(selectedScenario = null) }

    fun ask() {
        val state = _uiState.value
        val question = state.question.trim()
        if (question.isEmpty() || state.isLoading) return
        val request = QueryRequest(
            sessionId = sessionId,
            question = question,
            k = state.k,
            maxHops = state.maxHops,
            simulateWrongFirstHop = state.poisonHopOne,
            scenario = if (state.poisonHopOne) "wrong_first_hop" else "auto",
        )
        execute(request)
    }

    fun runScenario(scenario: Scenario) {
        if (_uiState.value.isLoading) return
        selectScenario(scenario)
        val request = QueryRequest(
            sessionId = sessionId,
            question = scenario.question,
            k = scenario.k,
            maxHops = scenario.maxHops,
            simulateWrongFirstHop = scenario.simulateWrongFirstHop,
            scenario = scenario.id,
        )
        execute(request)
    }

    private fun execute(request: QueryRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.query(request) }
                .onSuccess { response ->
                    _uiState.update { it.copy(isLoading = false, response = response) }
                    refreshSupportingData()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message?.takeIf { message -> message.isNotBlank() }
                                ?: "Backend unreachable at 10.0.2.2:8000",
                        )
                    }
                }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { api.resetMemory(ResetRequest(sessionId)) }
                .onSuccess { refreshSupportingData() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.message ?: "Unable to clear memory") }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun dismissResponse() = _uiState.update { it.copy(response = null, error = null) }
}
