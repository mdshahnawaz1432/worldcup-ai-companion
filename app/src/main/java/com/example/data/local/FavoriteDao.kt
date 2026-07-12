package com.example.data.local

import androidx.room.*
import com.example.data.model.FavoriteMatch
import com.example.data.model.FavoriteTeam
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    // --- Favorite Matches ---
    @Query("SELECT * FROM favorite_matches ORDER BY timestamp DESC")
    fun getFavoriteMatches(): Flow<List<FavoriteMatch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteMatch(favoriteMatch: FavoriteMatch)

    @Delete
    suspend fun deleteFavoriteMatch(favoriteMatch: FavoriteMatch)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_matches WHERE matchId = :matchId)")
    fun isMatchFavoriteFlow(matchId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_matches WHERE matchId = :matchId)")
    suspend fun isMatchFavorite(matchId: String): Boolean

    // --- Favorite Teams ---
    @Query("SELECT * FROM favorite_teams ORDER BY timestamp DESC")
    fun getFavoriteTeams(): Flow<List<FavoriteTeam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteTeam(favoriteTeam: FavoriteTeam)

    @Delete
    suspend fun deleteFavoriteTeam(favoriteTeam: FavoriteTeam)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_teams WHERE teamCode = :teamCode)")
    fun isTeamFavoriteFlow(teamCode: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_teams WHERE teamCode = :teamCode)")
    suspend fun isTeamFavorite(teamCode: String): Boolean
}
