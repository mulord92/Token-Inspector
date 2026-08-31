package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SmartContractResponse
import com.example.data.TokenResponse
import com.example.network.BlockscoutApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlin.math.pow

data class TokenData(
    val token: TokenResponse,
    val smart: SmartContractResponse?,
    val txCount: Long?
)

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
    data class Scanning(val step: String) : AppState()
    data class Error(val message: String) : AppState()
    data class Done(val data: TokenData, val analysis: RiskAnalysis) : AppState()
}

class TokenInspectorViewModel : ViewModel() {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://ledger.sidrachain.com/api/v2/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(BlockscoutApi::class.java)

    private val _uiState = MutableStateFlow<AppState>(AppState.Idle)
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = AppState.Idle
    }

    fun scan(address: String) {
        val trimmed = address.trim()
        if (trimmed.length < 10) return

        _uiState.value = AppState.Scanning("Fetching token data from ledger.sidrachain.com…")

        viewModelScope.launch {
            try {
                // Fetch token
                val tokenDef = async { api.getToken(trimmed) }
                // Fetch transfers for tx count (we can ignore errors if it fails)
                val transfersDef = async { 
                    try { api.getTransfers(trimmed) } catch (e: Exception) { null } 
                }
                // Fetch smart contract
                val smartDef = async {
                    try { api.getSmartContract(trimmed) } catch (e: Exception) { null }
                }
                
                val token = tokenDef.await()
                val transfers = transfersDef.await()
                
                _uiState.value = AppState.Scanning("Analyzing contract risk…")
                val smart = smartDef.await()

                val data = TokenData(
                    token = token,
                    smart = smart,
                    txCount = transfers?.nextPageParams?.index
                )

                val analysis = analyzeRisk(data)

                delay(500) // slight delay for animation effect
                _uiState.value = AppState.Done(data, analysis)

            } catch (e: Exception) {
                _uiState.value = AppState.Error(e.message ?: "Could not reach ledger.sidrachain.com — check the address.")
            }
        }
    }

    private fun analyzeRisk(data: TokenData): RiskAnalysis {
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
        val abiStr = smart?.abi?.toString()?.lowercase() ?: ""
        val hasSelfDestruct = listOf("selfdestruct", "destroy", "kill").any { abiStr.contains(it) }
        if (hasSelfDestruct) {
            flags.add(RiskFlag("critical", "Self-Destruct Function Detected", "Owner can permanently destroy the contract, wiping all token balances."))
            score += 40
        }

        // 4. Mint function
        val hasMint = listOf("mint", "issue", "create", "generatetokens").any { abiStr.contains(it) }
        if (hasMint) {
            flags.add(RiskFlag("critical", "Mint Function Present", "Owner can create new tokens at any time, diluting your holdings."))
            score += 30
        }

        // 5. Pause / freeze function
        val hasPause = listOf("pause", "freeze", "blacklist", "ban", "lock").any { abiStr.contains(it) }
        if (hasPause) {
            flags.add(RiskFlag("high", "Transfer Control Function", "Owner can pause transfers or blacklist wallets — your tokens could be frozen."))
            score += 15
        }

        // 6. Very low holder count = concentrated risk
        val holderCount = token.holders?.toIntOrNull() ?: 0
        if (holderCount in 1..49) {
            flags.add(RiskFlag("medium", "Low Holder Count ($holderCount)", "Very few wallets hold this token. High concentration increases manipulation risk."))
            score += 10
        }

        // 7. Total supply sanity
        val supply = token.totalSupply?.toDoubleOrNull() ?: 0.0
        val decimals = token.decimals?.toIntOrNull() ?: 18
        val realSupply = supply / 10.0.pow(decimals)
        if (realSupply > 1e15) {
            flags.add(RiskFlag("medium", "Astronomical Token Supply", "Token supply exceeds 1 quadrillion — often a sign of low-quality or spam token."))
            score += 8
        }

        // 8. No token icon / metadata
        if (token.iconUrl.isNullOrEmpty()) {
            flags.add(RiskFlag("low", "No Token Metadata", "No icon or verified metadata submitted. Legitimate projects typically register their token info."))
            score += 5
        }

        val cappedScore = score.coerceAtMost(100)
        
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
            verdictColor = Color(0xFF22C55E)
        }

        return RiskAnalysis(cappedScore, verdict, verdictColor, flags)
    }
}
