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
