package com.cricstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricstudio.app.ui.CricStudioViewModel
import com.cricstudio.app.ui.dashboard.DashboardScreen
import com.cricstudio.app.ui.studio.StudioScreen
import com.cricstudio.app.ui.theme.CricStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CricStudioViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(uiState.toast) {
                val msg = uiState.toast
                if (msg != null) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearToast()
                }
            }

            CricStudioTheme(darkTheme = uiState.darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val activeStudio = uiState.studio
                    if (activeStudio == null) {
                        DashboardScreen(
                            uiState = uiState,
                            onToggleTheme = viewModel::toggleTheme,
                            onRefresh = { viewModel.refreshMatches(silent = false) },
                            onCreateStudio = { name, matchKey ->
                                viewModel.createAndOpenStudio(name, matchKey)
                            },
                            onOpenSavedStudio = viewModel::openStudio,
                            onDeleteSavedStudio = viewModel::deleteSavedStudio,
                        )
                    } else {
                        StudioScreen(
                            uiState = uiState,
                            studio = activeStudio,
                            onLeaveStudio = viewModel::leaveStudio,
                            onToggleTheme = viewModel::toggleTheme,
                            onStartBroadcast = viewModel::startBroadcast,
                            onEndBroadcast = viewModel::endBroadcast,
                            onSelectScene = viewModel::setScene,
                            onSetAutomationMode = viewModel::setAutomationMode,
                            onUpdateRules = viewModel::updateRules,
                            onUpdateAutopilot = viewModel::updateAutopilot,
                            onUpdateBranding = viewModel::updateBranding,
                            onToggleBanner = viewModel::toggleBanner,
                            onShowCustomOverlay = { title, subtitle ->
                                viewModel.showCustomOverlay(title, subtitle)
                            },
                            onClearOverlay = viewModel::clearOverlay,
                            onSetCountdown = viewModel::setCountdownMinutes,
                            onShowAnnouncement = viewModel::showAnnouncement,
                            onToggleParticipantStage = viewModel::toggleParticipantStage,
                            onToggleParticipantMute = viewModel::toggleParticipantMute,
                            onSayCustomLine = viewModel::sayCustomLine,
                            onSimulateEvent = viewModel::simulateEvent,
                            onAcceptSuggestion = viewModel::acceptSuggestion,
                            onDismissSuggestion = viewModel::dismissSuggestion,
                            onAttachMatch = viewModel::attachMatch,
                            onToggleDemoAutoPlay = viewModel::toggleDemoAutoPlay,
                            onStepDemo = viewModel::stepDemo,
                            onAddDestination = viewModel::addDestination,
                            onToggleDestination = viewModel::toggleDestination,
                            onDeleteDestination = viewModel::deleteDestination,
                            onSendChat = viewModel::sendChat,
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}
