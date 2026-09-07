package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TokenInspectResult
import com.example.viewmodel.RiskAnalysis
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

data class RadarAxisData(
    val id: String,
    val name: String,
    val shortName: String,
    val riskValue: Float, // 0.0f (safe) to 1.0f (high risk)
    val description: String,
    val status: String, // "SAFE", "MODERATE", "CRITICAL"
    val statusColor: Color
)

fun calculateRadarAxes(
    data: TokenInspectResult,
    analysis: RiskAnalysis
): List<RadarAxisData> {
    val token = data.token
    val smart = data.smart
    val abi = smart?.abiFunctions ?: emptyList()

    // 1. Proxy Pattern
    val isProxy = smart?.isProxy == true
    val proxyScore = if (isProxy) 0.85f else 0.05f
    val proxyStatus = if (isProxy) "WARNING" else "SAFE"
    val proxyColor = if (isProxy) Color(0xFFF59E0B) else Color(0xFF34D399)
    val proxyDesc = if (isProxy) "Upgradeable Proxy (Logic can be altered)" else "Direct Immutable Contract"

    // 2. Mint Functionality
    val hasMint = abi.any { fn -> listOf("mint", "issue", "create", "generatetokens").any { fn.contains(it, ignoreCase = true) } }
    val mintScore = if (hasMint) 0.95f else 0.05f
    val mintStatus = if (hasMint) "CRITICAL" else "SAFE"
    val mintColor = if (hasMint) Color(0xFFEF4444) else Color(0xFF34D399)
    val mintDesc = if (hasMint) "Arbitrary Mint / Inflation Enabled" else "Fixed Supply (No Minting)"

    // 3. Owner Privileges (Self-destruct / Pause / Blacklist)
    val hasSelfDestruct = abi.any { fn -> listOf("selfdestruct", "destroy", "kill").any { fn.contains(it, ignoreCase = true) } }
    val hasPause = abi.any { fn -> listOf("pause", "freeze", "blacklist", "ban", "lock").any { fn.contains(it, ignoreCase = true) } }
    val privScore = when {
        hasSelfDestruct -> 1.00f
        hasPause -> 0.75f
        else -> 0.05f
    }
    val privStatus = when {
        hasSelfDestruct -> "CRITICAL"
        hasPause -> "WARNING"
        else -> "SAFE"
    }
    val privColor = when {
        hasSelfDestruct -> Color(0xFFEF4444)
        hasPause -> Color(0xFFF59E0B)
        else -> Color(0xFF34D399)
    }
    val privDesc = when {
        hasSelfDestruct -> "Self-Destruct / Killswitch Detected"
        hasPause -> "Transfer Pause & Blacklisting Capabilities"
        else -> "No Centralized Privilege Trapdoors"
    }

    // 4. Code Verification
    val isVerified = smart?.isVerified == true
    val verifScore = if (isVerified) 0.05f else 0.90f
    val verifStatus = if (isVerified) "SAFE" else "HIGH RISK"
    val verifColor = if (isVerified) Color(0xFF34D399) else Color(0xFFEF4444)
    val verifDesc = if (isVerified) "Bytecode & Source Verified" else "Unverified Bytecode"

    // 5. Holder Distribution
    val holders = token.holders?.toDoubleOrNull()?.toInt() ?: 0
    val holderScore = when {
        holders in 1..49 -> 0.70f
        holders == 0 && token.holders == null -> 0.50f
        else -> 0.05f
    }
    val holderStatus = if (holderScore > 0.4f) "MODERATE" else "SAFE"
    val holderColor = if (holderScore > 0.4f) Color(0xFFF59E0B) else Color(0xFF34D399)
    val holderDesc = if (holders in 1..49) "Low Holder Count ($holders Wallets)" else "Distributed (${holders.coerceAtLeast(1)} Holders)"

    // 6. Supply & Integrity
    val supply = token.totalSupply?.toDoubleOrNull() ?: 0.0
    val decimals = token.decimals?.toIntOrNull() ?: 18
    val realSupply = try {
        if (decimals in 0..30) supply / 10.0.pow(decimals) else supply
    } catch (e: Exception) {
        supply
    }
    val isAstro = realSupply > 1e15
    val hasNoMeta = token.iconUrl.isNullOrEmpty()
    val supplyScore = when {
        isAstro -> 0.80f
        hasNoMeta -> 0.35f
        else -> 0.05f
    }
    val supplyStatus = if (supplyScore >= 0.7f) "WARNING" else if (supplyScore > 0.2f) "MODERATE" else "SAFE"
    val supplyColor = if (supplyScore >= 0.7f) Color(0xFFEF4444) else if (supplyScore > 0.2f) Color(0xFF94A3B8) else Color(0xFF34D399)
    val supplyDesc = if (isAstro) "Astronomical Token Supply" else if (hasNoMeta) "Missing Verified Metadata" else "Standard Supply Architecture"

    return listOf(
        RadarAxisData("proxy", "Proxy Pattern", "Proxy", proxyScore, proxyDesc, proxyStatus, proxyColor),
        RadarAxisData("mint", "Mint Functionality", "Mint", mintScore, mintDesc, mintStatus, mintColor),
        RadarAxisData("privileges", "Owner Privileges", "Privileges", privScore, privDesc, privStatus, privColor),
        RadarAxisData("verification", "Code Verification", "Verification", verifScore, verifDesc, verifStatus, verifColor),
        RadarAxisData("holders", "Holder Distribution", "Holders", holderScore, holderDesc, holderStatus, holderColor),
        RadarAxisData("supply", "Supply & Integrity", "Supply", supplyScore, supplyDesc, supplyStatus, supplyColor)
    )
}

