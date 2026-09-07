package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspected_tokens")
data class InspectedTokenEntity(
    @PrimaryKey
    val address: String,
    val name: String?,
    val symbol: String?,
    val type: String?,
    val decimals: String?,
    val totalSupply: String?,
    val exchangeRate: String?,
    val circulatingMarketCap: String?,
    val holders: String?,
    val iconUrl: String?,
    val isVerified: Boolean,
    val isProxy: Boolean,
    val compilerVersion: String?,
    val licenseType: String?,
    val riskScore: Int,
    val riskVerdict: String,
    val riskVerdictColorLong: Long,
    val criticalFlagsCount: Int,
    val totalFlagsCount: Int,
    val topRiskSummary: String?,
    val timestamp: Long = System.currentTimeMillis()
)
