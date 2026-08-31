package com.example.network

import com.example.data.SmartContractResponse
import com.example.data.TokenResponse
import com.example.data.TransfersResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BlockscoutApi {
    @GET("tokens/{address}")
    suspend fun getToken(@Path("address") address: String): TokenResponse

    @GET("smart-contracts/{address}")
    suspend fun getSmartContract(@Path("address") address: String): SmartContractResponse

    @GET("tokens/{address}/holders")
    suspend fun getHolders(@Path("address") address: String, @Query("limit") limit: Int = 1): Any

    @GET("tokens/{address}/transfers")
    suspend fun getTransfers(@Path("address") address: String, @Query("limit") limit: Int = 1): TransfersResponse
}
