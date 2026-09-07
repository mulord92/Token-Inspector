package com.example.data

data class TokenModel(
    val name: String?,
    val symbol: String?,
    val type: String?,
    val decimals: String?,
    val totalSupply: String?,
    val exchangeRate: String?,
    val circulatingMarketCap: String?,
    val holders: String?,
    val iconUrl: String?
)

data class SmartContractModel(
    val isVerified: Boolean,
    val isProxy: Boolean,
    val compilerVersion: String?,
    val licenseType: String?,
    val abiFunctions: List<String>
)

data class TokenHolderModel(
    val address: String,
    val balance: String
)

data class TokenInspectResult(
    val token: TokenModel,
    val smart: SmartContractModel?,
    val txCount: String?,
    val topHolders: List<TokenHolderModel>? = null
)
