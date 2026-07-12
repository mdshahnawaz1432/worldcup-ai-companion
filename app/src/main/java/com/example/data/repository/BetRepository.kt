package com.example.data.repository

import com.example.data.local.BetDao
import com.example.data.model.PlacedBet
import com.example.data.model.Match
import kotlinx.coroutines.flow.Flow

class BetRepository(private val betDao: BetDao) {
    val allBets: Flow<List<PlacedBet>> = betDao.getAllBetsFlow()

    suspend fun getBetById(id: Long): PlacedBet? = betDao.getBetById(id)

    suspend fun placeBet(
        matchId: String,
        matchName: String,
        selectedMarket: String,
        selectedOutcome: String,
        odds: Double,
        stake: Double
    ): Long {
        val bet = PlacedBet(
            matchId = matchId,
            matchName = matchName,
            selectedMarket = selectedMarket,
            selectedOutcome = selectedOutcome,
            odds = odds,
            stake = stake,
            status = "PENDING",
            profitLoss = 0.0
        )
        return betDao.insertBet(bet)
    }

    suspend fun settlePendingBets(liveMatches: List<Match>) {
        val pendingBets = betDao.getPendingBets()
        for (bet in pendingBets) {
            val matchingMatch = liveMatches.find { it.id == bet.matchId } ?: continue
            
            // Only settle if match status is FINISHED
            if (matchingMatch.status == "FINISHED") {
                val winnerOutcome = when {
                    matchingMatch.homeScore > matchingMatch.awayScore -> "HOME"
                    matchingMatch.awayScore > matchingMatch.homeScore -> "AWAY"
                    else -> "DRAW"
                }

                val won = bet.selectedOutcome == winnerOutcome
                val status = if (won) "WON" else "LOST"
                val profitLoss = if (won) {
                    (bet.stake * bet.odds) - bet.stake
                } else {
                    -bet.stake
                }

                val settledBet = bet.copy(
                    status = status,
                    profitLoss = profitLoss
                )
                betDao.updateBet(settledBet)
            }
        }
    }

    suspend fun updateBet(bet: PlacedBet) {
        betDao.updateBet(bet)
    }

    suspend fun deleteBet(bet: PlacedBet) {
        betDao.deleteBet(bet)
    }

    suspend fun clearAllBets() {
        betDao.clearAllBets()
    }
}
