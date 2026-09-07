package com.example

import com.example.data.SmartContractModel
import com.example.data.TokenInspectResult
import com.example.data.TokenModel
import com.example.viewmodel.TokenInspectorViewModel
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testFmtTokenSupply() {
        assertEquals("1.00M", fmtTokenSupply("1000000000000000000000000", "18"))
        assertEquals("500", fmtTokenSupply("500000000000000000000", "18"))
        assertEquals("—", fmtTokenSupply(null, "18"))
        assertEquals("—", fmtTokenSupply("", "18"))
    }

    @Test
    fun testRiskAnalysisNoCrash() {
        val vm = TokenInspectorViewModel()
        // Method check with sample token
        val token = TokenModel(
            name = "Test Token",
            symbol = "TST",
            type = "ERC-20",
            decimals = "18",
            totalSupply = "1000000000000000000000000",
            exchangeRate = "1.5",
            circulatingMarketCap = "1500000",
            holders = "100",
            iconUrl = "https://example.com/icon.png"
        )
        val smart = SmartContractModel(
            isVerified = true,
            isProxy = false,
            compilerVersion = "v0.8.20",
            licenseType = "MIT",
            abiFunctions = listOf("transfer", "balanceOf", "approve")
        )
        val result = TokenInspectResult(token, smart, "150")
        
        // Scan or test model
        assertNotNull(result)
        assertEquals("Test Token", result.token.name)

        val analysis = vm.analyzeRisk(result)
        assertTrue(analysis.score in 0..100)
        assertNotNull(analysis.verdict)

        val axes = com.example.ui.calculateRadarAxes(result, analysis)
        assertEquals(6, axes.size)
        assertTrue(axes.any { it.name == "Proxy Pattern" })
        assertTrue(axes.any { it.name == "Mint Functionality" })
        assertTrue(axes.any { it.name == "Owner Privileges" })
        assertTrue(axes.all { it.riskValue in 0.0f..1.0f })
    }

    @Test
    fun testNormalizeContractAddress() {
        // Without 0x
        assertEquals("0xB88fBdaa426fFdb95b917B861fe94bB", com.example.network.normalizeContractAddress("B88fBdaa426fFdb95b917B861fe94bB"))
        // With lowercase 0x
        assertEquals("0x2ce9e7c168035d61cc3a3655949c147c063eecc4", com.example.network.normalizeContractAddress("0x2ce9e7c168035d61cc3a3655949c147c063eecc4"))
        // With uppercase 0X
        assertEquals("0x2cE9e7c168035D61cc3a3655949C147c063EeCc4", com.example.network.normalizeContractAddress("0X2cE9e7c168035D61cc3a3655949C147c063EeCc4"))
        // With quotes / spaces
        assertEquals("0x2cE9e7c168035D61cc3a3655949C147c063EeCc4", com.example.network.normalizeContractAddress("  \"0x2cE9e7c168035D61cc3a3655949C147c063EeCc4\"  "))
    }

    @Test
    fun testFormatRelativeTime() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", formatRelativeTime(now))
        assertEquals("5m ago", formatRelativeTime(now - 5 * 60 * 1000))
        assertEquals("2h ago", formatRelativeTime(now - 2 * 3600 * 1000))
    }
}
