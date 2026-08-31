package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.viewmodel.AppState
import com.example.viewmodel.RiskFlag
import com.example.viewmodel.TokenInspectorViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.NumberFormat
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private val viewModel: TokenInspectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0A0C10)
                ) { innerPadding ->
                    TokenInspectorApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TokenInspectorApp(viewModel: TokenInspectorViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    var address by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header()
        
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroSection()
            
            Spacer(modifier = Modifier.height(20.dp))
            
            InputSection(
                address = address,
                onAddressChange = { address = it },
                onScan = { viewModel.scan(address) },
                isScanning = state is AppState.Scanning
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            when (val s = state) {
                is AppState.Idle -> IdleExplainer()
                is AppState.Scanning -> ScanningView(s.step)
                is AppState.Error -> ErrorView(s.message) { viewModel.reset() }
                is AppState.Done -> ResultsView(
                    address = address,
                    data = s.data,
                    analysis = s.analysis,
                    onReset = { 
                        viewModel.reset() 
                        address = ""
                    }
                )
            }
        }
    }
}

@Composable
fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("SIDRACHAIN", color = Color(0xFF58A6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Token Inspector", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F5F9))
        }
        
        Row(
            modifier = Modifier
                .background(Color(0xFF161B22), CircleShape)
                .border(1.dp, Color(0xFF30363D), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF34D399), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text("LIVE LEDGER", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun HeroSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "CONTRACT RISK SCANNER",
            color = Color(0xFF58A6FF),
            fontSize = 10.sp,
            letterSpacing = 2.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Don't trust the warning.\nRead the contract.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF1F5F9),
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "That Uniswap-style \"imported token\" notice is a hardcoded UI default — it proves nothing. Paste any SidraChain token address to read the real contract data from the ledger.",
            color = Color(0xFF64748B),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 350.dp)
        )
    }
}

@Composable
fun InputSection(address: String, onAddressChange: (String) -> Unit, onScan: () -> Unit, isScanning: Boolean) {
    val isValid = address.trim().length >= 10
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("TOKEN CONTRACT ADDRESS", color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(9.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = { Text("0x… — any ERC-20 on SidraChain", fontSize = 12.sp, color = Color(0xFF64748B)) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFFF1F5F9)),
                enabled = !isScanning,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = if(isValid) Color(0xFF58A6FF) else Color(0xFF30363D),
                    focusedContainerColor = Color(0xFF0D1117),
                    unfocusedContainerColor = Color(0xFF0D1117)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onScan,
                enabled = isValid && !isScanning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF58A6FF),
                    contentColor = Color(0xFF0A0C10),
                    disabledContainerColor = Color(0xFF161B22),
                    disabledContentColor = Color(0xFF64748B)
                ),
                modifier = Modifier.height(52.dp)
            ) {
                Text(if (isScanning) "Scanning…" else "Inspect", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Text("Try: ", fontSize = 10.sp, color = Color(0xFF64748B))
            Text(
                "0x2cE9…EeCc4 (SMAf)", 
                fontSize = 10.sp, 
                color = Color(0xFF94A3B8), 
                modifier = Modifier.clickable { onAddressChange("0x2cE9e7c168035D61cc3a3655949C147c063EeCc4") }
            )
            Text(" · ", fontSize = 10.sp, color = Color(0xFF64748B))
            Text(
                "0xE409…6182 (WSDA)", 
                fontSize = 10.sp, 
                color = Color(0xFF94A3B8),
                modifier = Modifier.clickable { onAddressChange("0xE4095a910209D7BE03B55D02F40d4554B1666182") }
            )
        }
    }
}