@Composable
fun RiskRadarSection(
    data: TokenInspectResult,
    analysis: RiskAnalysis,
    modifier: Modifier = Modifier
) {
    val axes = remember(data, analysis) { calculateRadarAxes(data, analysis) }
    var selectedAxis by remember { mutableStateOf<RadarAxisData?>(null) }

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(data) {
        animationTrigger = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "radar_polygon_growth"
    )

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("risk_radar_chart_section")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = analysis.verdictColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RISK PROFILE · RADAR ANALYSIS",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .background(analysis.verdictColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, analysis.verdictColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${analysis.score}% OVERALL RISK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = analysis.verdictColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Radar Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color(0xFF0D1117), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF21262D), RoundedCornerShape(16.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            RadarCanvas(
                axes = axes,
                progress = animatedProgress,
                verdictColor = analysis.verdictColor,
                textMeasurer = textMeasurer,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Detailed Axis Breakdown Grid
        Text(
            text = "RISK FACTOR BREAKDOWN",
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            axes.forEach { axis ->
                RadarFactorItem(
                    axis = axis,
                    isSelected = selectedAxis?.id == axis.id,
                    onClick = {
                        selectedAxis = if (selectedAxis?.id == axis.id) null else axis
                    }
                )
            }
        }
    }
}

@Composable
fun RadarCanvas(
    axes: List<RadarAxisData>,
    progress: Float,
    verdictColor: Color,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (minOf(size.width, size.height) / 2f) * 0.62f
        val numAxes = axes.size

        val levels = listOf(0.25f, 0.50f, 0.75f, 1.0f)

        // 1. Draw web grid rings (concentric polygons)
        levels.forEach { level ->
            val ringRadius = maxRadius * level
            val ringPath = Path()
            for (i in 0 until numAxes) {
                val angle = (-Math.PI / 2 + i * (2 * Math.PI / numAxes)).toFloat()
                val x = center.x + ringRadius * cos(angle)
                val y = center.y + ringRadius * sin(angle)
                if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
            }
            ringPath.close()

            drawPath(
                path = ringPath,
                color = if (level == 1.0f) Color(0xFF30363D) else Color(0xFF21262D),
                style = Stroke(
                    width = if (level == 1.0f) 1.5.dp.toPx() else 1.dp.toPx()
                )
            )
        }

        // 2. Draw spokes radiating from center
        for (i in 0 until numAxes) {
            val angle = (-Math.PI / 2 + i * (2 * Math.PI / numAxes)).toFloat()
            val endX = center.x + maxRadius * cos(angle)
            val endY = center.y + maxRadius * sin(angle)

            drawLine(
                color = Color(0xFF21262D),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 3. Draw animated Risk Exposure Polygon
        val polygonPath = Path()
        val points = mutableListOf<Offset>()

        for (i in 0 until numAxes) {
            val axis = axes[i]
            val value = (axis.riskValue * progress).coerceIn(0.05f, 1.0f)
            val r = maxRadius * value
            val angle = (-Math.PI / 2 + i * (2 * Math.PI / numAxes)).toFloat()
            val pt = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            points.add(pt)

            if (i == 0) polygonPath.moveTo(pt.x, pt.y) else polygonPath.lineTo(pt.x, pt.y)
        }
        polygonPath.close()

        // Fill polygon with soft glowing gradient
        drawPath(
            path = polygonPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    verdictColor.copy(alpha = 0.40f * progress),
                    verdictColor.copy(alpha = 0.15f * progress)
                ),
                center = center,
                radius = maxRadius
            ),
            style = Fill
        )

        // Draw polygon outline
        drawPath(
            path = polygonPath,
            color = verdictColor.copy(alpha = 0.9f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. Draw vertex dots on each axis point
        points.forEachIndexed { index, pt ->
            val axisColor = axes[index].statusColor
            drawCircle(
                color = Color(0xFF0D1117),
                radius = 5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = axisColor,
                radius = 3.5.dp.toPx(),
                center = pt
            )
        }

        // 5. Draw Axis Labels around perimeter
        for (i in 0 until numAxes) {
            val axis = axes[i]
            val angle = (-Math.PI / 2 + i * (2 * Math.PI / numAxes)).toFloat()
            val labelDistance = maxRadius + 22.dp.toPx()
            val lx = center.x + labelDistance * cos(angle)
            val ly = center.y + labelDistance * sin(angle)

            val textLayoutResult: TextLayoutResult = textMeasurer.measure(
                text = axis.shortName,
                style = TextStyle(
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            )

            val textWidth = textLayoutResult.size.width
            val textHeight = textLayoutResult.size.height

            val drawX = lx - (textWidth / 2f)
            val drawY = ly - (textHeight / 2f)

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(drawX, drawY)
            )
        }
    }
}

@Composable
fun RadarFactorItem(
    axis: RadarAxisData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val percentage = (axis.riskValue * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isSelected) axis.statusColor.copy(alpha = 0.6f) else Color(0xFF21262D),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(axis.statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = axis.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F5F9)
                )
            }

            // Status Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$percentage%",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(axis.statusColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .border(1.dp, axis.statusColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = axis.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = axis.statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = axis.description,
            fontSize = 11.sp,
            color = Color(0xFF64748B)
        )
    }
}
