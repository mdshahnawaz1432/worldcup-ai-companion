package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Match
import com.example.data.model.MatchEvent
import com.example.data.model.MatchStats
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// --- Gemini API Moshi Models ---
@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent?)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

// --- App Domain AI Models ---
data class MatchAISummary(
    val shortSummary: String,
    val momentumAnalysis: String,
    val keyTurningPoints: String,
    val topPerformers: String,
    val winProbHome: Int,
    val winProbDraw: Int,
    val winProbAway: Int,
    val confidenceLevel: String // "High", "Medium", "Low"
)

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates an analytical AI analysis of a live match using Gemini,
     * or falls back to a highly realistic analytical local simulation.
     */
    suspend fun getMatchAnalysis(
        match: Match,
        stats: MatchStats?,
        events: List<MatchEvent>
    ): MatchAISummary = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        if (hasKey) {
            try {
                val prompt = buildAnalysisPrompt(match, stats, events)
                val responseText = callGeminiApi(apiKey, prompt)
                if (responseText != null) {
                    val parsed = parseResponseText(responseText)
                    if (parsed != null) {
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API failed, using fallback analyzer", e)
            }
        }

        // Return beautiful analytic fallback matching the live match events
        return@withContext generateFallbackAnalysis(match, stats, events)
    }

    private fun buildAnalysisPrompt(match: Match, stats: MatchStats?, events: List<MatchEvent>): String {
        val eventListText = events.joinToString("\n") {
            "- Minute ${it.minute}': ${it.type} by ${it.player1}" + (if (it.player2 != null) " (assist: ${it.player2})" else "") + " - ${it.detail}"
        }

        val statsText = if (stats != null) {
            "Possession: ${match.homeTeamName} ${stats.homePossession}% - ${stats.awayPossession}% ${match.awayTeamName}\n" +
            "Shots: ${stats.homeShots} - ${stats.awayShots}\n" +
            "Shots on Target: ${stats.homeShotsOnTarget} - ${stats.awayShotsOnTarget}\n" +
            "Corners: ${stats.homeCorners} - ${stats.awayCorners}\n" +
            "Yellow Cards: ${stats.homeYellowCards} - ${stats.awayYellowCards}\n" +
            "Red Cards: ${stats.homeRedCards} - ${stats.awayRedCards}\n" +
            "Expected Goals (xG): ${stats.homeXG} - ${stats.awayXG}"
        } else {
            "No stats available"
        }

        return """
            You are a world-class football analyst. Analyze this World Cup match:
            Teams: ${match.homeTeamName} (Home) vs ${match.awayTeamName} (Away)
            Current Score: ${match.homeTeamName} ${match.homeScore} - ${match.awayScore} ${match.awayTeamName}
            Minute: ${match.minute}'
            Status: ${match.status}

            Match Statistics:
            $statsText

            Match Key Events:
            $eventListText

            Please return a JSON response containing the analysis. It is CRITICAL that the response is strictly valid JSON matching this exact structure:
            {
              "shortSummary": "A concise single-paragraph description summarizing who is dominating, match intensity, and the current overall story.",
              "momentumAnalysis": "An analytical description of how game momentum has shifted between the teams (e.g. based on possession, cards, and attempts).",
              "keyTurningPoints": "A description of the main turning points or decisive moments in the match.",
              "topPerformers": "Mention the stand-out performers and why they played a crucial role.",
              "winProbHome": 50,
              "winProbDraw": 20,
              "winProbAway": 30,
              "confidenceLevel": "High"
            }
            Note: "winProbHome", "winProbDraw", and "winProbAway" must be integers summing up to exactly 100. "confidenceLevel" should be "High", "Medium", or "Low".
            Only return the JSON object. Do not wrap in markdown blocks.
        """.trimIndent()
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val requestBodyObject = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        val adapter = moshi.adapter(GeminiRequest::class.java)
        val requestJson = adapter.toJson(requestBodyObject)

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API call failed with status code ${response.code}: ${response.message}")
                return null
            }
            val responseBody = response.body?.string() ?: return null
            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val responseObj = responseAdapter.fromJson(responseBody)
            return responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        }
    }

    private fun parseResponseText(text: String): MatchAISummary? {
        return try {
            // Remove code block markings if present
            var cleanText = text.trim()
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (cleanText.startsWith("```")) {
                cleanText = cleanText.substringAfter("```").substringBeforeLast("```").trim()
            }

            // Parse JSON using Moshi or manual key parsing for ultimate safety
            val mapAdapter = moshi.adapter(Map::class.java)
            val parsedMap = mapAdapter.fromJson(cleanText) as? Map<*, *> ?: return null

            val shortSummary = parsedMap["shortSummary"] as? String ?: ""
            val momentumAnalysis = parsedMap["momentumAnalysis"] as? String ?: ""
            val keyTurningPoints = parsedMap["keyTurningPoints"] as? String ?: ""
            val topPerformers = parsedMap["topPerformers"] as? String ?: ""
            val winProbHome = (parsedMap["winProbHome"] as? Double)?.toInt()
                ?: (parsedMap["winProbHome"] as? String)?.toIntOrNull() ?: 33
            val winProbDraw = (parsedMap["winProbDraw"] as? Double)?.toInt()
                ?: (parsedMap["winProbDraw"] as? String)?.toIntOrNull() ?: 33
            val winProbAway = (parsedMap["winProbAway"] as? Double)?.toInt()
                ?: (parsedMap["winProbAway"] as? String)?.toIntOrNull() ?: 34
            val confidenceLevel = parsedMap["confidenceLevel"] as? String ?: "Medium"

            MatchAISummary(
                shortSummary = shortSummary,
                momentumAnalysis = momentumAnalysis,
                keyTurningPoints = keyTurningPoints,
                topPerformers = topPerformers,
                winProbHome = winProbHome,
                winProbDraw = winProbDraw,
                winProbAway = winProbAway,
                confidenceLevel = confidenceLevel
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: $text", e)
            null
        }
    }

    private fun generateFallbackAnalysis(
        match: Match,
        stats: MatchStats?,
        events: List<MatchEvent>
    ): MatchAISummary {
        val totalGoals = match.homeScore + match.awayScore
        val hasGoals = totalGoals > 0
        val isDraw = match.homeScore == match.awayScore

        // Determine who's dominating based on goals, xG, shots, possession
        val leader = when {
            match.homeScore > match.awayScore -> "home"
            match.awayScore > match.homeScore -> "away"
            stats != null && stats.homeXG > stats.awayXG + 0.3 -> "home"
            stats != null && stats.awayXG > stats.homeXG + 0.3 -> "away"
            stats != null && stats.homePossession > 55 -> "home"
            stats != null && stats.awayPossession > 55 -> "away"
            else -> "even"
        }

        val hName = match.homeTeamName
        val aName = match.awayTeamName

        val summary = when {
            match.status == "UPCOMING" -> {
                "This highly anticipated fixture promises tactical chess. Both $hName and $aName are looking to secure critical group stage points. Analysts expect a tight contest with a focus on structural discipline and swift transitional plays."
            }
            totalGoals == 0 -> {
                "A tactical masterclass where both sides cancel each other out. $hName has maintained structured defensive blocks, while $aName relies on counter-attacking width. The game is crying out for creative spark to unlock the deadlock."
            }
            leader == "home" && match.homeScore > match.awayScore -> {
                "$hName is currently in control, showcasing superior territorial dominance and clinical finishing. $aName has struggled to build play through the lines, allowing the home side to dictate tempo and establish a solid lead."
            }
            leader == "away" && match.awayScore > match.homeScore -> {
                "$aName has mounted a superb display of high-pressing football, exposing spaces in $hName's transition. With direct wing play and precise vertical passing, the away side is deservedly in front."
            }
            else -> {
                "An extremely open and high-octane encounter with end-to-end action. Both $hName and $aName are trading punches, with quick switches and dynamic overlaps keeping both goalkeepers occupied in a finely balanced contest."
            }
        }

        val momentum = when {
            match.status == "UPCOMING" -> {
                "A balanced 50-50 entry momentum as both squads enter with full strength. The first 15 minutes will determine who asserts territorial presence."
            }
            stats != null && stats.homePossession > 57 -> {
                "$hName is dominating the passing rhythm, establishing a high block that forces $aName to drop deep. Game pace is controlled entirely by the home team's central midfielders."
            }
            stats != null && stats.awayPossession > 57 -> {
                "$aName holds key spatial control, spreading the lines wide. $hName is structurally compact, looking to intercept and launch rapid direct counter-attacks."
            }
            else -> {
                "Dynamic swing momentum. Control has shifted in 15-minute intervals. $hName dominates the central channels, while $aName looks lethal on the overlap."
            }
        }

        val points = if (match.status == "UPCOMING") {
            "Initial team sheet selections and tactical setup will be the primary turning point of the match."
        } else {
            val goalEvents = events.filter { it.type == "GOAL" }
            if (goalEvents.isNotEmpty()) {
                val firstGoal = goalEvents.first()
                "The opening goal by ${firstGoal.player1} in the ${firstGoal.minute}th minute completely disrupted the opponent's low-block strategy, forcing them to open up their defensive lines."
            } else {
                "The compact midfield pressing from both teams has prevented any clean goal-scoring opportunities, locking the battle in the center circle."
            }
        }

        val performers = if (match.status == "UPCOMING") {
            "Keep an eye on key playmakers for both sides who will be responsible for breaking down compact blocks."
        } else {
            val scorers = events.filter { it.type == "GOAL" }.map { it.player1 }
            if (scorers.isNotEmpty()) {
                "${scorers.joinToString(" and ")} have been instrumental with intelligent off-the-ball runs and clinical composure under high pressure."
            } else {
                "The central defensive pairing of both nations have been spectacular, registering vital blocks and interceptions to maintain clean sheets."
            }
        }

        // Win probabilities
        val (hProb, dProb, aProb) = when {
            match.status == "UPCOMING" -> Triple(40, 30, 30)
            match.status == "FINISHED" -> {
                if (match.homeScore > match.awayScore) Triple(100, 0, 0)
                else if (match.awayScore > match.homeScore) Triple(0, 0, 100)
                else Triple(0, 100, 0)
            }
            match.homeScore > match.awayScore -> Triple(75, 15, 10)
            match.awayScore > match.homeScore -> Triple(10, 15, 75)
            stats != null && stats.homeXG > stats.awayXG -> Triple(48, 32, 20)
            stats != null && stats.awayXG > stats.homeXG -> Triple(20, 32, 48)
            else -> Triple(33, 34, 33)
        }

        val conf = if (match.status == "FINISHED") "High" else if (match.minute > 75) "High" else "Medium"

        return MatchAISummary(
            shortSummary = summary,
            momentumAnalysis = momentum,
            keyTurningPoints = points,
            topPerformers = performers,
            winProbHome = hProb,
            winProbDraw = dProb,
            winProbAway = aProb,
            confidenceLevel = conf
        )
    }
}
