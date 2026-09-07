package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TokenInspectResult
import com.example.data.local.AppDatabase
import com.example.data.local.InspectedTokenEntity
import com.example.data.local.TokenHistoryRepository
import com.example.network.RateLimitException
import com.example.network.ScanProgress
import com.example.network.SidraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow

data class RiskFlag(
    val level: String, // "critical", "high", "medium", "low"
    val label: String,
    val detail: String
)

data class RiskAnalysis(
    val score: Int,
    val verdict: String,
    val verdictColor: Color,
    val flags: List<RiskFlag>
)

sealed class AppState {
    object Idle : AppState()
    data class Scanning(val progress: ScanProgress) : AppState()
    data class Error(val message: String, val isRateLimit: Boolean = false) : AppState()
    data class Done(val data: TokenInspectResult, val analysis: RiskAnalysis) : AppState()
}

class TokenInspectorViewModel @JvmOverloads constructor(
    application: Application = Application(),
    historyRepository: TokenHistoryRepository? = null,
    private val repository: SidraRepository = SidraRepository()
) : AndroidViewModel(application) {

    private val historyRepo: TokenHistoryRepository? = historyRepository ?: try {
        TokenHistoryRepository(AppDatabase.getDatabase(application).tokenInspectionDao())
    } catch (e: Exception) {
        null
    }

    val recentInspections: StateFlow<List<InspectedTokenEntity>> = (historyRepo?.recentInspections ?: MutableStateFlow(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<AppState>(AppState.Idle)
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = AppState.Idle
    }

    fun scan(address: String) {
        val normalized = com.example.network.normalizeContractAddress(address)
        if (normalized.length < 10) return

        _uiState.value = AppState.Scanning(ScanProgress(step = "Connecting to ledger.sidrachain.com…"))

        viewModelScope.launch {
            try {
                val result = repository.fetchTokenData(normalized) { progress ->
                    _uiState.value = AppState.Scanning(progress)
                }
                result.onSuccess { data ->
                    _uiState.value = AppState.Scanning(ScanProgress(step = "Analyzing contract risk…"))
                    delay(250)
                    val analysis = analyzeRisk(data)
                    _uiState.value = AppState.Done(data, analysis)

                    // Persist to Room local storage
                    launch {
                        try {
                            historyRepo?.saveInspection(normalized, data, analysis)
                        } catch (e: Exception) {
                            // Non-blocking database failure safety
                        }
                    }
                }.onFailure { error ->
                    val isRateLimit = error is RateLimitException
                    _uiState.value = AppState.Error(
                        message = error.message ?: "Could not reach ledger.sidrachain.com — check the address.",
                        isRateLimit = isRateLimit
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AppState.Error(
                    message = e.message ?: "An unexpected error occurred.",
                    isRateLimit = e is RateLimitException
                )
            }
        }
    }

    fun loadSampleToken(sampleAddress: String? = null) {
        val normalized = sampleAddress?.let { com.example.network.normalizeContractAddress(it) } ?: "0x2cE9e7c168035D61cc3a3655949C147c063EeCc4"
        _uiState.value = AppState.Scanning(ScanProgress(step = "Loading contract analysis specimen…"))
        viewModelScope.launch {
            delay(300)
            val sampleData = repository.getSampleToken(normalized)
            val analysis = analyzeRisk(sampleData)
            _uiState.value = AppState.Done(sampleData, analysis)
        }
    }

    fun deleteFromHistory(address: String) {
        viewModelScope.launch {
            try {
                historyRepo?.deleteInspection(address)
            } catch (e: Exception) {
                // Non-blocking database failure safety
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyRepo?.clearHistory()
            } catch (e: Exception) {
                // Non-blocking database failure safety
            }
        }
    }

    fun analyzeRisk(data: TokenInspectResult): RiskAnalysis {
        val flags = mutableListOf<RiskFlag>()
        var score = 0

        val smart = data.smart
        val token = data.token

        // 1. Source code verification
        val isVerified = smart?.isVerified == true
        if (!isVerified) {
            flags.add(RiskFlag("high", "Source Code Unverified", "The contract bytecode hasn't been verified on the explorer. You cannot read what it actually does."))
            score += 20
        }

        // 2. Proxy / upgradeable
        val isProxy = smart?.isProxy == true
        if (isProxy) {
            flags.add(RiskFlag("medium", "Upgradeable Proxy Contract", "The contract logic can be silently swapped by the owner after deployment without your knowledge."))
            score += 10
        }

        // 3. Self-destruct capability
        val abiList = smart?.abiFunctions ?: emptyList()
        val hasSelfDestruct = abiList.any { fn ->
            listOf("selfdestruct", "destroy", "kill").any { k -> fn.contains(k, ignoreCase = true) }
        }
        if (hasSelfDestruct) {
            flags.add(RiskFlag("critical", "Self-Destruct Function Detected", "Owner can permanently destroy the contract, wiping all token balances."))
            score += 40
        }

        // 4. Mint function
        val hasMint = abiList.any { fn ->
            listOf("mint", "issue", "create", "generatetokens").any { k -> fn.contains(k, ignoreCase = true) }
        }
        if (hasMint) {
            flags.add(RiskFlag("critical", "Mint Function Present", "Owner can create new tokens at any time, diluting your holdings."))
            score += 30
        }

        // 5. Pause / freeze function
        val hasPause = abiList.any { fn ->
            listOf("pause", "freeze", "blacklist", "ban", "lock").any { k -> fn.contains(k, ignoreCase = true) }
        }
        if (hasPause) {
            flags.add(RiskFlag("high", "Transfer Control Function", "Owner can pause transfers or blacklist wallets — your tokens could be frozen."))
            score += 15
        }

        // 6. Very low holder count = concentrated risk
        val holderCount = token.holders?.toDoubleOrNull()?.toInt() ?: 0
        if (holderCount in 1..49) {
            flags.add(RiskFlag("medium", "Low Holder Count ($holderCount)", "Very few wallets hold this token. High concentration increases manipulation risk."))
            score += 10
        }

        // 7. Total supply sanity
        val supply = token.totalSupply?.toDoubleOrNull() ?: 0.0
        val decimals = token.decimals?.toIntOrNull() ?: 18
        val realSupply = try {
            if (decimals in 0..30) supply / 10.0.pow(decimals) else supply
        } catch (e: Exception) {
            supply
        }
        if (realSupply > 1e15) {
            flags.add(RiskFlag("medium", "Astronomical Token Supply", "Token supply exceeds 1 quadrillion — often a sign of low-quality or spam token."))
            score += 8
        }

        // 8. No token icon / metadata
        if (token.iconUrl.isNullOrEmpty()) {
            flags.add(RiskFlag("low", "No Token Metadata", "No icon or verified metadata submitted. Legitimate projects typically register their token info."))
            score += 5
        }

        val cappedScore = score.coerceIn(0, 100)

        val verdict: String
        val verdictColor: Color
        if (cappedScore >= 55) {
            verdict = "HIGH RISK"
            verdictColor = Color(0xFFEF4444)
        } else if (cappedScore >= 25) {
            verdict = "MEDIUM RISK"
            verdictColor = Color(0xFFF59E0B)
        } else {
            verdict = "LOW RISK"
            verdictColor = Color(0xFF34D399)
        }

        return RiskAnalysis(cappedScore, verdict, verdictColor, flags)
    }
}