@Composable
fun ScanningView(step: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .drawBehind {
                    drawArc(
                        color = Color(0xFF30363D),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(3.dp.toPx())
                    )
                    drawArc(
                        color = Color(0xFF58A6FF),
                        startAngle = rotation,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(3.dp.toPx())
                    )
                }
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(step, fontSize = 13.sp, color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(4.dp))
        Text("Querying ledger.sidrachain.com Blockscout API v2", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
fun ErrorView(msg: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C0A0A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x66EF4444), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚠", fontSize = 18.sp, color = Color(0xFFEF4444))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Scan failed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(4.dp))
            Text(msg, fontSize = 12.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, Color(0x66EF4444)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Try again", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ResultsView(address: String, data: com.example.viewmodel.TokenData, analysis: com.example.viewmodel.RiskAnalysis, onReset: () -> Unit) {
    val token = data.token
    val smart = data.smart
    val holderCount = token.holders?.toIntOrNull() ?: 0
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Verdict banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(analysis.verdictColor.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .border(1.dp, analysis.verdictColor.copy(alpha = 0.27f), RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!token.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = token.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(2.dp, analysis.verdictColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(analysis.score.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = analysis.verdictColor)
                        Text("/ 100", fontSize = 8.sp, color = analysis.verdictColor.copy(alpha = 0.7f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(18.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(token.name ?: "Unknown Token", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
                    if (!token.symbol.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = token.symbol,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .background(Color(0xFF1E2D45), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(analysis.verdict, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = analysis.verdictColor)
                    if (!token.iconUrl.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Risk score: ${analysis.score}/100", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(address.trim(), fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "View on Ledger ↗",
                    fontSize = 10.sp,
                    color = Color(0xFF00D4FF),
                    modifier = Modifier
                        .background(Color(0x1800D4FF), RoundedCornerShape(5.dp))
                        .clickable { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ledger.sidrachain.com/token/${address.trim()}"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "New scan",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(5.dp))
                        .clickable { onReset() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Token details grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text("TOKEN DETAILS · LIVE FROM LEDGER.SIDRACHAIN.COM", fontSize = 10.sp, color = Color(0xFF94A3B8), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(14.dp))
            
            val details = listOf(
                Pair("Type", token.type ?: "ERC-20"),
                Pair("Decimals", token.decimals ?: "18"),
                Pair("Total Supply", fmtTokenSupply(token.totalSupply, token.decimals) + " " + (token.symbol ?: "")),
                Pair("Holders", holderCount.toString()),
                Pair("Source Verified", if (smart?.isVerified == true) "✓ Yes" else "✗ No"),
                Pair("Proxy Contract", if (smart?.isProxy == true) "Yes" else "No"),
                Pair("Compiler", smart?.compilerVersion?.take(16) ?: "—"),
                Pair("License", smart?.licenseType ?: "—")
            )
            
            // Grid
            Column {
                for (i in details.indices step 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailBox(details[i].first, details[i].second, smart, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(10.dp))
                        if (i + 1 < details.size) {
                            DetailBox(details[i+1].first, details[i+1].second, smart, Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (i + 2 < details.size) Spacer(modifier = Modifier.height(10.dp))
                }
            }
            
            if (token.exchangeRate != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MARKET PRICE", fontSize = 9.sp, color = Color(0xFF64748B), letterSpacing = 1.sp)
                    val price = token.exchangeRate.toDoubleOrNull() ?: 0.0
                    Text("$" + String.format("%.6f", price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58A6FF))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Contract properties
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text("CONTRACT PROPERTIES", fontSize = 10.sp, color = Color(0xFF94A3B8), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(14.dp))
            
            val props = listOf(
                Pair("Source Verified", smart?.isVerified == true),
                Pair("No Proxy/Upgrade", smart?.isProxy != true),
                Pair("No Mint Function", analysis.flags.none { it.label.contains("Mint Function") }),
                Pair("No Pause/Freeze", analysis.flags.none { it.label.contains("Transfer Control") }),
                Pair("No Self-Destruct", analysis.flags.none { it.label.contains("Self-Destruct") }),
                Pair("Sufficient Holders", holderCount >= 50),
                Pair("Token Metadata", !token.iconUrl.isNullOrEmpty()),
                Pair("Normal Supply", analysis.flags.none { it.label.contains("Supply") })
            )
            
            Column {
                for (i in props.indices step 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PropBox(props[i].first, props[i].second, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        if (i + 1 < props.size) {
                            PropBox(props[i+1].first, props[i+1].second, Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (i + 2 < props.size) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Flags
        if (analysis.flags.isNotEmpty()) {
            Text("RISK FINDINGS", fontSize = 10.sp, color = Color(0xFF94A3B8), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(10.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                analysis.flags.forEach { flag ->
                    RiskFlagItem(flag)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else if (analysis.flags.none { it.level == "critical" || it.level == "high" }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A1F0A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x3322C55E), RoundedCornerShape(12.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✓", fontSize = 16.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text("No critical or high-risk issues detected", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF86EFAC))
                Spacer(modifier = Modifier.height(3.dp))
                Text("Always verify independently — this tool does not replace a professional audit.", fontSize = 11.sp, color = Color(0xFF4B7A4B), textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        
        Text(
            text = "Data sourced live from ledger.sidrachain.com via Blockscout REST API v2. Risk analysis is automated and does not replace a full security audit.",
            fontSize = 10.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                .padding(12.dp)
        )
    }
}

@Composable
fun DetailBox(label: String, value: String, smart: com.example.data.SmartContractResponse?, modifier: Modifier = Modifier) {
    val color = when (label) {
        "Source Verified" -> if (smart?.isVerified == true) Color(0xFF34D399) else Color(0xFFFCA5A5)
        "Proxy Contract" -> if (smart?.isProxy == true) Color(0xFFF59E0B) else Color(0xFF34D399)
        else -> Color(0xFFF1F5F9)
    }
    Column(
        modifier = modifier
            .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), fontSize = 9.sp, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun PropBox(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(if (ok) Color(0xFF161B22) else Color(0xFF1C0A0A), RoundedCornerShape(12.dp))
            .border(1.dp, if (ok) Color(0xFF30363D) else Color(0x33EF4444), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✓" else "✗", fontSize = 12.sp, color = if (ok) Color(0xFF34D399) else Color(0xFFEF4444))
        Spacer(modifier = Modifier.width(9.dp))
        Text(label, fontSize = 11.sp, color = if (ok) Color(0xFF94A3B8) else Color(0xFFFCA5A5))
    }
}

@Composable
fun RiskFlagItem(flag: com.example.viewmodel.RiskFlag) {
    val bg = when(flag.level) {
        "critical" -> Color(0xFF1C0A0A)
        "high" -> Color(0xFF1C1100)
        "medium" -> Color(0xFF0C1020)
        else -> Color(0xFF161B22)
    }
    val border = when(flag.level) {
        "critical" -> Color(0xFFEF4444)
        "high" -> Color(0xFFF59E0B)
        "medium" -> Color(0xFF58A6FF)
        else -> Color(0xFF30363D)
    }
    val dot = border
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, border.copy(alpha = 0.27f), RoundedCornerShape(10.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(7.dp).background(dot, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flag.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = flag.level.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = dot,
                    modifier = Modifier
                        .background(dot.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(flag.detail, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
        }
    }
}

@Composable
fun IdleExplainer() {
    Column {
        Text("WHAT THE SCANNER CHECKS", fontSize = 10.sp, color = Color(0xFF94A3B8), letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(10.dp))
        
        val checks = listOf(
            Pair("Mint Authority", "Reads the ABI for mint functions — owner can print new tokens and dilute your holdings."),
            Pair("Source Verification", "Checks if source code is published on ledger.sidrachain.com so you can read what the contract does."),
            Pair("Proxy/Upgradeable", "Detects if the contract is an upgradeable proxy — logic can be swapped after deployment."),
            Pair("Transfer Controls", "Pause, freeze, and blacklist functions that let owners lock your tokens."),
            Pair("Holder Concentration", "Flags tokens held by very few wallets — high concentration means easy price manipulation.")
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            checks.forEach { check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("⬡", color = Color(0xFF58A6FF), fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(check.first, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(check.second, fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

fun fmtTokenSupply(supplyStr: String?, decimalsStr: String?): String {
    val supply = supplyStr?.toDoubleOrNull() ?: 0.0
    val decimals = decimalsStr?.toIntOrNull() ?: 18
    val n = supply / 10.0.pow(decimals)
    
    return when {
        n >= 1e9 -> String.format("%.2fB", n / 1e9)
        n >= 1e6 -> String.format("%.2fM", n / 1e6)
        n >= 1e3 -> String.format("%.2fK", n / 1e3)
        else -> NumberFormat.getNumberInstance().format(n)
    }
}
