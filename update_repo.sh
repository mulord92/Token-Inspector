awk '
/val finalResult = TokenInspectResult/ {
    print "            delay(200)"
    print "            onProgress?.invoke(ScanProgress(step = \"Fetching top token holders…\"))"
    print "            var topHolders: List<com.example.data.TokenHolderModel>? = null"
    print "            try {"
    print "                val holdersUrl = \"https://ledger.sidrachain.com/api/v2/tokens/$normalized/holders\""
    print "                val holdersResponse = executeWithRetry(holdersUrl, maxRetries = 1, onProgress = null)"
    print "                if (holdersResponse.isSuccessful) {"
    print "                    val holdersBody = holdersResponse.body?.string()"
    print "                    if (!holdersBody.isNullOrEmpty()) {"
    print "                        val holdersJson = JSONObject(holdersBody)"
    print "                        val items = holdersJson.optJSONArray(\"items\")"
    print "                        if (items != null) {"
    print "                            val holdersList = mutableListOf<com.example.data.TokenHolderModel>()"
    print "                            for (i in 0 until Math.min(items.length(), 50)) {"
    print "                                val item = items.optJSONObject(i)"
    print "                                val addrObj = item?.optJSONObject(\"address\")"
    print "                                val hash = addrObj?.optString(\"hash\", \"\") ?: \"\""
    print "                                val value = item?.optString(\"value\", \"\") ?: \"\""
    print "                                if (hash.isNotEmpty()) {"
    print "                                    holdersList.add(com.example.data.TokenHolderModel(hash, value))"
    print "                                }"
    print "                            }"
    print "                            topHolders = holdersList"
    print "                        }"
    print "                    }"
    print "                }"
    print "                holdersResponse.close()"
    print "            } catch (e: Exception) {"
    print "                // Ignore holders optional failure"
    print "            }"
    print "            val finalResult = TokenInspectResult(token = tokenModel, smart = smartModel, txCount = txCount, topHolders = topHolders)"
    next
}
{ print }
' app/src/main/java/com/example/network/SidraRepository.kt > app/src/main/java/com/example/network/SidraRepository.tmp
mv app/src/main/java/com/example/network/SidraRepository.tmp app/src/main/java/com/example/network/SidraRepository.kt
