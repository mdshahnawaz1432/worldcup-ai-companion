package com.example.data.repository

import com.example.data.local.FavoriteDao
import com.example.data.model.FavoriteMatch
import com.example.data.model.FavoriteTeam
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {
    val favoriteMatches: Flow<List<FavoriteMatch>> = favoriteDao.getFavoriteMatches()
    val favoriteTeams: Flow<List<FavoriteTeam>> = favoriteDao.getFavoriteTeams()

    suspend fun addFavoriteMatch(matchId: String) {
        favoriteDao.insertFavoriteMatch(FavoriteMatch(matchId))
    }

    suspend fun removeFavoriteMatch(matchId: String) {
        favoriteDao.deleteFavoriteMatch(FavoriteMatch(matchId))
    }

    fun isMatchFavorite(matchId: String): Flow<Boolean> = favoriteDao.isMatchFavoriteFlow(matchId)

    suspend fun addFavoriteTeam(teamCode: String) {
        favoriteDao.insertFavoriteTeam(FavoriteTeam(teamCode))
    }

    suspend fun removeFavoriteTeam(teamCode: String) {
        favoriteDao.deleteFavoriteTeam(FavoriteTeam(teamCode))
    }

    fun isTeamFavorite(teamCode: String): Flow<Boolean> = favoriteDao.isTeamFavoriteFlow(teamCode)
}
