package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val name: String?,
    val symbol: String?,
    val type: String?,
    val decimals: String?,
    @Json(name = "total_supply") val totalSupply: String?,
    @Json(name = "exchange_rate") val exchangeRate: String?,
    @Json(name = "circulating_market_cap") val circulatingMarketCap: String?,
    val holders: String?,
    @Json(name = "icon_url") val iconUrl: String?
)

@JsonClass(generateAdapter = true)
data class SmartContractResponse(
    @Json(name = "is_verified") val isVerified: Boolean?,
    @Json(name = "is_proxy") val isProxy: Boolean?,
    @Json(name = "compiler_version") val compilerVersion: String?,
    @Json(name = "license_type") val licenseType: String?,
    val abi: Any?
)

@JsonClass(generateAdapter = true)
data class TransfersResponse(
    @Json(name = "next_page_params") val nextPageParams: NextPageParams?
)

@JsonClass(generateAdapter = true)
data class NextPageParams(
    val index: Long?
)
