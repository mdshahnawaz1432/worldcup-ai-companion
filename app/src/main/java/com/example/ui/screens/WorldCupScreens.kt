package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Match
import com.example.data.model.MatchEvent
import com.example.data.model.MatchStats
import com.example.data.model.Team
import com.example.data.model.Odds
import com.example.data.remote.MatchAISummary
import com.example.data.remote.TxLineService
import kotlin.random.Random
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.WorldCupViewModel
import com.example.ui.viewmodel.TxLineAuthStep
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// --- REUSABLE COMPONENTS ---

@Composable
fun FlagImage(countryCode: String, modifier: Modifier = Modifier) {
    // Map 3-letter FIFA codes to 2-letter ISO codes for FlagCDN
    val isoCode = when (countryCode.uppercase()) {
        "USA" -> "us"
        "MEX" -> "mx"
        "CAN" -> "ca"
        "PAN" -> "pa"
        "BRA" -> "br"
        "ARG" -> "ar"
        "URU" -> "uy"
        "COL" -> "co"
        "FRA" -> "fr"
        "ESP" -> "es"
        "ENG" -> "gb" // England uses gb flag as fallback
        "GER" -> "de"
        "ITA" -> "it"
        "JPN" -> "jp"
        "SEN" -> "sn"
        "MAR" -> "ma"
        else -> "un"
    }

    val flagUrl = "https://flagcdn.com/w160/$isoCode.png"

    AsyncImage(
        model = flagUrl,
        contentDescription = "$countryCode Flag",
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    var isVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            isVisible = !isVisible
            delay(800)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(EmeraldLive.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, EmeraldLive.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isVisible) EmeraldLive else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE",
            color = EmeraldLive,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MatchCard(
    match: Match,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Competition & Live/Upcoming Badge & Favorite Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = match.competition,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.status == "LIVE") {
                        LiveBadge()
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (match.status == "FINISHED") {
                        Text(
                            text = "FT",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite Match",
                            tint = if (isFavorite) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Teams & Score Center
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Home Team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    FlagImage(match.homeTeamCode, modifier = Modifier.size(48.dp, 32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = match.homeTeamName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score / Time display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (match.status == "UPCOMING") {
                        val parsedTime = try {
                            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val date = format.parse(match.startTime)
                            val displayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            displayFormat.format(date ?: Date())
                        } catch (e: Exception) {
                            "VS"
                        }
                        Text(
                            text = parsedTime,
                            color = GoldPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "KICKOFF",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // LIVE / FINISHED Scores
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = match.homeScore.toString(),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = " : ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = match.awayScore.toString(),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        if (match.status == "LIVE") {
                            Text(
                                text = "${match.minute}'",
                                color = EmeraldLive,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Away Team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    FlagImage(match.awayTeamCode, modifier = Modifier.size(48.dp, 32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = match.awayTeamName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Venue row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Venue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = match.venue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TeamCard(
    team: Team,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            FlagImage(team.code, modifier = Modifier.size(40.dp, 28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = team.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${team.group} • ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${team.points} pts",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Recent Form Balls
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                team.form.split(",").take(3).forEach { f ->
                    val color = when (f) {
                        "W" -> EmeraldLive
                        "D" -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Red
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f))
                            .border(1.dp, color, CircleShape)
                    ) {
                        Text(text = f, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite Team",
                    tint = if (isFavorite) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- 1. HOME SCREEN ---

@Composable
fun HomeScreen(
    viewModel: WorldCupViewModel,
    onNavigateToLive: () -> Unit,
    onNavigateToFixtures: () -> Unit,
    onNavigateToTeams: () -> Unit,
    onNavigateToMatchDetails: (String) -> Unit
) {
    val matches by viewModel.matches.collectAsState()
    val favMatches by viewModel.favoriteMatches.collectAsState()
    val liveMatches = matches.filter { it.status == "LIVE" }
    val upcomingMatches = matches.filter { it.status == "UPCOMING" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Image Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = R.drawable.img_hero_banner,
                    contentDescription = "Hero Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "WorldCup AI Companion",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Smart, real-time analytics powered by TxLINE",
                        color = GoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Quick Navigation Grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToLive,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Live Matches")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LIVE NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onNavigateToFixtures,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Fixtures", tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FIXTURES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Button(
                    onClick = onNavigateToTeams,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Teams", tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TEAMS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // FEATURED LIVE MATCHES SECTION
        if (liveMatches.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "FEATURED LIVE ACTION",
                        color = GoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    items(liveMatches) { match ->
                        val isFav = favMatches.any { it.matchId == match.id }
                        MatchCard(
                            match = match,
                            isFavorite = isFav,
                            onFavoriteToggle = { viewModel.toggleMatchFavorite(match.id) },
                            onClick = { onNavigateToMatchDetails(match.id) },
                            modifier = Modifier.width(300.dp)
                        )
                    }
                }
            }
        }

        // UPCOMING MATCHES SECTION
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "UPCOMING MATCHES",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (upcomingMatches.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No upcoming matches scheduled today.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(upcomingMatches.take(3)) { match ->
                val isFav = favMatches.any { it.matchId == match.id }
                MatchCard(
                    match = match,
                    isFavorite = isFav,
                    onFavoriteToggle = { viewModel.toggleMatchFavorite(match.id) },
                    onClick = { onNavigateToMatchDetails(match.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// --- 2. LIVE MATCHES SCREEN ---

@Composable
fun LiveMatchesScreen(
    viewModel: WorldCupViewModel,
    onNavigateToMatchDetails: (String) -> Unit
) {
    val matches by viewModel.filteredMatches.collectAsState(emptyList())
    val favMatches by viewModel.favoriteMatches.collectAsState()
    val liveMatches = matches.filter { it.status == "LIVE" }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBarComponent(viewModel = viewModel)

        if (liveMatches.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "No Matches",
                        tint = GoldPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Live Matches currently in progress.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please check Upcoming Fixtures for today's schedule.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(liveMatches) { match ->
                    val isFav = favMatches.any { it.matchId == match.id }
                    MatchCard(
                        match = match,
                        isFavorite = isFav,
                        onFavoriteToggle = { viewModel.toggleMatchFavorite(match.id) },
                        onClick = { onNavigateToMatchDetails(match.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarComponent(viewModel: WorldCupViewModel) {
    val query by viewModel.searchQuery.collectAsState()

    TextField(
        value = query,
        onValueChange = { viewModel.updateSearchQuery(it) },
        placeholder = { Text("Search teams, matches, or groups...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "ClearIcon")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

// --- 3. MATCH DETAILS SCREEN (WITH TABS: OVERVIEW, STATISTICS, TIMELINE, AI REPORT) ---

@Composable
fun MatchDetailsScreen(
    viewModel: WorldCupViewModel,
    onBack: () -> Unit
) {
    val detailContainer by viewModel.selectedMatchDetail.collectAsState()
    val aiAnalysis by viewModel.selectedMatchAIAnalysis.collectAsState()
    val isAiLoading by viewModel.isAILoading.collectAsState()
    val favMatches by viewModel.favoriteMatches.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Stats", "Events", "AI Insights")

    val container = detailContainer

    if (container == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(color = GoldPrimary)
        }
        return
    }

    val match = container.match
    val stats = container.stats
    val events = container.events
    val odds = container.odds
    val isFav = favMatches.any { it.matchId == match.id }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Bar Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Match Details",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleMatchFavorite(match.id) }) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Fav",
                    tint = if (isFav) GoldPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { viewModel.refreshMatchDetails() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // Live score board header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Home
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    FlagImage(match.homeTeamCode, modifier = Modifier.size(56.dp, 36.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(match.homeTeamName, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                // Scores
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                    if (match.status == "UPCOMING") {
                        Text("VS", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldPrimary)
                    } else {
                        Text(
                            text = "${match.homeScore} - ${match.awayScore}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (match.status == "LIVE") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldLive))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${match.minute}'", color = EmeraldLive, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Text("FINISHED", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Away
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    FlagImage(match.awayTeamCode, modifier = Modifier.size(56.dp, 36.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(match.awayTeamName, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GoldPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> OverviewTab(match, odds, viewModel, onBack)
                1 -> StatsTab(match, stats)
                2 -> EventsTab(events)
                3 -> AIInsightsTab(aiAnalysis, isAiLoading)
            }
        }
    }
}

@Composable
fun OverviewTab(match: Match, odds: Odds?, viewModel: WorldCupViewModel, onBack: () -> Unit) {
    val walletAddress by viewModel.solanaWalletAddress.collectAsState()
    val walletBalance by viewModel.solanaWalletBalance.collectAsState()
    val selectedBetOption by viewModel.selectedBetOption.collectAsState()
    val context = LocalContext.current
    var showWalletDialog by remember { mutableStateOf(false) }
    var stake by remember { mutableStateOf(10.0) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Information card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MATCH INFO", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow(label = "Competition", value = match.competition)
                    InfoRow(label = "Group", value = match.group)
                    InfoRow(label = "Stadium", value = match.venue)
                    if (match.status == "UPCOMING") {
                        InfoRow(label = "Kickoff Time", value = match.startTime)
                    }
                }
            }
        }

        // Odds Card
        if (odds != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TXLINE REAL-TIME ODDS", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OddBox(
                                label = match.homeTeamCode,
                                value = odds.homeWin.toString(),
                                isSelected = selectedBetOption == "HOME",
                                onClick = { viewModel.selectBetOption("HOME") },
                                modifier = Modifier.weight(1f).testTag("odd_box_home")
                            )
                            OddBox(
                                label = "DRAW",
                                value = odds.draw.toString(),
                                isSelected = selectedBetOption == "DRAW",
                                onClick = { viewModel.selectBetOption("DRAW") },
                                modifier = Modifier.weight(1f).testTag("odd_box_draw")
                            )
                            OddBox(
                                label = match.awayTeamCode,
                                value = odds.awayWin.toString(),
                                isSelected = selectedBetOption == "AWAY",
                                onClick = { viewModel.selectBetOption("AWAY") },
                                modifier = Modifier.weight(1f).testTag("odd_box_away")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Stake Section
                        Text(
                            text = "STAKE AMOUNT: ${String.format(Locale.US, "%.1f", stake)} SOL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = stake.toFloat(),
                            onValueChange = { stake = (Math.round(it * 2) / 2.0) }, // round to nearest 0.5
                            valueRange = 1.0f..100.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("stake_slider")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(5.0, 10.0, 25.0, 50.0).forEach { amount ->
                                val isSelected = stake == amount
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { stake = amount }
                                        .padding(vertical = 6.dp)
                                        .testTag("stake_chip_$amount")
                                ) {
                                    Text(
                                        text = "${amount.toInt()} SOL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val oddsValue = when (selectedBetOption) {
                                    "HOME" -> odds.homeWin
                                    "DRAW" -> odds.draw
                                    "AWAY" -> odds.awayWin
                                    else -> 1.0
                                }
                                viewModel.placeBet(match, selectedBetOption!!, oddsValue, stake)
                                Toast.makeText(context, "Bet confirmed successfully on $selectedBetOption!", Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                            enabled = selectedBetOption != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                disabledContainerColor = GoldPrimary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("confirm_bet_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (selectedBetOption != null) "Confirm Bet ($selectedBetOption)" else "Select an Option to Bet",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Solana Wallet Adapter Option Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("SOLANA PREDICTION PORTAL", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Stake predictions on-chain (Optional)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Build, contentDescription = "Solana Logo", tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (walletAddress == null) {
                        Button(
                            onClick = { showWalletDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Solana Wallet", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Status: Connected", color = EmeraldLive, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Balance: $walletBalance SOL", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Address: $walletAddress",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        val oddsValue = if (odds != null) {
                                            when (selectedBetOption) {
                                                "HOME" -> odds.homeWin
                                                "DRAW" -> odds.draw
                                                "AWAY" -> odds.awayWin
                                                else -> 1.0
                                            }
                                        } else {
                                            1.5
                                        }
                                        viewModel.placeBet(match, selectedBetOption ?: "HOME", oddsValue, stake)
                                        Toast.makeText(context, "Transaction successfully broadcasted on Solana!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    },
                                    enabled = selectedBetOption != null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldLive,
                                        disabledContainerColor = EmeraldLive.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).testTag("solana_confirm_bet_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Confirm Bet", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.disconnectSolanaWallet() },
                                    modifier = Modifier.weight(0.8f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Disconnect", color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Solana mobile wallet dialog simulator
    if (showWalletDialog) {
        Dialog(onDismissRequest = { showWalletDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("SELECT SOLANA WALLET", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GoldPrimary)
                    Spacer(modifier = Modifier.height(16.dp))

                    WalletItem(name = "Phantom Wallet", desc = "Most Popular Mobile Wallet") {
                        viewModel.connectWallet("Phantom Wallet", "app.phantom")
                        showWalletDialog = false
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    WalletItem(name = "Solflare Wallet", desc = "Secure and Ultra Fast") {
                        viewModel.connectWallet("Solflare Wallet", "com.solflare.mobile")
                        showWalletDialog = false
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    WalletItem(name = "Backpack", desc = "The Next Gen Multi-chain Wallet") {
                        viewModel.connectWallet("Backpack", "co.backpack.wallet")
                        showWalletDialog = false
                    }
                }
            }
        }
    }
}

@Composable
fun WalletItem(name: String, desc: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Icon(Icons.Default.AccountBox, contentDescription = "WalletIcon", tint = GoldPrimary, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun OddBox(
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        GoldPrimary
    } else {
        Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp) // Generous padding for visual comfort
            .heightIn(min = 48.dp) // Accessibility guideline touch target
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = if (isSelected) GoldPrimary else GoldPrimary.copy(alpha = 0.8f)
        )
    }
}

// Stats tab progress indicators
@Composable
fun StatsTab(match: Match, stats: MatchStats?) {
    if (stats == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Match statistics not generated yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TEAM ATTACK METRICS", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    StatRow(label = "Possession", homeValue = stats.homePossession, awayValue = stats.awayPossession, isPercentage = true)
                    StatRow(label = "Expected Goals (xG)", homeValueStr = stats.homeXG.toString(), awayValueStr = stats.awayXG.toString(), ratio = stats.homeXG / (stats.homeXG + stats.awayXG))
                    StatRow(label = "Total Shots", homeValue = stats.homeShots, awayValue = stats.awayShots)
                    StatRow(label = "Shots on Target", homeValue = stats.homeShotsOnTarget, awayValue = stats.awayShotsOnTarget)
                    StatRow(label = "Corners", homeValue = stats.homeCorners, awayValue = stats.awayCorners)
                    StatRow(label = "Fouls Committed", homeValue = stats.awayFouls, awayValue = stats.homeFouls) // reverse of attack
                    StatRow(label = "Yellow Cards", homeValue = stats.homeYellowCards, awayValue = stats.awayYellowCards)
                    StatRow(label = "Red Cards", homeValue = stats.homeRedCards, awayValue = stats.awayRedCards)
                }
            }
        }
    }
}

@Composable
fun StatRow(
    label: String,
    homeValue: Int,
    awayValue: Int,
    isPercentage: Boolean = false
) {
    val total = homeValue + awayValue
    val ratio = if (total > 0) homeValue.toFloat() / total.toFloat() else 0.5f

    StatRow(
        label = label,
        homeValueStr = if (isPercentage) "$homeValue%" else homeValue.toString(),
        awayValueStr = if (isPercentage) "$awayValue%" else awayValue.toString(),
        ratio = ratio.toDouble()
    )
}

@Composable
fun StatRow(
    label: String,
    homeValueStr: String,
    awayValueStr: String,
    ratio: Double
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(homeValueStr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(awayValueStr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress slide
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(ratio.toFloat().coerceIn(0.01f, 0.99f))
                        .background(GoldPrimary)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((1f - ratio.toFloat()).coerceIn(0.01f, 0.99f))
                        .background(EmeraldLive)
                )
            }
        }
    }
}

// Events timeline tab
@Composable
fun EventsTab(events: List<MatchEvent>) {
    if (events.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("No significant events reported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(events.sortedByDescending { it.minute }) { event ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // Time box
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = "${event.minute}'",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Icon of event
                val icon = when (event.type) {
                    "GOAL" -> Icons.Default.PlayArrow
                    "YELLOW_CARD" -> Icons.Default.Warning
                    "RED_CARD" -> Icons.Default.Warning
                    else -> Icons.Default.Refresh
                }
                val iconColor = when (event.type) {
                    "GOAL" -> EmeraldLive
                    "YELLOW_CARD" -> Color.Yellow
                    "RED_CARD" -> Color.Red
                    else -> GoldPrimary
                }

                Icon(
                    imageVector = icon,
                    contentDescription = event.type,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Texts
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.player1,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (event.player2 != null) {
                        Text(
                            text = "Assist: ${event.player2}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${event.type} - ${event.detail}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Team label
                Text(
                    text = event.team.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// AI Insights Tab
@Composable
fun AIInsightsTab(aiAnalysis: MatchAISummary?, isLoading: Boolean) {
    if (isLoading) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GoldPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Analyzing live statistics via Gemini...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        return
    }

    if (aiAnalysis == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("AI Insights not available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // AI Win probabilities Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AI WIN PROBABILITY ESTIMATE", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Confidence: ${aiAnalysis.confidenceLevel}",
                            color = if (aiAnalysis.confidenceLevel == "High") EmeraldLive else GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Percentages bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        if (aiAnalysis.winProbHome > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(aiAnalysis.winProbHome.toFloat())
                                    .background(GoldPrimary)
                            ) {
                                Text("${aiAnalysis.winProbHome}%", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (aiAnalysis.winProbDraw > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(aiAnalysis.winProbDraw.toFloat())
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("${aiAnalysis.winProbDraw}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (aiAnalysis.winProbAway > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(aiAnalysis.winProbAway.toFloat())
                                    .background(EmeraldLive)
                            ) {
                                Text("${aiAnalysis.winProbAway}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Labels description
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LegendItem(label = "Home Win", color = GoldPrimary)
                        LegendItem(label = "Draw", color = MaterialTheme.colorScheme.surfaceVariant)
                        LegendItem(label = "Away Win", color = EmeraldLive)
                    }
                }
            }
        }

        // Summary card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SHORT MATCH SUMMARY", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiAnalysis.shortSummary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        // Momentum analysis
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SPATIAL & MOMENTUM REPORT", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiAnalysis.momentumAnalysis, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        // Key turning points
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("KEY TURNING POINTS", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiAnalysis.keyTurningPoints, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        // Top Performers
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOP PERFORMERS", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiAnalysis.topPerformers, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        // Analytical estimate disclaimer
        item {
            Text(
                text = "AI predictions are analytical estimates based on live match statistics and should not be treated as guaranteed outcomes.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

// --- 4. TEAMS SCREEN ---

@Composable
fun TeamsScreen(
    viewModel: WorldCupViewModel,
    onNavigateToTeamDetails: (String) -> Unit
) {
    val teams by viewModel.filteredTeams.collectAsState(emptyList())
    val favTeams by viewModel.favoriteTeams.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = All Teams, 1 = Standings

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBarComponent(viewModel = viewModel)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DayTabItem(label = "ALL TEAMS", isSelected = selectedTab == 0) { selectedTab = 0 }
            DayTabItem(label = "GROUP STANDINGS", isSelected = selectedTab == 1) { selectedTab = 1 }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (selectedTab == 1) {
            val grouped = teams.groupBy { it.group }.toSortedMap()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                grouped.forEach { (groupLetter, groupTeams) ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "GROUP $groupLetter",
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(groupTeams.sortedByDescending { it.points }) { team ->
                        val isFav = favTeams.any { it.teamCode == team.code }
                        TeamCard(
                            team = team,
                            isFavorite = isFav,
                            onFavoriteToggle = { viewModel.toggleTeamFavorite(team.code) },
                            onClick = { onNavigateToTeamDetails(team.code) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(teams) { team ->
                    val isFav = favTeams.any { it.teamCode == team.code }
                    TeamCard(
                        team = team,
                        isFavorite = isFav,
                        onFavoriteToggle = { viewModel.toggleTeamFavorite(team.code) },
                        onClick = { onNavigateToTeamDetails(team.code) }
                    )
                }
            }
        }
    }
}

// --- 5. FIXTURES SCREEN ---

@Composable
fun FixturesScreen(
    viewModel: WorldCupViewModel,
    onNavigateToMatchDetails: (String) -> Unit
) {
    val matches by viewModel.matches.collectAsState()
    val favMatches by viewModel.favoriteMatches.collectAsState()

    var selectedDay by remember { mutableStateOf(0) } // 0 = Today, 1 = Tomorrow, 2 = Upcoming

    val todayMatches = matches.filter { it.status == "LIVE" || it.status == "FINISHED" || it.id == "m3" }
    val tomorrowMatches = matches.filter { it.id == "m4" }
    val upcomingMatches = matches.filter { it.id == "m3" || it.id == "m4" }

    val displayMatches = when (selectedDay) {
        0 -> todayMatches
        1 -> tomorrowMatches
        else -> upcomingMatches
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Date Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DayTabItem(label = "TODAY", isSelected = selectedDay == 0) { selectedDay = 0 }
            DayTabItem(label = "TOMORROW", isSelected = selectedDay == 1) { selectedDay = 1 }
            DayTabItem(label = "UPCOMING", isSelected = selectedDay == 2) { selectedDay = 2 }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayMatches.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("No matches scheduled for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayMatches) { match ->
                    val isFav = favMatches.any { it.matchId == match.id }
                    MatchCard(
                        match = match,
                        isFavorite = isFav,
                        onFavoriteToggle = { viewModel.toggleMatchFavorite(match.id) },
                        onClick = { onNavigateToMatchDetails(match.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DayTabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable { onClick() }
            .background(if (isSelected) GoldPrimary else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

// --- 6. DASHBOARD SCREEN ---

@Composable
fun DashboardScreen(viewModel: WorldCupViewModel) {
    val stats by viewModel.dashboardStats.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "TOURNAMENT INSIGHTS",
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                item { DashboardTile(label = "Live Matches", value = stats.liveMatchesCount.toString(), color = EmeraldLive) }
                item { DashboardTile(label = "Today's Fixtures", value = stats.todayMatchesCount.toString(), color = GoldPrimary) }
                item { DashboardTile(label = "Finished Matches", value = stats.finishedMatchesCount.toString(), color = MaterialTheme.colorScheme.onSurface) }
                item { DashboardTile(label = "Goals Scored", value = stats.goalsTodayCount.toString(), color = GoldPrimary) }
            }
        }

        item {
            val authStep by viewModel.txLineAuthStep.collectAsState()
            val guestJwt by viewModel.guestJwt.collectAsState()
            val txId by viewModel.subscriptionTxId.collectAsState()
            val sig by viewModel.activationSignature.collectAsState()
            val apiToken by viewModel.apiToken.collectAsState()
            val errMsg by viewModel.authErrorMessage.collectAsState()
            val walletAddress by viewModel.solanaWalletAddress.collectAsState()

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("txline_auth_panel")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TXLINE WEB3 API GATEWAY", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        
                        // Status badge
                        val (statusText, statusColor) = if (authStep == TxLineAuthStep.ACTIVATION_SUCCESSFUL) {
                            "LIVE" to EmeraldLive
                        } else {
                            "SANDBOX" to Color(0xFFF39C12)
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (authStep == TxLineAuthStep.ACTIVATION_SUCCESSFUL) {
                        // Fully Authenticated and Active state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = EmeraldLive,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Production API Authenticated", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("All REST operations are verified live.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val maskedToken = apiToken?.let { 
                            if (it.length > 15) it.take(8) + "..." + it.takeLast(6) else it 
                        } ?: "Unknown Token"
                        
                        InfoRow(label = "Secure Token", value = maskedToken)
                        InfoRow(label = "Wallet Address", value = walletAddress?.take(6) + "..." + walletAddress?.takeLast(4))
                        InfoRow(label = "Data Source", value = "TxLINE REST API (Live Verified)")
                        InfoRow(label = "Refresh Interval", value = "15s Poll with Auto-Retry")

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.disconnectSolanaWallet() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset and Disconnect Gateway")
                        }
                    } else {
                        // Not authenticated state
                        if (walletAddress == null) {
                            // Wallet not connected instructions
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Wallet Disconnected",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Solana Wallet Disconnected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Please connect your wallet in the Prediction Portal to trigger the official 6-step TxLINE Web3 authentication flow.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp
                                )
                            }
                        } else {
                            // Wallet connected: show stepper list and activate button
                            Text(
                                text = "Authentication Progress Checklist:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Step 1
                            StepRow(
                                stepNumber = "1",
                                title = "Request Guest JWT",
                                description = "POST /auth/guest/start",
                                isDone = authStep > TxLineAuthStep.REQUESTING_GUEST_JWT,
                                isActive = authStep == TxLineAuthStep.REQUESTING_GUEST_JWT
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Step 2
                            StepRow(
                                stepNumber = "2",
                                title = "On-Chain Subscription Transaction",
                                description = "Solana Contract Subscription (1.5 SOL)",
                                isDone = authStep > TxLineAuthStep.WAITING_CONFIRMATION,
                                isActive = authStep == TxLineAuthStep.CREATING_SUBSCRIPTION || authStep == TxLineAuthStep.WAITING_CONFIRMATION
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Step 3
                            StepRow(
                                stepNumber = "3",
                                title = "Sign Message with Wallet",
                                description = "Sign activation message block",
                                isDone = authStep > TxLineAuthStep.REQUESTING_SIGNATURE,
                                isActive = authStep == TxLineAuthStep.REQUESTING_SIGNATURE
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Step 4
                            StepRow(
                                stepNumber = "4",
                                title = "Activate API Token",
                                description = "POST /api/token/activate",
                                isDone = authStep == TxLineAuthStep.ACTIVATION_SUCCESSFUL,
                                isActive = authStep == TxLineAuthStep.ACTIVATING_API
                            )

                            if (errMsg != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(errMsg!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action button / loading state
                            val isRunning = authStep != TxLineAuthStep.WALLET_CONNECTED && authStep != TxLineAuthStep.ERROR && authStep != TxLineAuthStep.DISCONNECTED && authStep != TxLineAuthStep.ACTIVATION_SUCCESSFUL
                            if (isRunning) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = when(authStep) {
                                            TxLineAuthStep.CONNECTING_WALLET -> "Connecting to Selected Wallet..."
                                            TxLineAuthStep.REQUESTING_GUEST_JWT -> "Requesting Guest JWT..."
                                            TxLineAuthStep.CREATING_SUBSCRIPTION -> "Creating Solana Subscription (1.5 SOL)..."
                                            TxLineAuthStep.WAITING_CONFIRMATION -> "Waiting for transaction confirmation..."
                                            TxLineAuthStep.REQUESTING_SIGNATURE -> "Prompting signature on connected wallet..."
                                            TxLineAuthStep.ACTIVATING_API -> "Activating API token..."
                                            else -> "Loading..."
                                        },
                                        fontSize = 11.sp,
                                        color = GoldPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.startTxLineAuthFlow() },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("activate_txline_btn")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Activate")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Activate TxLINE Web3 Gateway", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepRow(
    stepNumber: String,
    title: String,
    description: String,
    isDone: Boolean,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else Color.Transparent, 
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 8.dp)
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Done",
                tint = EmeraldLive,
                modifier = Modifier.size(20.dp)
            )
        } else if (isActive) {
            CircularProgressIndicator(
                color = GoldPrimary,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(stepNumber, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isActive) GoldPrimary else if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DashboardTile(label: String, value: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

// --- 7. FAVORITES SCREEN ---

@Composable
fun FavoritesScreen(
    viewModel: WorldCupViewModel,
    onNavigateToMatchDetails: (String) -> Unit,
    onNavigateToTeamDetails: (String) -> Unit
) {
    val favMatches by viewModel.favoriteMatches.collectAsState()
    val favTeams by viewModel.favoriteTeams.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val teams by viewModel.teams.collectAsState()

    val myMatches = matches.filter { m -> favMatches.any { it.matchId == m.id } }
    val myTeams = teams.filter { t -> favTeams.any { it.teamCode == t.code } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("FAVORITE MATCHES", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (myMatches.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                ) {
                    Text("No saved matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(myMatches) { match ->
                MatchCard(
                    match = match,
                    isFavorite = true,
                    onFavoriteToggle = { viewModel.toggleMatchFavorite(match.id) },
                    onClick = { onNavigateToMatchDetails(match.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("FAVORITE TEAMS", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (myTeams.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                ) {
                    Text("No saved teams.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(myTeams) { team ->
                TeamCard(
                    team = team,
                    isFavorite = true,
                    onFavoriteToggle = { viewModel.toggleTeamFavorite(team.code) },
                    onClick = { onNavigateToTeamDetails(team.code) }
                )
            }
        }
    }
}

// --- 8. SETTINGS SCREEN ---

@Composable
fun SettingsScreen(viewModel: WorldCupViewModel) {
    val darkEnabled by viewModel.isDarkMode.collectAsState()
    val interval by viewModel.refreshInterval.collectAsState()
    val notifEnabled by viewModel.notificationsEnabled.collectAsState()
    val lang by viewModel.selectedLanguage.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PREFERENCES", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dark Mode Theme", fontSize = 13.sp)
                        Switch(checked = darkEnabled, onCheckedChange = { viewModel.setDarkMode(it) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Live Auto-Refresh Interval", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IntervalChip(label = "15s", isSelected = interval == 15) { viewModel.setRefreshInterval(15) }
                            IntervalChip(label = "30s", isSelected = interval == 30) { viewModel.setRefreshInterval(30) }
                            IntervalChip(label = "60s", isSelected = interval == 60) { viewModel.setRefreshInterval(60) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Match Push Notifications", fontSize = 13.sp)
                        Switch(checked = notifEnabled, onCheckedChange = { viewModel.setNotificationsEnabled(it) })
                    }
                }
            }
        }

        // Language Option Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LANGUAGE ARCHITECTURE", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LanguageSelector(label = "English", isSelected = lang == "en", modifier = Modifier.weight(1f)) { viewModel.setLanguage("en") }
                        LanguageSelector(label = "Español", isSelected = lang == "es", modifier = Modifier.weight(1f)) { viewModel.setLanguage("es") }
                        LanguageSelector(label = "Português", isSelected = lang == "pt", modifier = Modifier.weight(1f)) { viewModel.setLanguage("pt") }
                    }
                }
            }
        }

        // Demo test trigger card (highly requested for demo walkthroughs!)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DEMO & SIMULATION KIT", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Trigger a mockup live World Cup goal event to test dynamic UI scoring changes and system push notifications.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Goal simulation triggered!", Toast.LENGTH_SHORT).show()
                            // Update live match scores dynamically using the sandbox callback
                            val liveList = viewModel.matches.value.filter { it.status == "LIVE" }
                            if (liveList.isNotEmpty()) {
                                val match = liveList.random()
                                val isHome = Random.nextBoolean()
                                val scorer = if (isHome) "Richarlison" else "L. Messi"
                                val newEvent = MatchEvent(
                                    id = "demo_goal_" + UUID.randomUUID().toString().take(4),
                                    matchId = match.id,
                                    type = "GOAL",
                                    minute = match.minute + 1,
                                    team = if (isHome) "home" else "away",
                                    player1 = scorer,
                                    detail = "Stunning counter goal"
                                )
                                TxLineService.onGoalScored?.invoke(
                                    match.copy(
                                        homeScore = match.homeScore + (if (isHome) 1 else 0),
                                        awayScore = match.awayScore + (if (!isHome) 1 else 0),
                                        minute = match.minute + 1
                                    ),
                                    newEvent
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Trigger Simulated Goal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun IntervalChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable { onClick() }
            .background(if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp
        )
    }
}

@Composable
fun LanguageSelector(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable { onClick() }
            .background(if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, if (isSelected) GoldPrimary else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}
