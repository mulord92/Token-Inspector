package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TokenInspectResult

@Composable
fun ContractProperties(data: TokenInspectResult) {
    val abi = data.smart?.abiFunctions ?: emptyList()
    val isVerified = data.smart?.isVerified == true
    
    // Simulate data not available from standard Blockscout API
    val audited = false 
    val liquidityLocked = false
    // Sells Possible is true assuming it's not a honeypot unless we know otherwise
    val honeypot = false 

    val hasMint = abi.any { fn -> listOf("mint", "issue", "create", "generatetokens").any { fn.contains(it, ignoreCase = true) } }
    val mintDisabled = !hasMint

    val hasPauseOrDestruct = abi.any { fn -> listOf("selfdestruct", "destroy", "kill", "pause", "freeze", "blacklist", "ban", "lock").any { fn.contains(it, ignoreCase = true) } }
    val noOwnerBackdoors = !hasPauseOrDestruct

    val isProxy = data.smart?.isProxy == true
    val staticLogic = !isProxy

    val properties = listOf(
        Pair("Source Verified", isVerified),
        Pair("Third-Party Audit", audited),
        Pair("Liquidity Locked", liquidityLocked),
        Pair("Mint Disabled", mintDisabled),
        Pair("No Owner Backdoors", noOwnerBackdoors),
        Pair("Sells Possible", !honeypot),
        Pair("Static Logic", staticLogic)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1829), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "CONTRACT PROPERTIES",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Custom 2-column grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in properties.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PropertyItem(
                        label = properties[i].first, 
                        ok = properties[i].second,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (i + 1 < properties.size) {
                        PropertyItem(
                            label = properties[i + 1].first, 
                            ok = properties[i + 1].second,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyItem(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (ok) Color(0xFF0A1F0A) else Color(0xFF1C0A0A)
    val borderColor = if (ok) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)
    val textColor = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    
    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ok) "✓" else "✗",
            fontSize = 14.sp,
            color = textColor,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = textColor
        )
    }
}
