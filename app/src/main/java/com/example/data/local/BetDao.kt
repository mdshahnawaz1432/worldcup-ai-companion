package com.example.data.local

import androidx.room.*
import com.example.data.model.PlacedBet
import kotlinx.coroutines.flow.Flow

@Dao
interface BetDao {
    @Query("SELECT * FROM placed_bets ORDER BY timestamp DESC")
    fun getAllBetsFlow(): Flow<List<PlacedBet>>

    @Query("SELECT * FROM placed_bets WHERE id = :id")
    suspend fun getBetById(id: Long): PlacedBet?

    @Query("SELECT * FROM placed_bets WHERE status = 'PENDING'")
    suspend fun getPendingBets(): List<PlacedBet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBet(bet: PlacedBet): Long

    @Update
    suspend fun updateBet(bet: PlacedBet)

    @Delete
    suspend fun deleteBet(bet: PlacedBet)

    @Query("DELETE FROM placed_bets")
    suspend fun clearAllBets()
}
