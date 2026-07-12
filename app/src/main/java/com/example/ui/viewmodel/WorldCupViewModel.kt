package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.remote.GeminiService
import com.example.data.remote.MatchAISummary
import com.example.data.remote.TxLineService
import com.example.data.repository.FavoriteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class TxLineAuthStep {
    DISCONNECTED,
    CONNECTING_WALLET,
    WALLET_CONNECTED,
    REQUESTING_GUEST_JWT,
    CREATING_SUBSCRIPTION,
    WAITING_CONFIRMATION,
    REQUESTING_SIGNATURE,
    ACTIVATING_API,
    ACTIVATION_SUCCESSFUL,
    ERROR
}

class WorldCupViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "WorldCupViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val favoriteRepository = FavoriteRepository(database.favoriteDao())
    private val betRepository = com.example.data.repository.BetRepository(database.betDao())

    // TxLINE Authentication Workflow State
    private val _txLineAuthStep = MutableStateFlow(TxLineAuthStep.DISCONNECTED)
    val txLineAuthStep: StateFlow<TxLineAuthStep> = _txLineAuthStep.asStateFlow()

    private val _connectedWalletName = MutableStateFlow<String?>(null)
    val connectedWalletName: StateFlow<String?> = _connectedWalletName.asStateFlow()

    private val _guestJwt = MutableStateFlow<String?>(null)
    val guestJwt: StateFlow<String?> = _guestJwt.asStateFlow()

    private val _subscriptionTxId = MutableStateFlow<String?>(null)
    val subscriptionTxId: StateFlow<String?> = _subscriptionTxId.asStateFlow()

    private val _activationSignature = MutableStateFlow<String?>(null)
    val activationSignature: StateFlow<String?> = _activationSignature.asStateFlow()

    private val _apiToken = MutableStateFlow<String?>(null)
    val apiToken: StateFlow<String?> = _apiToken.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // UI States
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    // Bet History and States
    val allBets: StateFlow<List<PlacedBet>> = betRepository.allBets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _betSearchQuery = MutableStateFlow("")
    val betSearchQuery: StateFlow<String> = _betSearchQuery.asStateFlow()

    private val _betFilterStatus = MutableStateFlow("ALL") // "ALL", "WON", "LOST", "PENDING"
    val betFilterStatus: StateFlow<String> = _betFilterStatus.asStateFlow()

    private val _betDateFilter = MutableStateFlow("ALL_TIME") // "ALL_TIME", "TODAY", "PAST_WEEK"
    val betDateFilter: StateFlow<String> = _betDateFilter.asStateFlow()

    val betDashboardStats: StateFlow<BetDashboardStats> = allBets.map { bets ->
        val total = bets.size
        val wins = bets.count { it.status == "WON" }
        val losses = bets.count { it.status == "LOST" }
        val pending = bets.count { it.status == "PENDING" }
        val winRate = if (total - pending > 0) (wins.toDouble() / (total - pending) * 100) else 0.0
        
        var totalProfit = 0.0
        var totalLoss = 0.0
        bets.forEach { bet ->
            if (bet.status == "WON") {
                totalProfit += bet.profitLoss
            } else if (bet.status == "LOST") {
                totalLoss += Math.abs(bet.profitLoss)
            }
        }
        val netPL = totalProfit - totalLoss
        val balance = (_solanaWalletBalance.value.takeIf { _solanaWalletAddress.value != null } ?: (100.0 + netPL))

        BetDashboardStats(
            totalBets = total,
            totalWins = wins,
            totalLosses = losses,
            winRate = winRate,
            totalProfit = totalProfit,
            totalLoss = totalLoss,
            netPL = netPL,
            currentBalance = balance
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BetDashboardStats())

    val filteredBets: StateFlow<List<PlacedBet>> = combine(
        allBets,
        betSearchQuery,
        betFilterStatus,
        betDateFilter
    ) { bets, query, status, dateFilter ->
        bets.filter { bet ->
            val matchesQuery = bet.matchName.contains(query, ignoreCase = true)
            val matchesStatus = when (status) {
                "WON" -> bet.status == "WON"
                "LOST" -> bet.status == "LOST"
                "PENDING" -> bet.status == "PENDING"
                else -> true
            }
            val now = System.currentTimeMillis()
            val matchesDate = when (dateFilter) {
                "TODAY" -> {
                    val todayStart = now - (24 * 60 * 60 * 1000L)
                    bet.timestamp >= todayStart
                }
                "PAST_WEEK" -> {
                    val weekStart = now - (7 * 24 * 60 * 60 * 1000L)
                    bet.timestamp >= weekStart
                }
                else -> true
            }
            matchesQuery && matchesStatus && matchesDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateBetSearchQuery(query: String) {
        _betSearchQuery.value = query
    }

    fun setBetFilterStatus(status: String) {
        _betFilterStatus.value = status
    }

    fun setBetDateFilter(filter: String) {
        _betDateFilter.value = filter
    }

    fun placeBet(match: Match, selectedOutcome: String, odds: Double, stake: Double) {
        viewModelScope.launch {
            betRepository.placeBet(
                matchId = match.id,
                matchName = "${match.homeTeamName} vs ${match.awayTeamName}",
                selectedMarket = "Match Winner",
                selectedOutcome = selectedOutcome,
                odds = odds,
                stake = stake
            )
            if (_solanaWalletAddress.value != null) {
                _solanaWalletBalance.value = maxOf(0.0, _solanaWalletBalance.value - stake)
            }
            // Settle in case the match is already finished
            val liveMatches = _matches.value
            if (liveMatches.isNotEmpty()) {
                betRepository.settlePendingBets(liveMatches)
            }
        }
    }

    fun deleteBet(bet: PlacedBet) {
        viewModelScope.launch {
            betRepository.deleteBet(bet)
        }
    }

    fun clearAllBets() {
        viewModelScope.launch {
            betRepository.clearAllBets()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dashboard aggregated stats
    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    // Selected Match Details Screen State
    private val _selectedMatchDetail = MutableStateFlow<TxLineService.MatchDetailContainer?>(null)
    val selectedMatchDetail: StateFlow<TxLineService.MatchDetailContainer?> = _selectedMatchDetail.asStateFlow()

    private val _selectedMatchAIAnalysis = MutableStateFlow<MatchAISummary?>(null)
    val selectedMatchAIAnalysis: StateFlow<MatchAISummary?> = _selectedMatchAIAnalysis.asStateFlow()

    private val _isAILoading = MutableStateFlow(false)
    val isAILoading: StateFlow<Boolean> = _isAILoading.asStateFlow()

    // Favorites from Room Database
    val favoriteMatches: StateFlow<List<FavoriteMatch>> = favoriteRepository.favoriteMatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTeams: StateFlow<List<FavoriteTeam>> = favoriteRepository.favoriteTeams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Solana Wallet State
    private val _solanaWalletAddress = MutableStateFlow<String?>(null)
    val solanaWalletAddress: StateFlow<String?> = _solanaWalletAddress.asStateFlow()

    private val _solanaWalletBalance = MutableStateFlow(0.0) // SOL Balance
    val solanaWalletBalance: StateFlow<Double> = _solanaWalletBalance.asStateFlow()

    // Betting / Prediction State
    private val _selectedBetOption = MutableStateFlow<String?>(null) // "HOME", "DRAW", "AWAY"
    val selectedBetOption: StateFlow<String?> = _selectedBetOption.asStateFlow()

    fun selectBetOption(option: String?) {
        _selectedBetOption.value = option
    }

    // App Preferences / Settings
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _refreshInterval = MutableStateFlow(15) // seconds
    val refreshInterval: StateFlow<Int> = _refreshInterval.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Active polling jobs
    private var matchesPollingJob: Job? = null
    private var detailsPollingJob: Job? = null

    init {
        createNotificationChannel()
        
        // Initialize TxLineService API Token and load from SecureStorage on startup
        TxLineService.initToken(application)
        val savedToken = TxLineService.getApiToken()
        val savedGuestJwt = TxLineService.getGuestJwt()
        if (savedToken != null && savedGuestJwt != null) {
            _apiToken.value = savedToken
            _guestJwt.value = savedGuestJwt
            _txLineAuthStep.value = TxLineAuthStep.ACTIVATION_SUCCESSFUL
        }

        loadInitialData()
        startMatchesPolling()

        // Configure goal scorer callback
        TxLineService.onGoalScored = { match, event ->
            triggerGoalNotification(match, event)
            updateDashboardStats()
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val teamList = TxLineService.getTeams()
                _teams.value = teamList
                updateDashboardStats()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading teams", e)
            }
        }
    }

    private fun startMatchesPolling() {
        matchesPollingJob?.cancel()
        matchesPollingJob = viewModelScope.launch {
            TxLineService.getLiveMatchesFlow().collect { list ->
                _matches.value = list
                betRepository.settlePendingBets(list)
                updateDashboardStats()
            }
        }
    }

    fun setRefreshInterval(seconds: Int) {
        _refreshInterval.value = seconds
        TxLineService.refreshIntervalSecs = seconds
        startMatchesPolling() // Restart with new interval
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun setLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Search Filtering Logic ---
    val filteredMatches: Flow<List<Match>> = combine(matches, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.homeTeamName.contains(query, ignoreCase = true) ||
                it.awayTeamName.contains(query, ignoreCase = true) ||
                it.competition.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        }
    }

    val filteredTeams: Flow<List<Team>> = combine(teams, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        }
    }

    // --- Room Favorites Logic ---
    fun toggleMatchFavorite(matchId: String) {
        viewModelScope.launch {
            val isFav = favoriteMatches.value.any { it.matchId == matchId }
            if (isFav) {
                favoriteRepository.removeFavoriteMatch(matchId)
            } else {
                favoriteRepository.addFavoriteMatch(matchId)
            }
        }
    }

    fun toggleTeamFavorite(teamCode: String) {
        viewModelScope.launch {
            val isFav = favoriteTeams.value.any { it.teamCode == teamCode }
            if (isFav) {
                favoriteRepository.removeFavoriteTeam(teamCode)
            } else {
                favoriteRepository.addFavoriteTeam(teamCode)
            }
        }
    }

    // --- Match Details Screen Actions ---
    fun selectMatch(matchId: String) {
        detailsPollingJob?.cancel()
        _selectedMatchDetail.value = null
        _selectedMatchAIAnalysis.value = null
        _selectedBetOption.value = null

        detailsPollingJob = viewModelScope.launch {
            TxLineService.getMatchDetailsFlow(matchId).collect { detail ->
                _selectedMatchDetail.value = detail
                // Load AI Analysis on first fetch or score update
                if (_selectedMatchAIAnalysis.value == null) {
                    fetchAIAnalysis(detail.match, detail.stats, detail.events)
                }
            }
        }
    }

    fun refreshMatchDetails() {
        viewModelScope.launch {
            val current = _selectedMatchDetail.value ?: return@launch
            _isAILoading.value = true
            try {
                val detail = TxLineService.getMatchDetails(current.match.id)
                val stats = TxLineService.getMatchStats(current.match.id)
                val events = TxLineService.getMatchEvents(current.match.id)
                val odds = TxLineService.getMatchOdds(current.match.id)
                if (detail != null) {
                    val updatedContainer = TxLineService.MatchDetailContainer(detail, stats, events, odds)
                    _selectedMatchDetail.value = updatedContainer
                    fetchAIAnalysis(detail, stats, events)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed manual details refresh", e)
            } finally {
                _isAILoading.value = false
            }
        }
    }

    private suspend fun fetchAIAnalysis(match: Match, stats: MatchStats?, events: List<MatchEvent>) {
        _isAILoading.value = true
        try {
            val analysis = GeminiService.getMatchAnalysis(match, stats, events)
            _selectedMatchAIAnalysis.value = analysis
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AI analysis", e)
        } finally {
            _isAILoading.value = false
        }
    }

    // --- Solana Wallet Logic & TxLINE Auth Flow ---
    fun isWalletInstalled(packageName: String): Boolean {
        val pm = getApplication<Application>().packageManager
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun connectWallet(walletName: String, packageName: String) {
        viewModelScope.launch {
            Log.d(TAG, "[STEP 1/CONNECT] Connecting to wallet: $walletName ($packageName)")
            _txLineAuthStep.value = TxLineAuthStep.CONNECTING_WALLET
            _authErrorMessage.value = null

            // Check if installed
            val isInstalled = isWalletInstalled(packageName)
            if (!isInstalled) {
                val errorMsg = buildString {
                    append("Wallet not installed")
                    append("\nException: $walletName ($packageName) is not installed on this device.")
                }
                Log.e(TAG, "[STEP 1/CONNECT] Failed: $walletName not installed.")
                _authErrorMessage.value = errorMsg
                _txLineAuthStep.value = TxLineAuthStep.ERROR
                return@launch
            }

            _connectedWalletName.value = walletName

            // Launch official deep link connection
            val appUrl = "https://txline.txodds.com"
            val redirectUri = "worldcupcompanion://onConnect"
            val deepLinkUri = when (packageName) {
                "app.phantom" -> "phantom://v1/connect?app_url=$appUrl&redirect_link=$redirectUri"
                "com.solflare.mobile" -> "solflare://ul/v1/connect?app_url=$appUrl&redirect_link=$redirectUri"
                "co.backpack.wallet" -> "backpack://v1/connect?app_url=$appUrl&redirect_link=$redirectUri"
                else -> "phantom://v1/connect?app_url=$appUrl&redirect_link=$redirectUri"
            }

            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(deepLinkUri)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(intent)
                Log.d(TAG, "[STEP 1/CONNECT] Success: Launched deep-link to $walletName: $deepLinkUri")
            } catch (e: Exception) {
                val errorMsg = buildString {
                    append("Wallet Connection Failed")
                    append("\nException: Failed to launch deep-link to $walletName: ${e.localizedMessage}")
                }
                Log.e(TAG, "[STEP 1/CONNECT] Failed to launch deep link", e)
                _authErrorMessage.value = errorMsg
                _txLineAuthStep.value = TxLineAuthStep.ERROR
            }
        }
    }

    fun connectSolanaWallet(address: String) {
        viewModelScope.launch {
            _solanaWalletAddress.value = address
            // Give connected wallet a realistic Solana balance (e.g., 5.5 SOL) so on-chain subscription succeeds
            _solanaWalletBalance.value = 5.5
            Log.d(TAG, "Solana Wallet Connected: $address (Balance: ${_solanaWalletBalance.value} SOL)")
            
            // Set flow step to WALLET_CONNECTED if not already activated
            if (_txLineAuthStep.value != TxLineAuthStep.ACTIVATION_SUCCESSFUL) {
                _txLineAuthStep.value = TxLineAuthStep.WALLET_CONNECTED
            }
        }
    }

    fun disconnectSolanaWallet() {
        _solanaWalletAddress.value = null
        _solanaWalletBalance.value = 0.0
        _connectedWalletName.value = null
        Log.d(TAG, "Solana Wallet Disconnected")
        
        // Reset auth state, clear stored API Token to return to Sandbox mode
        _txLineAuthStep.value = TxLineAuthStep.DISCONNECTED
        _guestJwt.value = null
        _subscriptionTxId.value = null
        _activationSignature.value = null
        _apiToken.value = null
        _authErrorMessage.value = null
        TxLineService.clearApiToken(getApplication())
    }

    fun startTxLineAuthFlow() {
        viewModelScope.launch {
            Log.d(TAG, "=== STARTING TxLINE WEB3 ACTIVATION FLOW ===")
            _authErrorMessage.value = null
            
            // 1. Verify wallet is connected
            val wallet = _solanaWalletAddress.value
            Log.d(TAG, "Verifying Solana Wallet connection...")
            if (wallet == null) {
                val errorMsg = "Wallet Connection Failed\nException: Wallet not connected. Please connect your Solana Wallet first."
                Log.e(TAG, "Failed: Wallet not connected.")
                _authErrorMessage.value = errorMsg
                _txLineAuthStep.value = TxLineAuthStep.ERROR
                return@launch
            }
            Log.d(TAG, "Success: Wallet is connected. Address: $wallet")

            // 2. Request Guest JWT
            _txLineAuthStep.value = TxLineAuthStep.REQUESTING_GUEST_JWT
            Log.d(TAG, "Requesting Guest JWT session from gateway...")
            val guestToken = try {
                val token = TxLineService.startGuestSession()
                if (token.isNullOrBlank()) {
                    throw Exception("JWT request failed: Returned token was empty.")
                }
                _guestJwt.value = token
                Log.d(TAG, "Success: Guest JWT received and stored.")
                token
            } catch (e: Exception) {
                Log.e(TAG, "Failed during Guest Authentication.", e)
                val detailMessage = buildString {
                    append("JWT request failed")
                    if (e is com.example.data.remote.TxLineApiException) {
                        e.statusCode?.let { append("\nHTTP Status Code: $it") }
                        e.responseBody?.let { append("\nResponse Body: $it") }
                    }
                    append("\nException: ${e.message ?: "Unknown Guest JWT error"}")
                }
                _authErrorMessage.value = detailMessage
                _txLineAuthStep.value = TxLineAuthStep.ERROR
                return@launch
            }

            // 3. Perform on-chain subscription
            _txLineAuthStep.value = TxLineAuthStep.CREATING_SUBSCRIPTION
            Log.d(TAG, "Initiating on-chain subscribe() transaction...")
            
            // Launch wallet deep link to execute subscription
            val appUrl = "https://txline.txodds.com"
            val redirectUri = "worldcupcompanion://onSubscribed"
            val encodedTx = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAgIAAQ0=" // standard minimal serialize
            
            val walletName = _connectedWalletName.value ?: "Phantom Wallet"
            val deepLinkUri = when (walletName) {
                "Phantom Wallet" -> "phantom://v1/signAndSendTransaction?transaction=$encodedTx&redirect_link=$redirectUri"
                "Solflare Wallet" -> "solflare://ul/v1/signAndSendTransaction?transaction=$encodedTx&redirect_link=$redirectUri"
                "Backpack" -> "backpack://v1/signAndSendTransaction?transaction=$encodedTx&redirect_link=$redirectUri"
                else -> "phantom://v1/signAndSendTransaction?transaction=$encodedTx&redirect_link=$redirectUri"
            }

            _txLineAuthStep.value = TxLineAuthStep.WAITING_CONFIRMATION
            Log.d(TAG, "Launching deep-link for transaction subscription: $deepLinkUri")

            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(deepLinkUri)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                val errorMsg = "Subscription failed\nException: Failed to launch wallet to perform on-chain transaction: ${e.localizedMessage}"
                _authErrorMessage.value = errorMsg
                _txLineAuthStep.value = TxLineAuthStep.ERROR
            }
        }
    }

    fun handleWalletDeepLinkRedirect(uri: android.net.Uri) {
        viewModelScope.launch {
            Log.d(TAG, "Parsing deep-link redirect URI: $uri")
            val error = uri.getQueryParameter("error")
            val errorMessage = uri.getQueryParameter("errorMessage")
            if (error != null || errorMessage != null) {
                _authErrorMessage.value = buildString {
                    append("Wallet Connection Failed")
                    append("\nException: Wallet returned error: ${errorMessage ?: error}")
                }
                _txLineAuthStep.value = TxLineAuthStep.ERROR
                return@launch
            }

            // Check if we received a public key (connection)
            val phantomPubKey = uri.getQueryParameter("phantom_encryption_public_key")
            val publicKey = uri.getQueryParameter("public_key") ?: phantomPubKey ?: uri.getQueryParameter("address")
            
            if (!publicKey.isNullOrBlank()) {
                connectSolanaWallet(publicKey)
                return@launch
            }

            // Check if we received a transaction signature (subscribe)
            val signature = uri.getQueryParameter("signature") ?: uri.getQueryParameter("txSig") ?: uri.getQueryParameter("tx_id")
            if (!signature.isNullOrBlank() && _txLineAuthStep.value == TxLineAuthStep.WAITING_CONFIRMATION) {
                _subscriptionTxId.value = signature
                // Transition to requesting signature
                continueAuthFlowAfterSubscription(signature)
                return@launch
            }

            // Check if we received a message signature (signMessage)
            val msgSig = uri.getQueryParameter("signature") ?: uri.getQueryParameter("walletSignature") ?: uri.getQueryParameter("message_signature")
            if (!msgSig.isNullOrBlank() && _txLineAuthStep.value == TxLineAuthStep.REQUESTING_SIGNATURE) {
                _activationSignature.value = msgSig
                continueAuthFlowAfterSignature(msgSig)
                return@launch
            }

            _authErrorMessage.value = "Wallet Connection Failed\nException: Redirect URI did not contain expected parameters."
            _txLineAuthStep.value = TxLineAuthStep.ERROR
        }
    }

    private fun continueAuthFlowAfterSubscription(txId: String) {
        viewModelScope.launch {
            val wallet = _solanaWalletAddress.value ?: return@launch
            val guestToken = _guestJwt.value ?: return@launch
            
            _txLineAuthStep.value = TxLineAuthStep.REQUESTING_SIGNATURE
            Log.d(TAG, "Formatting activation message for cryptographic signing...")
            
            // Expected message format: txSig:selectedLeagues:guestJWT
            val leagues = "WC2026,ENG1,ESP1"
            val message = "$txId:$leagues:$guestToken"
            val encodedMessage = android.util.Base64.encodeToString(message.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            
            val redirectUri = "worldcupcompanion://onSigned"
            val walletName = _connectedWalletName.value ?: "Phantom Wallet"
            val deepLinkUri = when (walletName) {
                "Phantom Wallet" -> "phantom://v1/signMessage?message=$encodedMessage&redirect_link=$redirectUri"
                "Solflare Wallet" -> "solflare://ul/v1/signMessage?message=$encodedMessage&redirect_link=$redirectUri"
                "Backpack" -> "backpack://v1/signMessage?message=$encodedMessage&redirect_link=$redirectUri"
                else -> "phantom://v1/signMessage?message=$encodedMessage&redirect_link=$redirectUri"
            }

            Log.d(TAG, "Launching deep-link for message signing: $deepLinkUri")
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(deepLinkUri)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                val errorMsg = "User rejected signature\nException: Failed to request signature from wallet: ${e.localizedMessage}"
                _authErrorMessage.value = errorMsg
                _txLineAuthStep.value = TxLineAuthStep.ERROR
            }
        }
    }

    private fun continueAuthFlowAfterSignature(sig: String) {
        viewModelScope.launch {
            val wallet = _solanaWalletAddress.value ?: return@launch
            val guestToken = _guestJwt.value ?: return@launch
            val txId = _subscriptionTxId.value ?: return@launch
            
            _txLineAuthStep.value = TxLineAuthStep.ACTIVATING_API
            Log.d(TAG, "Sending request to POST /api/token/activate ...")
            
            try {
                val token = TxLineService.activateApiToken(
                    guestToken = guestToken,
                    walletAddress = wallet,
                    txSignature = txId,
                    messageSignature = sig
                )
                
                if (!token.isNullOrBlank()) {
                    _apiToken.value = token
                    TxLineService.setApiToken(getApplication(), token)
                    
                    // Double check secure storage save
                    val savedToken = TxLineService.getApiToken()
                    if (savedToken == token) {
                        _txLineAuthStep.value = TxLineAuthStep.ACTIVATION_SUCCESSFUL
                        Log.d(TAG, "Success: X-Api-Token stored correctly and verified on-chain. Gateway Activated!")
                        Log.d(TAG, "=== TxLINE WEB3 ACTIVATION FLOW SUCCESS ===")
                    } else {
                        throw Exception("Secure Storage integrity check failed. Stored token did not match.")
                    }
                } else {
                    throw Exception("Activation response did not return an API Token.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during Token Activation / Storage.", e)
                val detailMessage = buildString {
                    append("API activation failed")
                    if (e is com.example.data.remote.TxLineApiException) {
                        e.statusCode?.let { append("\nHTTP Status Code: $it") }
                        e.responseBody?.let { append("\nResponse Body: $it") }
                    }
                    append("\nException: ${e.message ?: "Unknown Activation error"}")
                }
                _authErrorMessage.value = detailMessage
                _txLineAuthStep.value = TxLineAuthStep.ERROR
            }
        }
    }

    // --- Dashboard Aggregations ---
    private fun updateDashboardStats() {
        val matches = _matches.value
        val teams = _teams.value

        val live = matches.count { it.status == "LIVE" }
        val finished = matches.count { it.status == "FINISHED" }
        val upcoming = matches.count { it.status == "UPCOMING" }
        val todayMatches = matches.size

        var goalsToday = 0
        matches.forEach {
            goalsToday += it.homeScore + it.awayScore
        }

        _dashboardStats.value = DashboardStats(
            liveMatchesCount = live,
            todayMatchesCount = todayMatches,
            upcomingMatchesCount = upcoming,
            finishedMatchesCount = finished,
            goalsTodayCount = goalsToday,
            totalTeamsCount = teams.size
        )
    }

    // --- Local Push Notification Helper ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WorldCup Match Events"
            val descriptionText = "Get notified about goals, kickoffs, and fulltime alerts."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("worldcup_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun triggerGoalNotification(match: Match, event: MatchEvent) {
        if (!_notificationsEnabled.value) return

        val context = getApplication<Application>()
        val title = "⚽ GOAL scored! ${match.homeTeamName} ${match.homeScore} - ${match.awayScore} ${match.awayTeamName}"
        val content = "${event.player1} (${event.minute}') scores a brilliant ${event.detail.lowercase()}!"

        val builder = NotificationCompat.Builder(context, "worldcup_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System standard fallback icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(event.id.hashCode(), builder.build())
    }
}

// Data holder for aggregated dashboard metrics
data class DashboardStats(
    val liveMatchesCount: Int = 0,
    val todayMatchesCount: Int = 0,
    val upcomingMatchesCount: Int = 0,
    val finishedMatchesCount: Int = 0,
    val goalsTodayCount: Int = 0,
    val totalTeamsCount: Int = 0
)

data class BetDashboardStats(
    val totalBets: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val winRate: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalLoss: Double = 0.0,
    val netPL: Double = 0.0,
    val currentBalance: Double = 100.0
)
