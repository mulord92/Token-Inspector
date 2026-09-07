package com.example

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.InspectedTokenEntity
import com.example.ui.ContractProperties
import com.example.ui.TokenHoldersSection
import com.example.ui.RiskRadarSection
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppState
import com.example.viewmodel.RiskFlag
import com.example.viewmodel.TokenInspectorViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val recentInspections by viewModel.recentInspections.collectAsState()
    var address by remember { mutableStateOf("") }
    val isRateLimited = (state as? AppState.Scanning)?.progress?.isRateLimited == true ||
            (state as? AppState.Error)?.isRateLimit == true
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header(isRateLimited = isRateLimited)
        
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
                isScanning = state is AppState.Scanning,
                recentItems = recentInspections.take(3)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            when (val s = state) {
                is AppState.Idle -> {
                    if (recentInspections.isNotEmpty()) {
                        RecentInspectionsSection(
                            inspections = recentInspections,
                            onSelect = { selectedAddress ->
                                address = selectedAddress
                                viewModel.scan(selectedAddress)
                            },
                            onDelete = { addrToDelete ->
                                viewModel.deleteFromHistory(addrToDelete)
                            },
                            onClear = {
                                viewModel.clearHistory()
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    IdleExplainer()
                }
                is AppState.Scanning -> ScanningView(s.progress)
                is AppState.Error -> ErrorView(
                    msg = s.message,
                    isRateLimit = s.isRateLimit,
                    onRetry = {
                        if (address.isNotBlank()) {
                            viewModel.scan(address)
                        } else {
                            viewModel.reset()
                        }
                    },
                    onLoadDemo = {
                        viewModel.loadSampleToken(address)
                    }
                )
                is AppState.Done -> {
                    ResultsView(
                        address = address,
                        data = s.data,
                        analysis = s.analysis,
                        onReset = { 
                            viewModel.reset() 
                            address = ""
                        }
                    )

                    if (recentInspections.size > 1) {
                        Spacer(modifier = Modifier.height(24.dp))
                        RecentInspectionsSection(
                            inspections = recentInspections.filter { !it.address.equals(address.trim(), ignoreCase = true) },
                            title = "OTHER RECENT INSPECTIONS",
                            onSelect = { selectedAddress ->
                                address = selectedAddress
                                viewModel.scan(selectedAddress)
                            },
                            onDelete = { addrToDelete ->
                                viewModel.deleteFromHistory(addrToDelete)
                            },
                            onClear = {
                                viewModel.clearHistory()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Header(isRateLimited: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "rate_limit_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("SIDRACHAIN SENTINEL", color = Color(0xFF58A6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Token Inspector", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F5F9))
        }
        
        Row(
            modifier = Modifier
                .background(
                    if (isRateLimited) Color(0xFF281804) else Color(0xFF161B22), 
                    CircleShape
                )
                .border(
                    1.dp, 
                    if (isRateLimited) Color(0xFFF59E0B) else Color(0xFF30363D), 
                    CircleShape
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isRateLimited) Color(0xFFF59E0B).copy(alpha = alpha) else Color(0xFF34D399), 
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRateLimited) "RATE LIMITED · RETRYING" else "LIVE LEDGER", 
                fontSize = 10.sp, 
                color = if (isRateLimited) Color(0xFFFBBF24) else Color(0xFF94A3B8), 
                fontWeight = FontWeight.Bold
            )
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
fun InputSection(
    address: String, 
    onAddressChange: (String) -> Unit, 
    onScan: () -> Unit, 
    isScanning: Boolean,
    recentItems: List<InspectedTokenEntity> = emptyList()
) {
    val isValid = address.trim().length >= 10
    val isMissing0x = !address.trim().startsWith("0x", ignoreCase = true) && address.trim().length in 38..44
    
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1829), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TOKEN CONTRACT ADDRESS", color = Color(0xFF475569), fontSize = 11.sp, letterSpacing = 1.5.sp)
            if (isMissing0x) {
                Text(
                    text = "Auto-prepending 0x",
                    color = Color(0xFF00D4FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = { Text("0x… or sidra1…", fontSize = 13.sp, color = Color(0xFF64748B)) },
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFFE2E8F0)),
                enabled = !isScanning,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1E4A6E),
                    unfocusedBorderColor = if(isValid) Color(0xFF1E4A6E) else Color(0xFF1E2D45),
                    focusedContainerColor = Color(0xFF070C18),
                    unfocusedContainerColor = Color(0xFF070C18)
                ),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    if (address.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(address))
                                    coroutineScope.launch {
                                        isCopied = true
                                        delay(1800)
                                        isCopied = false
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (isCopied) Color(0xFF22C55E) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            if (isCopied) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPIED", fontSize = 10.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            
            // Scan Button Micro-interaction
            val infiniteTransition = rememberInfiniteTransition()
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing)
                )
            )

            Button(
                onClick = onScan,
                enabled = isValid && !isScanning,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4FF),
                    contentColor = Color(0xFF070C18),
                    disabledContainerColor = Color(0xFF1E2D45),
                    disabledContentColor = if (isScanning) Color(0xFF00D4FF) else Color(0xFF334155)
                ),
                modifier = Modifier.height(52.dp),
                contentPadding = PaddingValues(horizontal = 22.dp)
            ) {
                if (isScanning) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Scanning",
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = angle }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decompiling…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Inspect", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Presets & Recent Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(
                Pair("SMAF", "0x2cE9e7c168035D61cc3a3655949C147c063EeCc4"),
                Pair("WSDA", "0xE409d78FDB95B917b861FE94bBEe391F0d1E6182")
            )
            
            // Recent items can just be mapped as Pair(label, address) and take max 1 or 2
            val displayItems = (recentItems.take(1).map { item ->
                val label = item.symbol?.takeIf { it.isNotBlank() } ?: item.name?.takeIf { it.isNotBlank() }?.take(6) ?: "Recent"
                Pair(label, item.address)
            } + presets).distinctBy { it.second }.take(3)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                displayItems.forEach { preset ->
                    val isActive = address == preset.second
                    val bgColor = if (isActive) Color(0xFF00D4FF).copy(alpha = 0.15f) else Color(0xFF07101F)
                    val textColor = if (isActive) Color(0xFF00D4FF) else Color(0xFF475569)
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable { onAddressChange(preset.second) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(preset.first, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningView(progress: com.example.network.ScanProgress) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    if (progress.isRateLimited) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1508), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rate Limit / Retrying Badge Pill
            Row(
                modifier = Modifier
                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFF59E0B).copy(alpha = pulseAlpha), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (progress.retrySecondsLeft > 0) "RATE LIMITED · COOLING DOWN" else "RETRYING REQUEST…",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Animated loader with amber accent
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .drawBehind {
                        drawArc(
                            color = Color(0xFF38290D),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(3.5.dp.toPx())
                        )
                        drawArc(
                            color = Color(0xFFF59E0B),
                            startAngle = rotation,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(3.5.dp.toPx())
                        )
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = progress.step,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF1F5F9),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cooldown Details Pills
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (progress.retrySecondsLeft > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2B1D0A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4A3212), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⏱ Retrying in ${progress.retrySecondsLeft}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFDE68A)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF2B1D0A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF4A3212), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Attempt ${progress.attempt}/${progress.maxAttempts}",
                        fontSize = 11.sp,
                        color = Color(0xFFD1D5DB)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "The SidraChain public ledger is managing high traffic. Automatic backoff is safely pacing requests to prevent disconnection.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    } else {
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
            Text(progress.step, fontSize = 13.sp, color = Color(0xFFF1F5F9), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Querying ledger.sidrachain.com Blockscout API v2", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun ErrorView(
    msg: String,
    isRateLimit: Boolean = false,
    onRetry: () -> Unit,
    onLoadDemo: () -> Unit
) {
    val bgColor = if (isRateLimit) Color(0xFF241806) else Color(0xFF1C0A0A)
    val borderColor = if (isRateLimit) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0x66EF4444)
    val accentColor = if (isRateLimit) Color(0xFFF59E0B) else Color(0xFFEF4444)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(if (isRateLimit) "⏳" else "⚠", fontSize = 20.sp, color = accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRateLimit) "Rate Limited (HTTP 429)" else "Scan Failed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (isRateLimit) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("COOLING DOWN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(msg, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Options Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.2f),
                    contentColor = accentColor
                ),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isRateLimit) "Retry Inspection" else "Try Again", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            if (isRateLimit) {
                OutlinedButton(
                    onClick = onLoadDemo,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF58A6FF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF388BFD).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Load Demo Token", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (isRateLimit) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "💡 Tip: The SidraChain public Blockscout API enforces request quotas. Exponential backoff will automatically retry, or you can load the verified specimen.",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ResultsView(address: String, data: com.example.data.TokenInspectResult, analysis: com.example.viewmodel.RiskAnalysis, onReset: () -> Unit) {
    val token = data.token
    val smart = data.smart
    val holderCount = token.holders?.toDoubleOrNull()?.toInt() ?: 0
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
                    color = Color(0xFF58A6FF),
                    modifier = Modifier
                        .background(Color(0x1858A6FF), RoundedCornerShape(5.dp))
                        .clickable { 
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ledger.sidrachain.com/token/${address.trim()}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore intent failure if no browser app is available
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "New scan",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(5.dp))
                        .clickable { onReset() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))

        ContractProperties(data = data)
        Spacer(modifier = Modifier.height(14.dp))
        TokenHoldersSection(data = data)

        Spacer(modifier = Modifier.height(14.dp))

        // Risk Radar Multi-Factor Chart
        RiskRadarSection(data = data, analysis = analysis)

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
                Pair("Holders", if (holderCount > 0) holderCount.toString() else (token.holders ?: "—")),
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
                    Text("$" + String.format(java.util.Locale.US, "%.6f", price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58A6FF))
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
fun DetailBox(label: String, value: String, smart: com.example.data.SmartContractModel?, modifier: Modifier = Modifier) {
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
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("WHAT THIS CHECKS", fontSize = 11.sp, color = Color(0xFF334155), letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 4.dp))
        
        val checks = listOf(
            Pair("Mint Authority", "Can the owner create new tokens after launch? Unlimited minting destroys token value."),
            Pair("Liquidity Lock", "Is trading liquidity verifiably locked in a time-lock contract, or just 'promised'?"),
            Pair("Owner Backdoors", "Can the deployer pause transfers, blacklist wallets, or drain funds through hidden functions?"),
            Pair("Honeypot Test", "A buy/sell simulation that detects if the contract blocks token sales — the most common trap.")
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            checks.forEach { check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1829), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "⬡",
                        color = Color(0xFF00D4FF),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = check.first,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        Text(
                            text = check.second,
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentInspectionsSection(
    inspections: List<InspectedTokenEntity>,
    title: String = "RECENT INSPECTIONS (LAST 10)",
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E2D45), RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${inspections.size}/10",
                        fontSize = 9.sp,
                        color = Color(0xFF93C5FD),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(
                onClick = onClear,
                modifier = Modifier
                    .height(32.dp)
                    .testTag("clear_history_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Clear All",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inspections.forEach { item ->
                RecentInspectionCard(
                    item = item,
                    onSelect = { onSelect(item.address) },
                    onDelete = { onDelete(item.address) }
                )
            }
        }
    }
}

@Composable
fun RecentInspectionCard(
    item: InspectedTokenEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val verdictColor = Color(item.riskVerdictColorLong.toInt())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("recent_inspection_item_${item.address}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon or Token Avatar
        if (!item.iconUrl.isNullOrEmpty()) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = item.name ?: "Token Icon",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF0D1117), CircleShape)
                    .border(1.dp, verdictColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.symbol?.take(3)?.uppercase() ?: (item.name?.take(2)?.uppercase() ?: "0x"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center token details & risk summary
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = item.name ?: "Unnamed Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.symbol.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.symbol,
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .background(Color(0xFF1E2D45), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                // Relative Timestamp
                Text(
                    text = formatRelativeTime(item.timestamp),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Risk Summary Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Score badge
                Box(
                    modifier = Modifier
                        .background(verdictColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, verdictColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${item.riskScore}/100 · ${item.riskVerdict}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                }

                if (!item.topRiskSummary.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.topRiskSummary,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Contract address preview
            Text(
                text = "${item.address.take(8)}…${item.address.takeLast(6)}",
                fontSize = 10.sp,
                color = Color(0xFF58A6FF),
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(36.dp)
                .testTag("delete_history_${item.address}")
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete search from history",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}

fun fmtTokenSupply(supplyStr: String?, decimalsStr: String?): String {
    if (supplyStr.isNullOrEmpty()) return "—"
    return try {
        val supply = supplyStr.toDoubleOrNull() ?: return supplyStr
        val decimals = decimalsStr?.toIntOrNull() ?: 18
        val n = if (decimals in 0..30) supply / 10.0.pow(decimals) else supply
        if (n.isNaN() || n.isInfinite()) return supplyStr
        when {
            n >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", n / 1e9)
            n >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", n / 1e6)
            n >= 1e3 -> String.format(java.util.Locale.US, "%.2fK", n / 1e3)
            else -> NumberFormat.getNumberInstance(java.util.Locale.US).apply { maximumFractionDigits = 4 }.format(n)
        }
    } catch (e: Exception) {
        supplyStr
    }
}

