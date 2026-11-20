package com.cricstudio.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cricstudio.app.R
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.MatchStateCode
import com.cricstudio.app.model.SavedStudio
import com.cricstudio.app.ui.AppUiState
import com.cricstudio.app.ui.components.CricLogo
import com.cricstudio.app.ui.components.LivePulseDot
import com.cricstudio.app.ui.theme.BroadcastDisplayFamily
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricRed

private enum class MatchFilter(val label: String) {
    LIVE("Live"),
    UPCOMING("Upcoming"),
    RESULTS("Results"),
    ALL("All"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: AppUiState,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onCreateStudio: (name: String, matchKey: String?) -> Unit,
    onOpenSavedStudio: (SavedStudio) -> Unit,
    onDeleteSavedStudio: (String) -> Unit,
) {
    var filter by remember { mutableStateOf(MatchFilter.LIVE) }
    var searchQuery by remember { mutableStateOf("") }
    var createDialogMatch by remember { mutableStateOf<Pair<Boolean, MatchListItem?>>(false to null) }
    var studioNameDraft by remember { mutableStateOf("") }

    val counts = remember(uiState.matches) {
        mapOf(
            MatchFilter.LIVE to uiState.matches.count { it.state == MatchStateCode.IN || it.isDemo },
            MatchFilter.UPCOMING to uiState.matches.count { it.state == MatchStateCode.PRE },
            MatchFilter.RESULTS to uiState.matches.count { it.state == MatchStateCode.POST },
            MatchFilter.ALL to uiState.matches.size,
        )
    }

    val filtered = remember(uiState.matches, filter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        uiState.matches.filter { m ->
            val matchesFilter = when (filter) {
                MatchFilter.LIVE -> m.state == MatchStateCode.IN || m.isDemo
                MatchFilter.UPCOMING -> m.state == MatchStateCode.PRE
                MatchFilter.RESULTS -> m.state == MatchStateCode.POST
                MatchFilter.ALL -> true
            }
            val matchesQuery = q.isEmpty() ||
                "${m.name} ${m.leagueName} ${m.description} ${m.teams.joinToString(" ") { it.name }}"
                    .lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    fun openCreateDialog(match: MatchListItem?) {
        studioNameDraft = if (match != null && match.teams.isNotEmpty()) {
            "${match.teams.joinToString(" vs ") { it.abbr }} — Live Watch-along"
        } else {
            "Cricket Live Show"
        }
        createDialogMatch = true to match
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { CricLogo() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_matches_button"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh live scores")
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                    Button(
                        onClick = { openCreateDialog(null) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("new_studio_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_studio))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 310.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val featured = uiState.matches.firstOrNull { it.state == MatchStateCode.IN || it.isDemo } ?: uiState.matches.firstOrNull()
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            Color(0xFF0A1633),
                                            Color(0xFF142A5E),
                                            Color(0xFF0C2B1E),
                                        ),
                                    ),
                                )
                                .padding(18.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(color = CricRed, shape = RoundedCornerShape(6.dp)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            LivePulseDot(color = Color.White, size = 6.dp)
                                            Text("YOUTUBE RTMP READY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "720p30 H.264 · Live Audio Mix",
                                        color = Color(0xFFBFD4FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.dashboard_headline),
                                    fontFamily = BroadcastDisplayFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD5E1F5),
                                )
                                if (featured != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "FEATURED BROADCAST",
                                                color = Color(0xFF81C995),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = featured.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Button(
                                            onClick = { openCreateDialog(featured) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("hero_launch_studio_button"),
                                        ) {
                                            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Launch Studio")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_matches_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("match_search_input"),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MatchFilter.entries) { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = {
                                    val c = counts[f] ?: 0
                                    Text(if (f == MatchFilter.ALL) "All ($c)" else "${f.label} ($c)")
                                },
                                modifier = Modifier.testTag("filter_${f.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            if (uiState.savedStudios.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.your_studios),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.savedStudios, key = { it.id }) { s ->
                                InputChip(
                                    selected = false,
                                    onClick = { onOpenSavedStudio(s) },
                                    label = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Delete saved studio",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDeleteSavedStudio(s.id) },
                                        )
                                    },
                                    modifier = Modifier.testTag("saved_studio_${s.id}"),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.matchesError != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Could not load ESPN live scores (${uiState.matchesError}). The recorded T20I demo replay is ready below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (uiState.loadingMatches && uiState.matches.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SportsCricket,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.no_matches_message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(filtered, key = { "${it.leagueId}:${it.id}" }) { match ->
                    MatchCard(
                        match = match,
                        onCreate = { openCreateDialog(match) },
                    )
                }
            }
        }
    }

    if (createDialogMatch.first) {
        val selectedMatch = createDialogMatch.second
        AlertDialog(
            onDismissRequest = { createDialogMatch = false to null },
            title = { Text(stringResource(R.string.create_studio)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selectedMatch != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.SportsCricket, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "${selectedMatch.name} · ${selectedMatch.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = studioNameDraft,
                        onValueChange = { studioNameDraft = it },
                        label = { Text("Show title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_title_input"),
                    )
                    Text(
                        text = "Live scores, ball-by-ball commentary, automated scenes and event celebrations are attached automatically. You can switch matches anytime inside the studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mk = selectedMatch?.matchKey
                        createDialogMatch = false to null
                        onCreateStudio(studioNameDraft, mk)
                    },
                    enabled = studioNameDraft.isNotBlank(),
                    modifier = Modifier.testTag("confirm_enter_studio_button"),
                ) {
                    Text(stringResource(R.string.enter_studio))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { createDialogMatch = false to null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MatchCard(
    match: MatchListItem,
    onCreate: () -> Unit,
) {
    Card(
        onClick = onCreate,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_card_${match.id}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (match.state == MatchStateCode.IN) {
                    Surface(color = CricRed, shape = RoundedCornerShape(6.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LivePulseDot(color = Color.White, size = 6.dp)
                            Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = if (match.state == MatchStateCode.PRE) "Upcoming" else "Result",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (match.isDemo) {
                    Surface(color = CricGreen.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Demo replay",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = CricGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = match.leagueName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                match.teams.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!t.logo.isNullOrBlank()) {
                                AsyncImage(
                                    model = t.logo,
                                    contentDescription = t.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = t.abbr.take(3),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = t.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (t.winner) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = t.score,
                            fontFamily = BroadcastDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Text(
                text = listOf(match.description, match.statusText).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.create_studio),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
