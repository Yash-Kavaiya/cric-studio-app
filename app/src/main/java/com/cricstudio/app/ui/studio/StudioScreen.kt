package com.cricstudio.app.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cricstudio.app.R
import com.cricstudio.app.model.AutomationMode
import com.cricstudio.app.model.AutomationRules
import com.cricstudio.app.model.AutopilotSettings
import com.cricstudio.app.model.BannerKind
import com.cricstudio.app.model.Branding
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.SceneId
import com.cricstudio.app.model.StreamPlatform
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.model.Suggestion
import com.cricstudio.app.ui.AppUiState
import com.cricstudio.app.ui.theme.CricRed

private enum class ProducerTab(val label: String, val icon: ImageVector) {
    SCENES("Scenes", Icons.Rounded.GridView),
    AUTO("Auto & Events", Icons.Rounded.AutoAwesome),
    MATCH("Match", Icons.Rounded.SportsCricket),
    BRAND("Brand", Icons.Rounded.Palette),
    STREAM("Stream & Chat", Icons.Rounded.Cast),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    uiState: AppUiState,
    studio: StudioState,
    onLeaveStudio: () -> Unit,
    onToggleTheme: () -> Unit,
    onStartBroadcast: () -> Unit,
    onEndBroadcast: () -> Unit,
    onSelectScene: (SceneId) -> Unit,
    onSetAutomationMode: (AutomationMode) -> Unit,
    onUpdateRules: ((AutomationRules) -> AutomationRules) -> Unit,
    onUpdateAutopilot: ((AutopilotSettings) -> AutopilotSettings) -> Unit,
    onUpdateBranding: ((Branding) -> Branding) -> Unit,
    onToggleBanner: (BannerKind) -> Unit,
    onShowCustomOverlay: (title: String, subtitle: String?) -> Unit,
    onClearOverlay: () -> Unit,
    onSetCountdown: (Int?) -> Unit,
    onShowAnnouncement: (title: String, text: String) -> Unit,
    onToggleParticipantStage: (String) -> Unit,
    onToggleParticipantMute: (String) -> Unit,
    onSayCustomLine: (String) -> Unit,
    onSimulateEvent: (CricketEventType) -> Unit,
    onAcceptSuggestion: (Suggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onAttachMatch: (String?) -> Unit,
    onToggleDemoAutoPlay: () -> Unit,
    onStepDemo: (Int) -> Unit,
    onAddDestination: (StreamPlatform, String, String, String) -> Unit,
    onToggleDestination: (String) -> Unit,
    onDeleteDestination: (String) -> Unit,
    onSendChat: (String) -> Unit,
) {
    BackHandler(onBack = onLeaveStudio)

    var selectedTab by remember { mutableStateOf(ProducerTab.SCENES) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showCustomOverlayDialog by remember { mutableStateOf(false) }
    var showAddDestDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onLeaveStudio,
                        modifier = Modifier.testTag("leave_studio_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to dashboard")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = studio.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        uiState.matchDetail?.let { m ->
                            Text(
                                text = m.statusText.ifBlank { m.title },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (uiState.darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                    Button(
                        onClick = { if (studio.isLive) onEndBroadcast() else onStartBroadcast() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (studio.isLive) CricRed else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("go_live_button"),
                    ) {
                        Icon(
                            if (studio.isLive) Icons.Rounded.StopCircle else Icons.Rounded.RadioButtonChecked,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (studio.isLive) R.string.end_broadcast else R.string.go_live))
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val isWide = maxWidth >= 760.dp

            val producerDeck: @Composable (Modifier) -> Unit = { deckModifier ->
                Column(modifier = deckModifier) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        ProducerTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.label) },
                                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("studio_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            ProducerTab.SCENES -> ScenesProducerTab(
                                studio = studio,
                                onSelectScene = onSelectScene,
                                onSetCountdown = onSetCountdown,
                                onOpenAnnouncementDialog = { showAnnouncementDialog = true },
                                onOpenCustomOverlayDialog = { showCustomOverlayDialog = true },
                            )
                            ProducerTab.AUTO -> AutoDirectorTab(
                                uiState = uiState,
                                studio = studio,
                                onUpdateAutopilot = onUpdateAutopilot,
                                onSetAutomationMode = onSetAutomationMode,
                                onUpdateRules = onUpdateRules,
                                onAcceptSuggestion = onAcceptSuggestion,
                                onDismissSuggestion = onDismissSuggestion,
                                onSimulateEvent = onSimulateEvent,
                            )
                            ProducerTab.MATCH -> MatchSwitcherTab(
                                uiState = uiState,
                                studio = studio,
                                onAttachMatch = onAttachMatch,
                                onToggleDemoAutoPlay = onToggleDemoAutoPlay,
                                onStepDemo = onStepDemo,
                            )
                            ProducerTab.BRAND -> BrandAndStageTab(
                                studio = studio,
                                onUpdateBranding = onUpdateBranding,
                                onToggleBanner = onToggleBanner,
                                onToggleParticipantStage = onToggleParticipantStage,
                                onToggleParticipantMute = onToggleParticipantMute,
                            )
                            ProducerTab.STREAM -> StreamAndChatTab(
                                uiState = uiState,
                                onAddDestinationClick = { showAddDestDialog = true },
                                onToggleDestination = onToggleDestination,
                                onDeleteDestination = onDeleteDestination,
                                onSayCustomLine = onSayCustomLine,
                                onSendChat = onSendChat,
                            )
                        }
                    }
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BroadcastStageCanvas(
                            studio = studio,
                            match = uiState.matchDetail,
                            allMatches = uiState.matches,
                            commentary = uiState.commentary,
                            onClearOverlay = onClearOverlay,
                        )
                    }
                    producerDeck(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BroadcastStageCanvas(
                            studio = studio,
                            match = uiState.matchDetail,
                            allMatches = uiState.matches,
                            commentary = uiState.commentary,
                            onClearOverlay = onClearOverlay,
                        )
                    }
                    producerDeck(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAnnouncementDialog) {
        var title by remember { mutableStateOf(studio.announcement?.title ?: "STUDIO UPDATE") }
        var body by remember { mutableStateOf(studio.announcement?.text ?: "Live match analysis right after the break!") }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Show Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, maxLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShowAnnouncement(title, body)
                        showAnnouncementDialog = false
                    },
                    enabled = title.isNotBlank() || body.isNotBlank(),
                ) { Text("Show on air") }
            },
            dismissButton = { TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") } },
        )
    }

    if (showCustomOverlayDialog) {
        var title by remember { mutableStateOf("BREAKING NEWS") }
        var subtitle by remember { mutableStateOf("Subscribe and vote in the live fan poll!") }
        AlertDialog(
            onDismissRequest = { showCustomOverlayDialog = false },
            title = { Text("Custom Broadcast Overlay") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Subtitle") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShowCustomOverlay(title, subtitle)
                        showCustomOverlayDialog = false
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Fire overlay") }
            },
            dismissButton = { TextButton(onClick = { showCustomOverlayDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAddDestDialog) {
        var platform by remember { mutableStateOf(StreamPlatform.YOUTUBE) }
        var name by remember { mutableStateOf(StreamPlatform.YOUTUBE.label) }
        var url by remember { mutableStateOf(StreamPlatform.YOUTUBE.presetUrl) }
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDestDialog = false },
            title = { Text("Add Stream Destination") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(StreamPlatform.entries) { p ->
                            FilterChip(
                                selected = platform == p,
                                onClick = {
                                    platform = p
                                    name = p.label
                                    url = p.presetUrl
                                },
                                label = { Text(p.label) },
                            )
                        }
                    }
                    Text(platform.helpText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Label") }, singleLine = true)
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("RTMP URL") }, singleLine = true)
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Stream Key") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddDestination(platform, name, url, key)
                        showAddDestDialog = false
                    },
                    enabled = url.isNotBlank() && key.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDestDialog = false }) { Text("Cancel") } },
        )
    }
}
