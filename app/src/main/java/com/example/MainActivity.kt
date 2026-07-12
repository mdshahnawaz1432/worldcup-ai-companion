package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.WorldCupTheme
import com.example.ui.viewmodel.WorldCupViewModel

class MainActivity : ComponentActivity() {
    private var viewModelRef: WorldCupViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WorldCupViewModel = viewModel()
            viewModelRef = viewModel
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            WorldCupTheme(darkTheme = isDarkMode) {
                MainAppShell(viewModel = viewModel)
            }
        }
        intent?.data?.let { handleDeepLink(it) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(data: android.net.Uri) {
        if (data.scheme == "worldcupcompanion") {
            viewModelRef?.handleWalletDeepLinkRedirect(data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(viewModel: WorldCupViewModel) {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMatchId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentScreen != "details") {
                TopAppBar(
                    title = {
                        Row {
                            Text(
                                text = "WorldCup AI",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " Companion",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentScreen = "settings" }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen != "details") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == "home",
                        onClick = { currentScreen = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "live",
                        onClick = { currentScreen = "live" },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Live") },
                        label = { Text("Live", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "fixtures",
                        onClick = { currentScreen = "fixtures" },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Fixtures") },
                        label = { Text("Fixtures", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "saved",
                        onClick = { currentScreen = "saved" },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Saved") },
                        label = { Text("Saved", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "dashboard",
                        onClick = { currentScreen = "dashboard" },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Insights") },
                        label = { Text("Insights", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "bets",
                        onClick = { currentScreen = "bets" },
                        icon = { Icon(Icons.Default.List, contentDescription = "Ledger") },
                        label = { Text("Ledger", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "bets" -> BetHistoryScreen(viewModel = viewModel)
                "home" -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLive = { currentScreen = "live" },
                    onNavigateToFixtures = { currentScreen = "fixtures" },
                    onNavigateToTeams = { currentScreen = "teams" },
                    onNavigateToMatchDetails = { matchId ->
                        selectedMatchId = matchId
                        viewModel.selectMatch(matchId)
                        currentScreen = "details"
                    }
                )
                "live" -> LiveMatchesScreen(
                    viewModel = viewModel,
                    onNavigateToMatchDetails = { matchId ->
                        selectedMatchId = matchId
                        viewModel.selectMatch(matchId)
                        currentScreen = "details"
                    }
                )
                "fixtures" -> FixturesScreen(
                    viewModel = viewModel,
                    onNavigateToMatchDetails = { matchId ->
                        selectedMatchId = matchId
                        viewModel.selectMatch(matchId)
                        currentScreen = "details"
                    }
                )
                "teams" -> TeamsScreen(
                    viewModel = viewModel,
                    onNavigateToTeamDetails = { /* Can handle team info panel details */ }
                )
                "saved" -> FavoritesScreen(
                    viewModel = viewModel,
                    onNavigateToMatchDetails = { matchId ->
                        selectedMatchId = matchId
                        viewModel.selectMatch(matchId)
                        currentScreen = "details"
                    },
                    onNavigateToTeamDetails = { /* Can handle team details */ }
                )
                "dashboard" -> DashboardScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
                "details" -> MatchDetailsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" }
                )
            }
        }
    }
}
