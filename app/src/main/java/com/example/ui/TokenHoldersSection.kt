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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TokenInspectResult
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

@Composable
fun TokenHoldersSection(data: TokenInspectResult) {
    val holders = data.topHolders
    if (holders.isNullOrEmpty()) return

    val decimals = data.token.decimals?.toIntOrNull() ?: 18
    val totalSupplyRaw = data.token.totalSupply?.toDoubleOrNull() ?: 0.0
    val format = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1829), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOP TOKEN HOLDERS",
                fontSize = 11.sp,
                color = Color(0xFF475569),
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Top ${holders.size}",
                fontSize = 10.sp,
                color = Color(0xFF58A6FF),
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ADDRESS", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
            Text("AMOUNT", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.weight(0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("% OF TOTAL", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.weight(0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1E2D45))
        )
        
        // Table Rows
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            holders.take(50).forEachIndexed { index, holder ->
                val balanceRaw = holder.balance.toDoubleOrNull() ?: 0.0
                val realBalance = if (decimals in 0..30) balanceRaw / 10.0.pow(decimals) else balanceRaw
                
                val percentage = if (totalSupplyRaw > 0) (balanceRaw / totalSupplyRaw) * 100 else 0.0
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = formatAddress(holder.address),
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        text = format.format(realBalance),
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.weight(0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val pctColor = if (percentage > 5.0) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                    Text(
                        text = String.format("%.2f%%", percentage),
                        fontSize = 12.sp,
                        color = pctColor,
                        fontWeight = if (percentage > 5.0) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(0.4f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

private fun formatAddress(address: String): String {
    if (address.length < 12) return address
    return address.take(6) + "…" + address.takeLast(4)
}
