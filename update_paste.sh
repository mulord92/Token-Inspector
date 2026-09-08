awk '
/^import androidx.compose.material.icons.outlined.ContentCopy/ {
    print $0
    print "import androidx.compose.material.icons.outlined.ContentPaste"
    next
}
/if \(address.isNotEmpty\(\)\) {/ {
    print $0
    in_copy = 1
    balance = 1
    next
}
in_copy {
    if (/{/) balance++
    if (/}/) balance--
    if (balance == 0) {
        print $0
        print "                    } else {"
        print "                        Row("
        print "                            modifier = Modifier"
        print "                                .clip(RoundedCornerShape(6.dp))"
        print "                                .clickable {"
        print "                                    clipboardManager.getText()?.text?.let { pastedText ->"
        print "                                        onAddressChange(pastedText)"
        print "                                    }"
        print "                                }"
        print "                                .padding(8.dp),"
        print "                            verticalAlignment = Alignment.CenterVertically"
        print "                        ) {"
        print "                            Icon("
        print "                                imageVector = Icons.Outlined.ContentPaste,"
        print "                                contentDescription = \"Paste\","
        print "                                tint = Color(0xFF64748B),"
        print "                                modifier = Modifier.size(14.dp)"
        print "                            )"
        print "                            Spacer(modifier = Modifier.width(4.dp))"
        print "                            Text(\"PASTE\", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)"
        print "                        }"
        in_copy = 0
        next
    }
}
{ print }
' app/src/main/java/com/example/MainActivity.kt > app/src/main/java/com/example/MainActivity.tmp
mv app/src/main/java/com/example/MainActivity.tmp app/src/main/java/com/example/MainActivity.kt
