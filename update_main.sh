awk '
/^import com.example.ui.RiskRadarSection/ {
    print "import com.example.ui.TokenHoldersSection"
    print $0
    next
}
/ContractProperties\(data = data\)/ {
    print $0
    print "        Spacer(modifier = Modifier.height(14.dp))"
    print "        TokenHoldersSection(data = data)"
    next
}
{ print }
' app/src/main/java/com/example/MainActivity.kt > app/src/main/java/com/example/MainActivity.tmp
mv app/src/main/java/com/example/MainActivity.tmp app/src/main/java/com/example/MainActivity.kt
