package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlacedBet
import com.example.ui.theme.*
import com.example.ui.viewmodel.WorldCupViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetHistoryScreen(viewModel: WorldCupViewModel) {
    val bets by viewModel.filteredBets.collectAsState()
    val stats by viewModel.betDashboardStats.collectAsState()
    val searchQuery by viewModel.betSearchQuery.collectAsState()
    val selectedStatus by viewModel.betFilterStatus.collectAsState()
    val selectedDate by viewModel.betDateFilter.collectAsState()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper Section: Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "BET RESOLUTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "My Betting Ledger",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (bets.isNotEmpty() || searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_all_bets_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Ledger",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Dashboard Metrics
            item {
                BetDashboardGrid(stats = stats)
            }

            // 2. Cumulative P/L Chart
            item {
                CumulativePlChart(bets = bets)
            }

            // 3. Search and Filters
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Search box
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateBetSearchQuery(it) },
                        placeholder = { Text("Search bets by team/match name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateBetSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "ClearIcon")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bet_search_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Filters
                    Text(
                        text = "STATUS FILTER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statuses = listOf("ALL", "WON", "LOST", "PENDING")
                        items(statuses) { status ->
                            val isSelected = selectedStatus == status
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setBetFilterStatus(status) },
                                label = { Text(status, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("filter_chip_$status")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Date Filters
                    Text(
                        text = "DATE RANGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val dates = listOf(
                            "ALL_TIME" to "All Time",
                            "TODAY" to "Today",
                            "PAST_WEEK" to "Past 7 Days"
                        )
                        items(dates) { (key, label) ->
                            val isSelected = selectedDate == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setBetDateFilter(key) },
                                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("date_chip_$key")
                            )
                        }
                    }
                }
            }

            // 4. Ledger Entries Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEDGER ENTRIES (${bets.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 5. Entries List
            if (bets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Empty",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching bets found" else "No bets placed yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(bets) { bet ->
                    BetCardItem(
                        bet = bet,
                        onDelete = {
                            viewModel.deleteBet(bet)
                            Toast.makeText(context, "Bet entry deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Bets?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all entries from your betting history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllBets()
                        showClearDialog = false
                        Toast.makeText(context, "Bet history cleared successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_clear_bets")
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BetDashboardGrid(stats: com.example.ui.viewmodel.BetDashboardStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Net Balance & Win Rate Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LEDGER BALANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f SOL", stats.currentBalance),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "WIN RATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.winRate),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = EmeraldLive
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            // Statistics Grid (2x2)
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Net P/L",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val netColor = if (stats.netPL >= 0) EmeraldLive else DeepCrimson
                    val prefix = if (stats.netPL >= 0) "+" else ""
                    Text(
                        text = "$prefix${String.format(Locale.US, "%.2f SOL", stats.netPL)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = netColor
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Bets Placed",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stats.totalBets} bets",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Wins",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stats.totalWins} won",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldLive
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Losses",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stats.totalLosses} lost",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepCrimson
                    )
                }
            }
        }
    }
}

@Composable
fun CumulativePlChart(bets: List<PlacedBet>) {
    // We will draw a cumulative profit/loss chart over time
    // If we have empty history, show a placeholder diagram
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PERFORMANCE CHART (CUMULATIVE P/L)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (bets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Place bets to see trend lines",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Calculate cumulative values
                val sortedBets = bets.sortedBy { it.timestamp }
                val points = mutableListOf<Float>()
                var cumulative = 0.0
                points.add(0f) // Start at zero
                sortedBets.forEach { bet ->
                    cumulative += bet.profitLoss
                    points.add(cumulative.toFloat())
                }

                val minPl = points.minOrNull() ?: 0f
                val maxPl = points.maxOrNull() ?: 0f
                val range = if (maxPl - minPl == 0f) 1f else (maxPl - minPl)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val width = size.width
                    val height = size.height

                    // Draw baseline
                    val baselineY = height - ((0f - minPl) / range) * height
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(0f, baselineY),
                        end = Offset(width, baselineY),
                        strokeWidth = 2f
                    )

                    // Draw trend path
                    val path = Path()
                    val pointCount = points.size
                    val stepX = width / (pointCount - 1)

                    points.forEachIndexed { index, value ->
                        val x = index * stepX
                        val y = height - ((value - minPl) / range) * height
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    // Draw line
                    drawPath(
                        path = path,
                        color = GoldPrimary,
                        style = Stroke(width = 4f)
                    )

                    // Fill under path
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GoldPrimary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dateFormat = SimpleDateFormat("MMM d", Locale.US)
                    Text(
                        text = dateFormat.format(Date(sortedBets.first().timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Latest Ledger Update",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(sortedBets.last().timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BetCardItem(bet: PlacedBet, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)
    val betDate = dateFormat.format(Date(bet.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("bet_card_${bet.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Match Name & Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bet.matchName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Placed on: $betDate",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_bet_${bet.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details section: Selection, Odds, Stake, Payout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SELECTION",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = bet.selectedOutcome,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldPrimary
                    )
                }

                Column {
                    Text(
                        text = "ODDS",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fx", bet.odds),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(
                        text = "STAKE",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f SOL", bet.stake),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PAYOUT",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    val potentialPayout = bet.stake * bet.odds
                    Text(
                        text = String.format(Locale.US, "%.2f SOL", potentialPayout),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                val (statusText, statusBg, statusColor) = when (bet.status) {
                    "WON" -> Triple("WON", EmeraldLive.copy(alpha = 0.15f), EmeraldLive)
                    "LOST" -> Triple("LOST", DeepCrimson.copy(alpha = 0.15f), DeepCrimson)
                    else -> Triple("PENDING", GoldPrimary.copy(alpha = 0.15f), GoldPrimary)
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // Profit / Loss Statement
                if (bet.status != "PENDING") {
                    val plColor = if (bet.profitLoss >= 0) EmeraldLive else DeepCrimson
                    val plPrefix = if (bet.profitLoss >= 0) "Profit: +" else "Loss: "
                    Text(
                        text = "$plPrefix${String.format(Locale.US, "%.2f SOL", bet.profitLoss)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = plColor
                    )
                } else {
                    Text(
                        text = "Awaiting fulltime result",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
