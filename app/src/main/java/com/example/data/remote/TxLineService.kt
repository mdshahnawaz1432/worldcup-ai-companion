package com.example.data.remote

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader

class TxLineApiException(
    val statusCode: Int?,
    val responseBody: String?,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// --- API Request / Response structures ---
data class GuestJwtResponse(
    val guestToken: String?,
    val accessToken: String?,
    val token: String?,
    val tokenType: String?,
    val expiresIn: Long?
)

data class TokenActivateRequest(
    val txSig: String,
    val walletSignature: String,
    val leagues: List<String>
)

data class TokenActivateResponse(
    val accessToken: String?,
    val apiToken: String?,
    val token: String?,
    val tokenType: String?,
    val expiresIn: Long?
)

object TxLineService {
    private const val TAG = "TxLineService"
    private var apiToken: String? = null
    private var guestJwt: String? = null
    private var isSubscribed = false
    private var appContext: android.content.Context? = null

    // Configuration
    var useSandbox = true
    var refreshIntervalSecs = 15

    // In-Memory Sandbox State
    private val teamsList = mutableListOf<Team>()
    private val matchesList = mutableListOf<Match>()
    private val matchStatsMap = mutableMapOf<String, MatchStats>()
    private val matchEventsMap = mutableMapOf<String, MutableList<MatchEvent>>()
    private val matchOddsMap = mutableMapOf<String, Odds>()

    // Network & JSON Parsing Clients
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Notification Trigger Callback
    var onGoalScored: ((match: Match, event: MatchEvent) -> Unit)? = null

    init {
        initializeSandboxData()
    }

    /**
     * Load securely stored API token and guest JWT on startup.
     */
    fun initToken(context: android.content.Context) {
        appContext = context.applicationContext
        val savedToken = SecureStorage.getApiToken(context)
        val savedGuestJwt = SecureStorage.getGuestJwt(context)
        if (!savedToken.isNullOrBlank() && !savedGuestJwt.isNullOrBlank()) {
            apiToken = savedToken
            guestJwt = savedGuestJwt
            useSandbox = false
            isSubscribed = true
            Log.d(TAG, "Successfully initialized active API credentials on startup. Token: $apiToken")
        } else {
            Log.d(TAG, "No API credentials found in secure storage. Defaulting to sandbox mode.")
            useSandbox = true
            isSubscribed = false
        }
    }

    /**
     * Set active API token, store securely, and enable production endpoints.
     */
    fun setApiToken(context: android.content.Context, token: String?) {
        apiToken = token
        if (token != null) {
            val gj = guestJwt ?: ""
            SecureStorage.saveCredentials(context, gj, token)
            useSandbox = false
            isSubscribed = true
            Log.d(TAG, "API credentials saved. Switched TxLINE API to PRODUCTION mode.")
        } else {
            SecureStorage.clearCredentials(context)
            guestJwt = null
            useSandbox = true
            isSubscribed = false
            Log.d(TAG, "API credentials cleared. Switched TxLINE API to SANDBOX mode.")
        }
    }

    fun clearApiToken(context: android.content.Context) {
        setApiToken(context, null)
    }

    /**
     * Official TxLINE Flow - Step 1: Request Guest JWT from POST /auth/guest/start
     */
    suspend fun startGuestSession(): String? {
        val url = "https://txline.txodds.com/api/v1/auth/guest/start"
        Log.d(TAG, "[STEP 1] Requesting Guest JWT from official endpoint: POST $url")
        return try {
            retryIO(3) {
                val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val emptyBody = "{}".toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(emptyBody)
                    .build()
                
                Log.d(TAG, "[STEP 1] Executing request. Target URL: $url")
                client.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "[STEP 1] Received response. HTTP status code: $statusCode, Response body length: ${body.length}")
                    
                    if (!response.isSuccessful) {
                        Log.e(TAG, "[STEP 1] Guest auth start failed. HTTP $statusCode, Body: $body")
                        throw TxLineApiException(
                            statusCode = statusCode,
                            responseBody = body,
                            message = "Guest auth start failed on POST /auth/guest/start"
                        )
                    }
                    
                    try {
                        val res = moshi.adapter(GuestJwtResponse::class.java).fromJson(body)
                        val token = res?.guestToken ?: res?.accessToken ?: res?.token
                        if (!token.isNullOrBlank()) {
                            Log.d(TAG, "[STEP 2] TxLINE Guest JWT successfully received: ${token.take(15)}...")
                            guestJwt = token
                            token
                        } else {
                            Log.e(TAG, "[STEP 1] No token fields found in the response JSON: $body")
                            throw TxLineApiException(
                                statusCode = statusCode,
                                responseBody = body,
                                message = "Moshi parsed response but found no token fields (guestToken, accessToken, or token)"
                            )
                        }
                    } catch (e: Exception) {
                        if (e is TxLineApiException) throw e
                        Log.e(TAG, "[STEP 1] JSON parsing failed for response: $body", e)
                        throw TxLineApiException(
                            statusCode = statusCode,
                            responseBody = body,
                            message = "JSON Parsing failed: ${e.localizedMessage ?: "Moshi JSON decoding error"}",
                            cause = e
                        )
                    }
                }
            }
        } catch (e: TxLineApiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[STEP 1] Network or IO failure during guest auth", e)
            throw TxLineApiException(
                statusCode = null,
                responseBody = null,
                message = "Network / IO Failure: ${e.localizedMessage ?: "Unknown error"}",
                cause = e
            )
        }
    }

    /**
     * Official TxLINE Flow - Step 4: Activate API token from POST /api/token/activate
     */
    suspend fun activateApiToken(
        guestToken: String,
        walletAddress: String,
        txSignature: String,
        messageSignature: String,
        leagues: List<String> = listOf("WC2026", "ENG1", "ESP1")
    ): String? {
        val url = "https://txline.txodds.com/api/v1/api/token/activate"
        Log.d(TAG, "[STEP 8] Requesting API Token activation from endpoint: POST $url")
        Log.d(TAG, "[STEP 9] Using Guest JWT in Authorization header: Bearer ${guestToken.take(12)}...")
        
        val payloadObj = TokenActivateRequest(
            txSig = txSignature,
            walletSignature = messageSignature,
            leagues = leagues
        )
        val payloadJson = moshi.adapter(TokenActivateRequest::class.java).toJson(payloadObj)
        Log.d(TAG, "[STEP 8] Outgoing Activation Payload: $payloadJson")

        return try {
            retryIO(3) {
                val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $guestToken")
                    .post(payloadJson.toRequestBody(jsonMediaType))
                    .build()

                Log.d(TAG, "[STEP 8] Executing activation request. Target URL: $url")
                client.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "[STEP 8] Received response. HTTP status code: $statusCode, Response body length: ${body.length}")

                    if (!response.isSuccessful) {
                        Log.e(TAG, "[STEP 8] Token activation failed. HTTP $statusCode, Body: $body")
                        throw TxLineApiException(
                            statusCode = statusCode,
                            responseBody = body,
                            message = "Token activation failed on POST /api/token/activate"
                        )
                    }

                    try {
                        val res = moshi.adapter(TokenActivateResponse::class.java).fromJson(body)
                        val token = res?.accessToken ?: res?.apiToken ?: res?.token
                        if (!token.isNullOrBlank()) {
                            Log.d(TAG, "[STEP 10] TxLINE API Token successfully activated: ${token.take(15)}...")
                            guestJwt = guestToken
                            apiToken = token
                            token
                        } else {
                            Log.e(TAG, "[STEP 8] No token fields found in response JSON: $body")
                            throw TxLineApiException(
                                statusCode = statusCode,
                                responseBody = body,
                                message = "Moshi parsed response but found no active token fields (accessToken, apiToken, or token)"
                            )
                        }
                    } catch (e: Exception) {
                        if (e is TxLineApiException) throw e
                        Log.e(TAG, "[STEP 8] JSON parsing failed for response: $body", e)
                        throw TxLineApiException(
                            statusCode = statusCode,
                            responseBody = body,
                            message = "JSON Parsing failed: ${e.localizedMessage ?: "Moshi JSON decoding error"}",
                            cause = e
                        )
                    }
                }
            }
        } catch (e: TxLineApiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[STEP 8] Network or IO failure during token activation", e)
            throw TxLineApiException(
                statusCode = null,
                responseBody = null,
                message = "Network / IO Failure: ${e.localizedMessage ?: "Unknown error"}",
                cause = e
            )
        }
    }

    fun isUserAuthenticated(): Boolean = apiToken != null && guestJwt != null

    fun getApiToken(): String? = apiToken

    fun getGuestJwt(): String? = guestJwt

    private fun checkResponseAndToken(response: okhttp3.Response) {
        if (response.code == 401) {
            appContext?.let { clearApiToken(it) }
            throw Exception("Unauthorized (401) - Token expired or invalid.")
        }
        if (!response.isSuccessful) {
            throw Exception("HTTP error code: ${response.code}")
        }
    }

    // Helper for robust retry-on-failure logic
    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "API call failed, retrying in $currentDelay ms...", e)
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block() // Last attempt
    }

    // --- Core API Methods ---

    suspend fun getTeams(): List<Team> {
        if (useSandbox || apiToken == null || guestJwt == null) {
            delay(300)
            return teamsList.toList()
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/teams")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    val listType = Types.newParameterizedType(List::class.java, Team::class.java)
                    val adapter = moshi.adapter<List<Team>>(listType)
                    adapter.fromJson(body) ?: throw Exception("Failed to parse teams JSON")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real teams. Falling back to sandbox.", e)
            teamsList.toList()
        }
    }

    suspend fun getTeamDetails(code: String): Team? {
        if (useSandbox || apiToken == null || guestJwt == null) {
            return teamsList.find { it.code == code }
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/teams/$code")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    moshi.adapter(Team::class.java).fromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real team details. Falling back to sandbox.", e)
            teamsList.find { it.code == code }
        }
    }

    suspend fun getMatches(): List<Match> {
        if (useSandbox || apiToken == null || guestJwt == null) {
            simulateLiveMatchTick()
            return matchesList.toList()
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/matches")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    val listType = Types.newParameterizedType(List::class.java, Match::class.java)
                    val adapter = moshi.adapter<List<Match>>(listType)
                    adapter.fromJson(body) ?: throw Exception("Failed to parse matches JSON")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real matches. Falling back to sandbox.", e)
            simulateLiveMatchTick()
            matchesList.toList()
        }
    }

    suspend fun getMatchDetails(id: String): Match? {
        if (useSandbox || apiToken == null || guestJwt == null) {
            simulateLiveMatchTick()
            return matchesList.find { it.id == id }
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/matches/$id")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    moshi.adapter(Match::class.java).fromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real match details. Falling back to sandbox.", e)
            simulateLiveMatchTick()
            matchesList.find { it.id == id }
        }
    }

    suspend fun getMatchStats(matchId: String): MatchStats? {
        if (useSandbox || apiToken == null || guestJwt == null) {
            return matchStatsMap[matchId]
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/matches/$matchId/stats")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    moshi.adapter(MatchStats::class.java).fromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real match stats. Falling back to sandbox.", e)
            matchStatsMap[matchId]
        }
    }

    suspend fun getMatchEvents(matchId: String): List<MatchEvent> {
        if (useSandbox || apiToken == null || guestJwt == null) {
            return matchEventsMap[matchId]?.toList() ?: emptyList()
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/matches/$matchId/events")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    val listType = Types.newParameterizedType(List::class.java, MatchEvent::class.java)
                    val adapter = moshi.adapter<List<MatchEvent>>(listType)
                    adapter.fromJson(body) ?: throw Exception("Failed to parse match events JSON")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real match events. Falling back to sandbox.", e)
            matchEventsMap[matchId]?.toList() ?: emptyList()
        }
    }

    suspend fun getMatchOdds(matchId: String): Odds? {
        if (useSandbox || apiToken == null || guestJwt == null) {
            return matchOddsMap[matchId]
        }
        return try {
            retryIO(3) {
                val request = Request.Builder()
                    .url("https://txline.txodds.com/api/v1/matches/$matchId/odds")
                    .header("Authorization", "Bearer $guestJwt")
                    .header("X-Api-Token", apiToken ?: "")
                    .build()
                client.newCall(request).execute().use { response ->
                    checkResponseAndToken(response)
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    moshi.adapter(Odds::class.java).fromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch real match odds. Falling back to sandbox.", e)
            matchOddsMap[matchId]
        }
    }

    // --- Dynamic Live Polling / SSE Flow ---
    fun getLiveMatchesFlow(): Flow<List<Match>> = flow {
        if (!useSandbox && apiToken != null && guestJwt != null) {
            // Attempt SSE Server-Sent Events stream for true real-time updates!
            val sseRequest = Request.Builder()
                .url("https://txline.txodds.com/api/v1/matches/stream")
                .header("Authorization", "Bearer $guestJwt")
                .header("X-Api-Token", apiToken ?: "")
                .build()
            try {
                client.newCall(sseRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Connected to TxLINE SSE Stream successfully.")
                        val reader = response.body?.charStream()?.let { BufferedReader(it) }
                        var line: String?
                        while (reader?.readLine().also { line = it } != null) {
                            if (line?.startsWith("data:") == true) {
                                val data = line!!.removePrefix("data:").trim()
                                val listType = Types.newParameterizedType(List::class.java, Match::class.java)
                                val adapter = moshi.adapter<List<Match>>(listType)
                                val list = adapter.fromJson(data)
                                if (list != null) {
                                    emit(list)
                                }
                            }
                        }
                    } else {
                        throw Exception("SSE stream closed with code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSE failed or not supported. Falling back to real-time REST polling.", e)
            }
        }

        // Falling back to robust REST polling / high-fidelity simulation loop
        while (true) {
            emit(getMatches())
            delay(refreshIntervalSecs * 1000L)
        }
    }

    fun getMatchDetailsFlow(matchId: String): Flow<MatchDetailContainer> = flow {
        while (true) {
            val match = getMatchDetails(matchId)
            if (match != null) {
                val stats = getMatchStats(matchId)
                val events = getMatchEvents(matchId)
                val odds = getMatchOdds(matchId)
                emit(MatchDetailContainer(match, stats, events, odds))
            }
            delay(refreshIntervalSecs * 1000L)
        }
    }

    // Data wrapper for Match Details screen
    data class MatchDetailContainer(
        val match: Match,
        val stats: MatchStats?,
        val events: List<MatchEvent>,
        val odds: Odds?
    )

    // --- Sandbox Initialization and Simulation ---

    private fun initializeSandboxData() {
        // 1. Teams
        val countries = listOf(
            Triple("USA", "United States", "Group A"),
            Triple("MEX", "Mexico", "Group A"),
            Triple("CAN", "Canada", "Group A"),
            Triple("PAN", "Panama", "Group A"),
            Triple("BRA", "Brazil", "Group B"),
            Triple("ARG", "Argentina", "Group B"),
            Triple("URU", "Uruguay", "Group B"),
            Triple("COL", "Colombia", "Group B"),
            Triple("FRA", "France", "Group C"),
            Triple("ESP", "Spain", "Group C"),
            Triple("ENG", "England", "Group C"),
            Triple("GER", "Germany", "Group C"),
            Triple("ITA", "Italy", "Group D"),
            Triple("JPN", "Japan", "Group D"),
            Triple("SEN", "Senegal", "Group D"),
            Triple("MAR", "Morocco", "Group D")
        )

        countries.forEach { (code, name, group) ->
            teamsList.add(
                Team(
                    code = code,
                    name = name,
                    group = group,
                    played = 5,
                    won = Random.nextInt(1, 4),
                    drawn = Random.nextInt(1, 2),
                    lost = Random.nextInt(0, 2),
                    points = 0, // calculated below
                    goalsFor = Random.nextInt(5, 12),
                    goalsAgainst = Random.nextInt(3, 8),
                    form = generateFormString()
                )
            )
        }

        // Calculate points
        for (i in teamsList.indices) {
            val t = teamsList[i]
            teamsList[i] = t.copy(points = t.won * 3 + t.drawn)
        }

        // Sort teams list by points initially
        teamsList.sortByDescending { it.points }

        // 2. Matches
        val venues = listOf("MetLife Stadium, NY", "SoFi Stadium, LA", "Azteca, Mexico City", "BC Place, Vancouver")
        val today = Date()
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Create some matches
        // Match 1: LIVE
        matchesList.add(
            Match(
                id = "m1",
                homeTeamName = "Brazil",
                homeTeamCode = "BRA",
                awayTeamName = "Argentina",
                awayTeamCode = "ARG",
                homeScore = 2,
                awayScore = 1,
                status = "LIVE",
                minute = 62,
                competition = "FIFA World Cup - Group B",
                startTime = format.format(today),
                group = "Group B",
                venue = venues[0]
            )
        )

        // Match 2: LIVE
        matchesList.add(
            Match(
                id = "m2",
                homeTeamName = "France",
                homeTeamCode = "FRA",
                awayTeamName = "Germany",
                awayTeamCode = "GER",
                homeScore = 0,
                awayScore = 0,
                status = "LIVE",
                minute = 24,
                competition = "FIFA World Cup - Group C",
                startTime = format.format(today),
                group = "Group C",
                venue = venues[1]
            )
        )

        // Match 3: UPCOMING (Today)
        val calendar = Calendar.getInstance()
        calendar.time = today
        calendar.add(Calendar.HOUR, 2)
        matchesList.add(
            Match(
                id = "m3",
                homeTeamName = "United States",
                homeTeamCode = "USA",
                awayTeamName = "Mexico",
                awayTeamCode = "MEX",
                homeScore = 0,
                awayScore = 0,
                status = "UPCOMING",
                minute = 0,
                competition = "FIFA World Cup - Group A",
                startTime = format.format(calendar.time),
                group = "Group A",
                venue = venues[2]
            )
        )

        // Match 4: UPCOMING (Tomorrow)
        calendar.time = today
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        matchesList.add(
            Match(
                id = "m4",
                homeTeamName = "England",
                homeTeamCode = "ENG",
                awayTeamName = "Spain",
                awayTeamCode = "ESP",
                homeScore = 0,
                awayScore = 0,
                status = "UPCOMING",
                minute = 0,
                competition = "FIFA World Cup - Group C",
                startTime = format.format(calendar.time),
                group = "Group C",
                venue = venues[3]
            )
        )

        // Match 5: FINISHED
        calendar.time = today
        calendar.add(Calendar.HOUR, -4)
        matchesList.add(
            Match(
                id = "m5",
                homeTeamName = "Japan",
                homeTeamCode = "JPN",
                awayTeamName = "Senegal",
                awayTeamCode = "SEN",
                homeScore = 3,
                awayScore = 2,
                status = "FINISHED",
                minute = 90,
                competition = "FIFA World Cup - Group D",
                startTime = format.format(calendar.time),
                group = "Group D",
                venue = venues[0]
            )
        )

        // Match 6: FINISHED
        calendar.time = today
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        matchesList.add(
            Match(
                id = "m6",
                homeTeamName = "Italy",
                homeTeamCode = "ITA",
                awayTeamName = "Morocco",
                awayTeamCode = "MAR",
                homeScore = 1,
                awayScore = 0,
                status = "FINISHED",
                minute = 90,
                competition = "FIFA World Cup - Group D",
                startTime = format.format(calendar.time),
                group = "Group D",
                venue = venues[1]
            )
        )

        // Initialize Stats
        matchStatsMap["m1"] = MatchStats(
            matchId = "m1",
            homePossession = 54, awayPossession = 46,
            homeShots = 11, awayShots = 8,
            homeShotsOnTarget = 5, awayShotsOnTarget = 3,
            homeCorners = 4, awayCorners = 3,
            homeFouls = 12, awayFouls = 14,
            homeYellowCards = 1, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homeXG = 1.64, awayXG = 1.12
        )

        matchStatsMap["m2"] = MatchStats(
            matchId = "m2",
            homePossession = 42, awayPossession = 58,
            homeShots = 3, awayShots = 6,
            homeShotsOnTarget = 1, awayShotsOnTarget = 2,
            homeCorners = 1, awayCorners = 4,
            homeFouls = 6, awayFouls = 8,
            homeYellowCards = 0, awayYellowCards = 1,
            homeRedCards = 0, awayRedCards = 0,
            homeXG = 0.35, awayXG = 0.68
        )

        // Initialize Events
        matchEventsMap["m1"] = mutableListOf(
            MatchEvent("e1", "m1", "GOAL", 12, "away", "L. Messi", "E. Fernandez", "Left foot shot"),
            MatchEvent("e2", "m1", "GOAL", 34, "home", "Vinicius Jr.", "Neymar Jr", "Header"),
            MatchEvent("e3", "m1", "YELLOW_CARD", 42, "away", "R. De Paul", null, "Rough tackle"),
            MatchEvent("e4", "m1", "GOAL", 58, "home", "Rodrygo", "Casemiro", "Volley"),
            MatchEvent("e5", "m1", "YELLOW_CARD", 61, "home", "Marquinhos", null, "Handball")
        )

        matchEventsMap["m2"] = mutableListOf(
            MatchEvent("e6", "m2", "YELLOW_CARD", 15, "away", "J. Kimmich", null, "Tactical foul")
        )

        matchEventsMap["m5"] = mutableListOf(
            MatchEvent("e7", "m5", "GOAL", 18, "home", "K. Mitoma", "W. Endo", "Tap-in"),
            MatchEvent("e8", "m5", "GOAL", 29, "away", "N. Jackson", "I. Sarr", "Counter-attack"),
            MatchEvent("e9", "m5", "GOAL", 44, "home", "R. Doan", null, "Solo run"),
            MatchEvent("e10", "m5", "GOAL", 73, "away", "S. Mane", "K. Koulibaly", "Header"),
            MatchEvent("e11", "m5", "GOAL", 88, "home", "A. Ueda", "K. Mitoma", "Late winner")
        )

        matchEventsMap["m6"] = mutableListOf(
            MatchEvent("e12", "m6", "GOAL", 65, "home", "F. Chiesa", "N. Barella", "Long-range strike"),
            MatchEvent("e13", "m6", "YELLOW_CARD", 81, "away", "A. Hakimi", null, "Dissent")
        )

        // Initialize Odds
        matchOddsMap["m1"] = Odds("m1", 2.25, 3.10, 3.40)
        matchOddsMap["m2"] = Odds("m2", 1.95, 3.40, 4.00)
        matchOddsMap["m3"] = Odds("m3", 2.10, 3.20, 3.65)
        matchOddsMap["m4"] = Odds("m4", 2.50, 3.00, 3.00)
    }

    private fun generateFormString(): String {
        val results = listOf("W", "D", "L")
        return List(5) { results.random() }.joinToString(",")
    }

    /**
     * Ticks the sandbox simulation: updates minute, occasionally scores a goal,
     * updates team stats, adds yellow cards, etc.
     */
    private fun simulateLiveMatchTick() {
        matchesList.forEachIndexed { index, match ->
            if (match.status == "LIVE") {
                val nextMinute = match.minute + 1
                if (nextMinute > 90) {
                    // Match finished
                    matchesList[index] = match.copy(status = "FINISHED", minute = 90)
                    Log.d(TAG, "Match ${match.id} finished.")
                    return@forEachIndexed
                }

                // Increment minute
                var updatedMatch = match.copy(minute = nextMinute)

                // Retrieve stats
                val stats = matchStatsMap[match.id]
                var updatedStats = stats

                // Occasional live action simulation (Random probability)
                val chance = Random.nextDouble()
                if (chance < 0.15 && stats != null) {
                    // Add shot
                    val isHome = Random.nextBoolean()
                    val onTarget = Random.nextDouble() < 0.4
                    val goalScored = onTarget && (Random.nextDouble() < 0.35)

                    val newHomeShots = stats.homeShots + (if (isHome) 1 else 0)
                    val newAwayShots = stats.awayShots + (if (!isHome) 1 else 0)
                    val newHomeShotsOnTarget = stats.homeShotsOnTarget + (if (isHome && onTarget) 1 else 0)
                    val newAwayShotsOnTarget = stats.awayShotsOnTarget + (if (!isHome && onTarget) 1 else 0)
                    val newHomeXG = stats.homeXG + (if (isHome) Random.nextDouble(0.05, 0.25) else 0.0)
                    val newAwayXG = stats.awayXG + (if (!isHome) Random.nextDouble(0.05, 0.25) else 0.0)

                    updatedStats = stats.copy(
                        homeShots = newHomeShots,
                        awayShots = newAwayShots,
                        homeShotsOnTarget = newHomeShotsOnTarget,
                        awayShotsOnTarget = newAwayShotsOnTarget,
                        homeXG = String.format(Locale.US, "%.2f", newHomeXG).toDouble(),
                        awayXG = String.format(Locale.US, "%.2f", newAwayXG).toDouble()
                    )

                    if (goalScored) {
                        val scorer = if (isHome) {
                            listOf("Neymar Jr", "Vinicius Jr.", "Rodrygo", "Richarlison", "Raphinha").random()
                        } else {
                            listOf("L. Messi", "L. Martinez", "J. Alvarez", "A. Di Maria", "E. Fernandez").random()
                        }

                        val assist = if (isHome) {
                            listOf("Neymar Jr", "Casemiro", "Bruno Guimaraes", null).random()
                        } else {
                            listOf("L. Messi", "R. De Paul", "E. Fernandez", null).random()
                        }

                        val details = listOf("Header", "Left foot shot", "Right foot shot", "Free kick", "Penalty").random()

                        val newScoreHome = match.homeScore + (if (isHome) 1 else 0)
                        val newScoreAway = match.awayScore + (if (!isHome) 1 else 0)
                        updatedMatch = updatedMatch.copy(homeScore = newScoreHome, awayScore = newScoreAway)

                        val newEvent = MatchEvent(
                            id = "e_" + UUID.randomUUID().toString().take(4),
                            matchId = match.id,
                            type = "GOAL",
                            minute = nextMinute,
                            team = if (isHome) "home" else "away",
                            player1 = scorer,
                            player2 = assist,
                            detail = details
                        )

                        matchEventsMap[match.id]?.add(newEvent)
                        onGoalScored?.invoke(updatedMatch, newEvent)
                        Log.d(TAG, "GOAL! ${updatedMatch.homeTeamName} $newScoreHome - $newScoreAway ${updatedMatch.awayTeamName} ($scorer $nextMinute')")
                    }
                } else if (chance < 0.25 && stats != null) {
                    // Update possession slightly
                    val shift = Random.nextInt(-3, 4)
                    val newHomePoss = (stats.homePossession + shift).coerceIn(30, 70)
                    updatedStats = stats.copy(
                        homePossession = newHomePoss,
                        awayPossession = 100 - newHomePoss
                    )
                }

                // Apply updates
                matchesList[index] = updatedMatch
                if (updatedStats != null) {
                    matchStatsMap[match.id] = updatedStats
                }
            }
        }
    }
}
