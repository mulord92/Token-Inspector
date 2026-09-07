package com.example.data.local

import androidx.compose.ui.graphics.toArgb
import com.example.data.TokenInspectResult
import com.example.viewmodel.RiskAnalysis
import kotlinx.coroutines.flow.Flow

class TokenHistoryRepository(private val dao: TokenInspectionDao) {

    val recentInspections: Flow<List<InspectedTokenEntity>> = dao.getRecentInspections()

    suspend fun saveInspection(
        address: String,
        data: TokenInspectResult,
        analysis: RiskAnalysis
    ) {
        val token = data.token
        val smart = data.smart

        val criticalCount = analysis.flags.count { it.level.equals("critical", ignoreCase = true) }
        val topSummary = analysis.flags.firstOrNull()?.label ?: if (analysis.score == 0) "No Risk Flags Detected" else null

        val colorLong = analysis.verdictColor.toArgb().toLong()

        val entity = InspectedTokenEntity(
            address = address.trim(),
            name = token.name,
            symbol = token.symbol,
            type = token.type,
            decimals = token.decimals,
            totalSupply = token.totalSupply,
            exchangeRate = token.exchangeRate,
            circulatingMarketCap = token.circulatingMarketCap,
            holders = token.holders,
            iconUrl = token.iconUrl,
            isVerified = smart?.isVerified == true,
            isProxy = smart?.isProxy == true,
            compilerVersion = smart?.compilerVersion,
            licenseType = smart?.licenseType,
            riskScore = analysis.score,
            riskVerdict = analysis.verdict,
            riskVerdictColorLong = colorLong,
            criticalFlagsCount = criticalCount,
            totalFlagsCount = analysis.flags.size,
            topRiskSummary = topSummary,
            timestamp = System.currentTimeMillis()
        )

        dao.saveAndPrune(entity)
    }

    suspend fun deleteInspection(address: String) {
        dao.deleteByAddress(address.trim())
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}
