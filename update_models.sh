awk '
/data class TokenInspectResult/ {
    print "data class TokenHolderModel("
    print "    val address: String,"
    print "    val balance: String"
    print ")"
    print ""
}
/val txCount: String\?/ {
    print $0
    print "    val topHolders: List<TokenHolderModel>? = null"
    next
}
{ print }
' app/src/main/java/com/example/data/TokenModels.kt > app/src/main/java/com/example/data/TokenModels.tmp
mv app/src/main/java/com/example/data/TokenModels.tmp app/src/main/java/com/example/data/TokenModels.kt
