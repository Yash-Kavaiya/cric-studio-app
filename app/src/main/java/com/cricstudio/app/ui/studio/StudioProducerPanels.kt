package com.cricstudio.app.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricstudio.app.R
import com.cricstudio.app.model.ACCENT_SWATCHES
import com.cricstudio.app.model.AutomationMode
import com.cricstudio.app.model.AutomationRules
import com.cricstudio.app.model.AutopilotSettings
import com.cricstudio.app.model.BackgroundGroup
import com.cricstudio.app.model.BackgroundId
import com.cricstudio.app.model.BannerKind
import com.cricstudio.app.model.Branding
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.DEMO_MATCH_KEY
import com.cricstudio.app.model.MatchStateCode
import com.cricstudio.app.model.SceneGroup
import com.cricstudio.app.model.SceneId
import com.cricstudio.app.model.StreamPlatform
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.model.Suggestion
import com.cricstudio.app.ui.AppUiState
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricPurple
import com.cricstudio.app.ui.theme.CricRed
import com.cricstudio.app.ui.theme.CricYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenesProducerTab(
    studio: StudioState,
    onSelectScene: (SceneId) -> Unit,
    onSetCountdown: (Int?) -> Unit,
    onOpenAnnouncementDialog: () -> Unit,
    onOpenCustomOverlayDialog: () -> Unit,
) {
    var selectedGroup by remember { mutableStateOf<SceneGroup?>(null) }
    val scenes = remember(selectedGroup) {
        if (selectedGroup == null) SceneId.entries else SceneId.entries.filter { it.group == selectedGroup }
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                        label = { Text("All (${SceneId.entries.size})") },
                    )
                }
                items(SceneGroup.entries) { grp ->
                    FilterChip(
                        selected = selectedGroup == grp,
                        onClick = { selectedGroup = grp },
                        label = { Text(grp.label) },
                    )
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { onSetCountdown(if (studio.countdownTo == null) 5 else null) },
                    modifier = Modifier.testTag("countdown_toggle_button"),
                ) {
                    Text(if (studio.countdownTo == null) "5m Countdown" else "Clear Countdown")
                }
                OutlinedButton(
                    onClick = onOpenAnnouncementDialog,
                    modifier = Modifier.testTag("announcement_dialog_button"),
                ) {
                    Text("Announcement")
                }
                OutlinedButton(
                    onClick = onOpenCustomOverlayDialog,
                    modifier = Modifier.testTag("custom_overlay_dialog_button"),
                ) {
                    Text("Custom Overlay")
                }
            }
        }

        items(scenes.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { scene ->
                    val active = studio.scene == scene
                    Card(
                        onClick = { onSelectScene(scene) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                        border = BorderStroke(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scene_card_${scene.name.lowercase()}"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = scene.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = scene.group.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoDirectorTab(
    uiState: AppUiState,
    studio: StudioState,
    onUpdateAutopilot: ((AutopilotSettings) -> AutopilotSettings) -> Unit,
    onSetAutomationMode: (AutomationMode) -> Unit,
    onUpdateRules: ((AutomationRules) -> AutomationRules) -> Unit,
    onAcceptSuggestion: (Suggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onSimulateEvent: (CricketEventType) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Autopilot card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.auto_live_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Rotates scenes & triggers live commentary automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = studio.autopilot.enabled,
                            onCheckedChange = { enabled -> onUpdateAutopilot { it.copy(enabled = enabled) } },
                            modifier = Modifier.testTag("autopilot_switch"),
                        )
                    }
                    Column {
                        Text(
                            text = "Scene rotation interval: ${studio.autopilot.sceneSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = studio.autopilot.sceneSeconds.toFloat(),
                            onValueChange = { v -> onUpdateAutopilot { it.copy(sceneSeconds = v.toInt()) } },
                            valueRange = 8f..45f,
                        )
                    }
                    ToggleRow("Live commentator", studio.autopilot.commentator) { v ->
                        onUpdateAutopilot { it.copy(commentator = v) }
                    }
                    ToggleRow("Speak commentary aloud (TTS)", studio.autopilot.speakTts) { v ->
                        onUpdateAutopilot { it.copy(speakTts = v) }
                    }
                    ToggleRow("Include camera scenes in cycle", studio.autopilot.includeCameras) { v ->
                        onUpdateAutopilot { it.copy(includeCameras = v) }
                    }
                }
            }
        }

        // Director mode & rules
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.director_mode), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AutomationMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = studio.automation == mode,
                                onClick = { onSetAutomationMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, AutomationMode.entries.size),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                    ToggleRow("Wickets & hat-trick alerts", studio.rules.wicket) { v ->
                        onUpdateRules { it.copy(wicket = v) }
                    }
                    ToggleRow("Fours & sixes overlays", studio.rules.boundary) { v ->
                        onUpdateRules { it.copy(boundary = v) }
                    }
                    ToggleRow("End-of-over summaries", studio.rules.overEnd) { v ->
                        onUpdateRules { it.copy(overEnd = v) }
                    }
                    ToggleRow("Batting & bowling milestones", studio.rules.milestones) { v ->
                        onUpdateRules { it.copy(milestones = v) }
                    }
                    ToggleRow("Innings break & match result cuts", studio.rules.inningsBreak) { v ->
                        onUpdateRules { it.copy(inningsBreak = v) }
                    }
                }
            }
        }

        // Suggestions queue (Assist mode)
        if (uiState.suggestions.isNotEmpty()) {
            item {
                Text("Director suggestions", style = MaterialTheme.typography.titleSmall)
            }
            items(uiState.suggestions, key = { it.id }) { sug ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sug.reason, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            sug.scene?.let {
                                Text("Cut to ${it.displayName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { onAcceptSuggestion(sug) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Accept suggestion", tint = CricGreen)
                        }
                        IconButton(onClick = { onDismissSuggestion(sug.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss suggestion", tint = CricRed)
                        }
                    }
                }
            }
        }

        // Fire a moment rehearsal buttons
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.fire_a_moment), style = MaterialTheme.typography.titleMedium)
                    val moments = listOf(
                        CricketEventType.FOUR to ("FOUR!" to CricGreen),
                        CricketEventType.SIX to ("SIX!" to CricPurple),
                        CricketEventType.WICKET to ("WICKET" to CricRed),
                        CricketEventType.MILESTONE_BAT to ("FIFTY" to CricYellow),
                        CricketEventType.MILESTONE_BOWL to ("5-FOR" to CricYellow),
                        CricketEventType.PARTNERSHIP to ("50 STAND" to MaterialTheme.colorScheme.primary),
                        CricketEventType.HATTRICK_WATCH to ("HAT-TRICK" to CricRed),
                        CricketEventType.OVER_END to ("OVER END" to MaterialTheme.colorScheme.secondary),
                        CricketEventType.INNINGS_END to ("BREAK" to MaterialTheme.colorScheme.tertiary),
                        CricketEventType.MATCH_END to ("RESULT" to CricGreen),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        moments.forEach { (type, pair) ->
                            val (label, tint) = pair
                            Surface(
                                onClick = { onSimulateEvent(type) },
                                color = tint.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
                                modifier = Modifier.testTag("fire_moment_${type.name.lowercase()}"),
                            ) {
                                Text(
                                    text = label,
                                    color = tint,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live event feed
        item {
            Text(stringResource(R.string.live_event_feed), style = MaterialTheme.typography.titleSmall)
        }
        if (uiState.events.isEmpty()) {
            item {
                Text(
                    "Events detected from live balls or rehearsal triggers will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(uiState.events.take(12)) { ev ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(ev.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        ev.subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchSwitcherTab(
    uiState: AppUiState,
    studio: StudioState,
    onAttachMatch: (String?) -> Unit,
    onToggleDemoAutoPlay: () -> Unit,
    onStepDemo: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (studio.matchKey == DEMO_MATCH_KEY) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Demo T20I Replay Controls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onToggleDemoAutoPlay,
                                modifier = Modifier.testTag("demo_autoplay_button"),
                            ) {
                                Icon(
                                    if (uiState.demoAutoPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (uiState.demoAutoPlay) "Pause" else "Auto Play")
                            }
                            OutlinedButton(
                                onClick = { onStepDemo(1) },
                                modifier = Modifier.testTag("demo_step_ball_button"),
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("+1 Ball")
                            }
                            OutlinedButton(onClick = { onStepDemo(6) }) { Text("+1 Over") }
                            OutlinedButton(onClick = { onStepDemo(30) }) { Text("+5 Overs") }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Available matches", style = MaterialTheme.typography.titleMedium)
                if (studio.matchKey != null) {
                    TextButton(onClick = { onAttachMatch(null) }) { Text("Detach") }
                }
            }
        }

        items(uiState.matches, key = { it.matchKey }) { m ->
            val attached = studio.matchKey == m.matchKey
            Card(
                onClick = { onAttachMatch(m.matchKey) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (attached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    width = if (attached) 1.5.dp else 1.dp,
                    color = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.SportsCricket,
                        contentDescription = null,
                        tint = if (m.state == MatchStateCode.IN) CricRed else MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${m.leagueName} · ${m.statusText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (attached) {
                        Icon(Icons.Rounded.Check, contentDescription = "Attached", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun BrandAndStageTab(
    studio: StudioState,
    onUpdateBranding: ((Branding) -> Branding) -> Unit,
    onToggleBanner: (BannerKind) -> Unit,
    onToggleParticipantStage: (String) -> Unit,
    onToggleParticipantMute: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Studio Theme & Accent", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ACCENT_SWATCHES) { hex ->
                            val selected = studio.brand.accentHex == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onBackground else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    )
                                    .clickable { onUpdateBranding { it.copy(accentHex = hex) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected accent", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    BackgroundGroup.entries.forEach { group ->
                        Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BackgroundId.entries.filter { it.group == group }) { bg ->
                                val selected = studio.brand.background == bg
                                Box(
                                    modifier = Modifier
                                        .width(126.dp)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brush.linearGradient(listOf(Color(bg.primaryHex), Color(bg.secondaryHex))))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) Color(bg.defaultAccentHex) else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            onUpdateBranding {
                                                it.copy(background = bg, accentHex = bg.defaultAccentHex)
                                            }
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.BottomStart,
                                ) {
                                    Text(
                                        text = bg.displayName,
                                        color = if (bg.isLight) Color(0xFF14181F) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Graphics & Overlays", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = studio.brand.title,
                        onValueChange = { v -> onUpdateBranding { it.copy(title = v) } },
                        label = { Text("Show title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = studio.brand.ticker,
                        onValueChange = { v -> onUpdateBranding { it.copy(ticker = v) } },
                        label = { Text("Ticker message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = studio.brand.pollQuestion,
                        onValueChange = { v -> onUpdateBranding { it.copy(pollQuestion = v) } },
                        label = { Text("Fan poll question") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ToggleRow("Show scorebug on camera scenes", studio.brand.showScorebug) { v ->
                        onUpdateBranding { it.copy(showScorebug = v) }
                    }
                    ToggleRow("Show bottom ticker", studio.brand.showTicker) { v ->
                        onUpdateBranding { it.copy(showTicker = v) }
                    }
                    ToggleRow("Show commentary captions", studio.brand.captions) { v ->
                        onUpdateBranding { it.copy(captions = v) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = studio.banner?.kind == BannerKind.SPONSOR,
                            onClick = { onToggleBanner(BannerKind.SPONSOR) },
                            label = { Text("Sponsor Banner") },
                        )
                        FilterChip(
                            selected = studio.banner?.kind == BannerKind.QUICK_SCORE,
                            onClick = { onToggleBanner(BannerKind.QUICK_SCORE) },
                            label = { Text("Quick Score Banner") },
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera Stage Panel", style = MaterialTheme.typography.titleMedium)
                    studio.participants.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(p.role.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onToggleParticipantStage(p.id) }) {
                                    Icon(
                                        if (p.onStage) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle on stage",
                                        tint = if (p.onStage) CricGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onToggleParticipantMute(p.id) }) {
                                    Icon(
                                        if (p.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = "Toggle mute",
                                        tint = if (p.muted) CricRed else MaterialTheme.colorScheme.onSurface,
                                    )
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
fun StreamAndChatTab(
    uiState: AppUiState,
    onAddDestinationClick: () -> Unit,
    onEditDestinationClick: (com.cricstudio.app.model.StreamDestination) -> Unit,
    onToggleDestination: (String) -> Unit,
    onDeleteDestination: (String) -> Unit,
    onSelectQuality: (com.cricstudio.app.model.StreamQuality) -> Unit,
    onSayCustomLine: (String) -> Unit,
    onSendChat: (String) -> Unit,
) {
    var commentaryDraft by remember { mutableStateOf("") }
    var chatDraft by remember { mutableStateOf("") }
    val telemetry = uiState.streamTelemetry

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stream quality & RTMP destinations
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Stream Destinations",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = onAddDestinationClick,
                            modifier = Modifier.testTag("add_destination_button"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    // Encoding Quality Selector
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.cricstudio.app.model.StreamQuality.entries.forEach { q ->
                            FilterChip(
                                selected = telemetry.quality == q,
                                onClick = { onSelectQuality(q) },
                                label = { Text(q.label, fontSize = 11.sp) },
                                modifier = Modifier.testTag("quality_${q.name.lowercase()}"),
                            )
                        }
                    }

                    if (uiState.destinations.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                "Tap Add (or Go live), choose a platform and paste its stream key to broadcast straight from this device.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    } else {
                        uiState.destinations.forEach { d ->
                            val liveInfo = telemetry.destinationStatuses[d.id]
                            val missingKey = d.streamKey.isBlank()
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(d.platform.colorHex)),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                d.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${d.platform.label} · ${if (missingKey) "Key missing — tap Edit" else d.keyMasked}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (missingKey) CricRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Switch(checked = d.enabled, onCheckedChange = { onToggleDestination(d.id) })
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(onClick = { onEditDestinationClick(d) }) {
                                            Text("Edit Key")
                                        }
                                        IconButton(onClick = { onDeleteDestination(d.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete destination", tint = CricRed)
                                        }
                                    }
                                    if (liveInfo != null && liveInfo.status != com.cricstudio.app.model.BroadcastStatus.IDLE) {
                                        val tint = when (liveInfo.status) {
                                            com.cricstudio.app.model.BroadcastStatus.LIVE -> CricGreen
                                            com.cricstudio.app.model.BroadcastStatus.ERROR -> CricRed
                                            else -> CricYellow
                                        }
                                        Text(
                                            text = "${liveInfo.status.label}: ${liveInfo.message}" +
                                                if (liveInfo.bitrateKbps > 0) " (${liveInfo.bitrateKbps} kbps)" else "",
                                            color = tint,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    telemetry.lastError?.let { err ->
                        Surface(
                            color = CricRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CricRed.copy(alpha = 0.45f)),
                        ) {
                            Text(
                                text = err,
                                color = CricRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        }
                    }

                    if (telemetry.logs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "RTMP Connection Log",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            telemetry.logs.take(6).forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Say on air commentary box
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Live Commentary Cue", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = commentaryDraft,
                            onValueChange = { commentaryDraft = it },
                            placeholder = { Text("Type a line to caption & speak on air...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                if (commentaryDraft.isNotBlank()) {
                                    onSayCustomLine(commentaryDraft)
                                    commentaryDraft = ""
                                }
                            },
                            enabled = commentaryDraft.isNotBlank(),
                        ) {
                            Text("Air")
                        }
                    }
                    uiState.commentaryHistory.take(4).forEach { line ->
                        Text(
                            text = "• ${line.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Studio chat
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Studio Chat", style = MaterialTheme.typography.titleMedium)
                    uiState.chatMessages.takeLast(8).forEach { msg ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${msg.from}:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(msg.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chatDraft,
                            onValueChange = { chatDraft = it },
                            placeholder = { Text("Send studio message...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_chat_input"),
                        )
                        IconButton(
                            onClick = {
                                if (chatDraft.isNotBlank()) {
                                    onSendChat(chatDraft)
                                    chatDraft = ""
                                }
                            },
                            modifier = Modifier.testTag("studio_chat_send_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send chat")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
