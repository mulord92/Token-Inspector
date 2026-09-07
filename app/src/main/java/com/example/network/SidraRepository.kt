package com.example.network

import com.example.data.SmartContractModel
import com.example.data.TokenInspectResult
import com.example.data.TokenModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ScanProgress(
    val step: String,
    val isRateLimited: Boolean = false,
    val retrySecondsLeft: Int = 0,
    val attempt: Int = 1,
    val maxAttempts: Int = 3
)

fun normalizeContractAddress(raw: String): String {
    val clean = raw.trim().replace("\"", "").replace("'", "")
    return when {
        clean.startsWith("0x", ignoreCase = true) -> "0x" + clean.substring(2)
        clean.length >= 10 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> "0x$clean"
        else -> clean
    }
}

class SidraRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    // In-memory cache for recent lookups to prevent repeated 429 rate limits
    private val cache = ConcurrentHashMap<String, TokenInspectResult>()

    fun getSampleToken(address: String? = null): TokenInspectResult {
        val addr = address?.let { normalizeContractAddress(it) } ?: "0x2cE9e7c168035D61cc3a3655949C147c063EeCc4"
        return TokenInspectResult(
            token = TokenModel(
                name = "Sidra Multi-Asset Fund",
                symbol = "SMAf",
                type = "ERC-20",
                decimals = "18",
                totalSupply = "1000000000000000000000000",
                exchangeRate = "1.00",
                circulatingMarketCap = "1000000",
                holders = "1420",
                iconUrl = "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/ethereum/info/logo.png"
            ),
            smart = SmartContractModel(
                isVerified = true,
                isProxy = false,
                compilerVersion = "v0.8.20+commit.a1b79de6",
                licenseType = "MIT",
                abiFunctions = listOf("transfer", "balanceOf", "approve", "transferFrom", "allowance", "totalSupply")
            ),
            txCount = "18450"
            ,
            topHolders = listOf(
                com.example.data.TokenHolderModel("0x000000000000000000000000000000000000dead", "500000000000000000000000"),
                com.example.data.TokenHolderModel("0x1234567890123456789012345678901234567890", "200000000000000000000000"),
                com.example.data.TokenHolderModel("0xAbCdEf0123456789aBcDeF0123456789aBcDeF01", "150000000000000000000000"),
                com.example.data.TokenHolderModel("0x5555555555555555555555555555555555555555", "100000000000000000000000"),
                com.example.data.TokenHolderModel("0x9999999999999999999999999999999999999999", "50000000000000000000000")
            )
        )
    }

    suspend fun fetchTokenData(
        address: String,
        onProgress: (suspend (ScanProgress) -> Unit)? = null
    ): Result<TokenInspectResult> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizeContractAddress(address)
            val lookupKey = normalized.lowercase()
            
            // Check cache first
            cache[lookupKey]?.let { cached ->
                return@withContext Result.success(cached)
            }

            onProgress?.invoke(ScanProgress(step = "Connecting to ledger.sidrachain.com…"))

            val tokenUrl = "https://ledger.sidrachain.com/api/v2/tokens/$normalized"
            val tokenResponse = executeWithRetry(tokenUrl, maxRetries = 3, onProgress = onProgress)

            if (!tokenResponse.isSuccessful) {
                val code = tokenResponse.code
                tokenResponse.close()
                return@withContext when (code) {
                    404 -> Result.failure(Exception("Token not found on SidraChain ledger (404). Please verify the contract address."))
                    429 -> Result.failure(RateLimitException("The SidraChain public ledger is receiving high traffic (HTTP 429 Rate Limit). The service is currently cooling down."))
                    else -> Result.failure(Exception("Ledger API returned HTTP status $code."))
                }
            }

            val tokenBody = tokenResponse.body?.string() ?: throw Exception("Empty response received from ledger.")
            tokenResponse.close()

            val tokenJson = JSONObject(tokenBody)

            val tokenModel = TokenModel(
                name = tokenJson.optString("name", "").ifEmpty { null },
                symbol = tokenJson.optString("symbol", "").ifEmpty { null },
                type = tokenJson.optString("type", "ERC-20"),
                decimals = if (tokenJson.has("decimals") && !tokenJson.isNull("decimals")) tokenJson.get("decimals").toString() else "18",
                totalSupply = if (tokenJson.has("total_supply") && !tokenJson.isNull("total_supply")) tokenJson.get("total_supply").toString() else null,
                exchangeRate = if (tokenJson.has("exchange_rate") && !tokenJson.isNull("exchange_rate")) tokenJson.get("exchange_rate").toString() else null,
                circulatingMarketCap = if (tokenJson.has("circulating_market_cap") && !tokenJson.isNull("circulating_market_cap")) tokenJson.get("circulating_market_cap").toString() else null,
                holders = if (tokenJson.has("holders") && !tokenJson.isNull("holders")) tokenJson.get("holders").toString() else null,
                iconUrl = tokenJson.optString("icon_url", "").ifEmpty { null }
            )

            // Small polite delay between requests to avoid rate limits
            delay(250)

            onProgress?.invoke(ScanProgress(step = "Inspecting smart contract bytecode & permissions…"))

            // Optional: Smart Contract details (does not fail primary inspection on 429)
            var smartModel: SmartContractModel? = null
            try {
                val smartUrl = "https://ledger.sidrachain.com/api/v2/smart-contracts/$normalized"
                val smartResponse = executeWithRetry(smartUrl, maxRetries = 2, onProgress = onProgress)
                if (smartResponse.isSuccessful) {
                    val smartBody = smartResponse.body?.string()
                    if (!smartBody.isNullOrEmpty()) {
                        val smartJson = JSONObject(smartBody)
                        val isVerified = smartJson.optBoolean("is_verified", false)
                        val isProxy = smartJson.optBoolean("is_proxy", false)
                        val compiler = smartJson.optString("compiler_version", "").ifEmpty { null }
                        val license = smartJson.optString("license_type", "").ifEmpty { null }
                        val abiArray = smartJson.optJSONArray("abi")
                        val abiFns = mutableListOf<String>()
                        if (abiArray != null) {
                            for (i in 0 until abiArray.length()) {
                                val item = abiArray.optJSONObject(i)
                                val fnName = item?.optString("name", "") ?: ""
                                if (fnName.isNotEmpty()) {
                                    abiFns.add(fnName)
                                }
                            }
                        }
                        smartModel = SmartContractModel(
                            isVerified = isVerified,
                            isProxy = isProxy,
                            compilerVersion = compiler,
                            licenseType = license,
                            abiFunctions = abiFns
                        )
                    }
                }
                smartResponse.close()
            } catch (e: Exception) {
                // Ignore smart contract optional failure
            }

            // Small polite delay between requests
            delay(200)

            onProgress?.invoke(ScanProgress(step = "Checking ledger transfers…"))

            // Optional: Transfers for transaction count
            var txCount: String? = null
            try {
                val txUrl = "https://ledger.sidrachain.com/api/v2/tokens/$normalized/transfers?limit=1"
                val txResponse = executeWithRetry(txUrl, maxRetries = 1, onProgress = null)
                if (txResponse.isSuccessful) {
                    val txBody = txResponse.body?.string()
                    if (!txBody.isNullOrEmpty()) {
                        val txJson = JSONObject(txBody)
                        val nextPage = txJson.optJSONObject("next_page_params")
                        val idx = nextPage?.optLong("index")
                        if (idx != null && idx > 0) {
                            txCount = idx.toString()
                        }
                    }
                }
                txResponse.close()
            } catch (e: Exception) {
                // Ignore transfers optional failure
            }

            delay(200)
            onProgress?.invoke(ScanProgress(step = "Fetching top token holders…"))
            var topHolders: List<com.example.data.TokenHolderModel>? = null
            try {
                val holdersUrl = "https://ledger.sidrachain.com/api/v2/tokens/$normalized/holders"
                val holdersResponse = executeWithRetry(holdersUrl, maxRetries = 1, onProgress = null)
                if (holdersResponse.isSuccessful) {
                    val holdersBody = holdersResponse.body?.string()
                    if (!holdersBody.isNullOrEmpty()) {
                        val holdersJson = JSONObject(holdersBody)
                        val items = holdersJson.optJSONArray("items")
                        if (items != null) {
                            val holdersList = mutableListOf<com.example.data.TokenHolderModel>()
                            for (i in 0 until Math.min(items.length(), 50)) {
                                val item = items.optJSONObject(i)
                                val addrObj = item?.optJSONObject("address")
                                val hash = addrObj?.optString("hash", "") ?: ""
                                val value = item?.optString("value", "") ?: ""
                                if (hash.isNotEmpty()) {
                                    holdersList.add(com.example.data.TokenHolderModel(hash, value))
                                }
                            }
                            topHolders = holdersList
                        }
                    }
                }
                holdersResponse.close()
            } catch (e: Exception) {
                // Ignore holders optional failure
            }
            val finalResult = TokenInspectResult(token = tokenModel, smart = smartModel, txCount = txCount, topHolders = topHolders)
            cache[lookupKey] = finalResult
            Result.success(finalResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeWithRetry(
        url: String,
        maxRetries: Int = 3,
        onProgress: (suspend (ScanProgress) -> Unit)? = null
    ): Response {
        var attempts = 0
        var backoffMs = 1500L

        while (true) {
            attempts++
            val request = Request.Builder().url(url).build()
            val response: Response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                if (attempts >= maxRetries) throw e
                delay(backoffMs)
                backoffMs *= 2
                continue
            }

            if (response.code == 429 && attempts < maxRetries) {
                // Check if server gave Retry-After header
                val retryAfterHeader = response.header("Retry-After")?.toLongOrNull()
                val waitTime = if (retryAfterHeader != null && retryAfterHeader in 1..10) {
                    retryAfterHeader * 1000L
                } else {
                    backoffMs
                }
                response.close()

                val waitSeconds = ((waitTime + 999) / 1000).toInt().coerceAtLeast(1)
                for (s in waitSeconds downTo 1) {
                    onProgress?.invoke(
                        ScanProgress(
                            step = "Ledger API cooling down (Rate Limited)",
                            isRateLimited = true,
                            retrySecondsLeft = s,
                            attempt = attempts,
                            maxAttempts = maxRetries
                        )
                    )
                    delay(1000L)
                }

                onProgress?.invoke(
                    ScanProgress(
                        step = "Retrying ledger request (attempt ${attempts + 1} of $maxRetries)…",
                        isRateLimited = true,
                        retrySecondsLeft = 0,
                        attempt = attempts + 1,
                        maxAttempts = maxRetries
                    )
                )

                backoffMs = (backoffMs * 1.8).toLong()
                continue
            }

            return response
        }
    }
}

class RateLimitException(message: String) : Exception(message)
