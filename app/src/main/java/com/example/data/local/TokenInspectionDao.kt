package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenInspectionDao {

    @Query("SELECT * FROM inspected_tokens ORDER BY timestamp DESC LIMIT 10")
    fun getRecentInspections(): Flow<List<InspectedTokenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: InspectedTokenEntity)

    @Query("DELETE FROM inspected_tokens WHERE address = :address")
    suspend fun deleteByAddress(address: String)

    @Query("DELETE FROM inspected_tokens")
    suspend fun clearAll()

    @Query("DELETE FROM inspected_tokens WHERE address NOT IN (SELECT address FROM inspected_tokens ORDER BY timestamp DESC LIMIT 10)")
    suspend fun pruneOldRecords()

    @Transaction
    suspend fun saveAndPrune(entity: InspectedTokenEntity) {
        insertOrUpdate(entity)
        pruneOldRecords()
    }
}
