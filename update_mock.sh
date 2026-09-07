awk '
/txCount = "18450"/ {
    print $0
    print "            ,"
    print "            topHolders = listOf("
    print "                com.example.data.TokenHolderModel(\"0x000000000000000000000000000000000000dead\", \"500000000000000000000000\"),"
    print "                com.example.data.TokenHolderModel(\"0x1234567890123456789012345678901234567890\", \"200000000000000000000000\"),"
    print "                com.example.data.TokenHolderModel(\"0xAbCdEf0123456789aBcDeF0123456789aBcDeF01\", \"150000000000000000000000\"),"
    print "                com.example.data.TokenHolderModel(\"0x5555555555555555555555555555555555555555\", \"100000000000000000000000\"),"
    print "                com.example.data.TokenHolderModel(\"0x9999999999999999999999999999999999999999\", \"50000000000000000000000\")"
    print "            )"
    next
}
{ print }
' app/src/main/java/com/example/network/SidraRepository.kt > app/src/main/java/com/example/network/SidraRepository.tmp
mv app/src/main/java/com/example/network/SidraRepository.tmp app/src/main/java/com/example/network/SidraRepository.kt
