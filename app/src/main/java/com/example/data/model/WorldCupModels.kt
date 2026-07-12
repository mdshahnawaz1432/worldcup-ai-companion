package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Match(
    val id: String,
    val homeTeamName: String,
    val homeTeamCode: String,
    val awayTeamName: String,
    val awayTeamCode: String,
    val homeScore: Int,
    val awayScore: Int,
    val status: String, // "LIVE", "UPCOMING", "FINISHED"
    val minute: Int,
    val competition: String,
    val startTime: String,
    val group: String,
    val venue: String
)

data class MatchStats(
    val matchId: String,
    val homePossession: Int,
    val awayPossession: Int,
    val homeShots: Int,
    val awayShots: Int,
    val homeShotsOnTarget: Int,
    val awayShotsOnTarget: Int,
    val homeCorners: Int,
    val awayCorners: Int,
    val homeFouls: Int,
    val awayFouls: Int,
    val homeYellowCards: Int,
    val awayYellowCards: Int,
    val homeRedCards: Int,
    val awayRedCards: Int,
    val homeXG: Double,
    val awayXG: Double
)

data class MatchEvent(
    val id: String,
    val matchId: String,
    val type: String, // "GOAL", "YELLOW_CARD", "RED_CARD", "SUBSTITUTION"
    val minute: Int,
    val team: String, // "home", "away"
    val player1: String,
    val player2: String? = null,
    val detail: String = ""
)

data class Team(
    val code: String, // e.g. "BRA"
    val name: String,
    val group: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val points: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val form: String // e.g. "W,D,W,L,W"
)

data class Odds(
    val matchId: String,
    val homeWin: Double,
    val draw: Double,
    val awayWin: Double
)

// --- Room entities ---

@Entity(tableName = "favorite_matches")
data class FavoriteMatch(
    @PrimaryKey val matchId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_teams")
data class FavoriteTeam(
    @PrimaryKey val teamCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "placed_bets")
data class PlacedBet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: String,
    val matchName: String,
    val selectedMarket: String, // e.g. "Match Winner"
    val selectedOutcome: String, // "HOME", "DRAW", "AWAY"
    val odds: Double,
    val stake: Double,
    val status: String, // "PENDING", "WON", "LOST"
    val profitLoss: Double,
    val timestamp: Long = System.currentTimeMillis()
)

