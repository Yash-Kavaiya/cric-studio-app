package com.cricstudio.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cricstudio.app.engine.CommentatorEngine
import com.cricstudio.app.engine.DemoReplayEngine
import com.cricstudio.app.engine.EspnClient
import com.cricstudio.app.engine.EspnNormalizer
import com.cricstudio.app.engine.EventDetector
import com.cricstudio.app.engine.StudioStorage
import com.cricstudio.app.model.ActiveBanner
import com.cricstudio.app.model.ActiveOverlay
import com.cricstudio.app.model.Announcement
import com.cricstudio.app.model.AutomationMode
import com.cricstudio.app.model.AutomationRules
import com.cricstudio.app.model.AutopilotSettings
import com.cricstudio.app.model.BannerKind
import com.cricstudio.app.model.Branding
import com.cricstudio.app.model.ChatMessage
import com.cricstudio.app.model.CommentaryLine
import com.cricstudio.app.model.CricketEvent
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.DEMO_MATCH_KEY
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.OverlayId
import com.cricstudio.app.model.SavedStudio
import com.cricstudio.app.model.SceneGroup
import com.cricstudio.app.model.SceneId
import com.cricstudio.app.model.StreamDestination
import com.cricstudio.app.model.StreamPlatform
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.model.Suggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AppUiState(
    val darkTheme: Boolean = true,
    val matches: List<MatchListItem> = emptyList(),
    val loadingMatches: Boolean = true,
    val matchesError: String? = null,
    val savedStudios: List<SavedStudio> = emptyList(),
    val studio: StudioState? = null,
    val matchDetail: MatchDetail? = null,
    val loadingMatchDetail: Boolean = false,
    val demoAutoPlay: Boolean = true,
    val events: List<CricketEvent> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val commentary: CommentaryLine? = null,
    val commentaryHistory: List<CommentaryLine> = emptyList(),
    val destinations: List<StreamDestination> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val toast: String? = null,
)

class CricStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val espn = EspnClient()
    private val storage = StudioStorage(application)
    private val commentator = CommentatorEngine(application)
    private var demoEngine: DemoReplayEngine? = null
    private var detector: EventDetector? = null
    private var overlaySeq = 0
    private var lastSceneChangeMs = System.currentTimeMillis()
    private var lastChatterMs = 0L
    private var autopilotStep = 0

    private var matchPollJob: Job? = null
    private var demoLoopJob: Job? = null
    private var studioTickJob: Job? = null

    private val _uiState = MutableStateFlow(
        AppUiState(
            darkTheme = storage.isDarkTheme(),
            savedStudios = storage.getSavedStudios(),
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            demoEngine = withContext(Dispatchers.IO) {
                runCatching { DemoReplayEngine(application.assets, startAt = 82) }.getOrNull()
            }
            refreshMatches()
            startMatchesPolling()
        }
    }

    fun toggleTheme() {
        val next = !_uiState.value.darkTheme
        storage.setDarkTheme(next)
        _uiState.update { it.copy(darkTheme = next) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }

    private fun startMatchesPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                refreshMatches(silent = true)
            }
        }
    }

    fun refreshMatches(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(loadingMatches = true) }
            val demoItem = demoEngine?.listItem()
            try {
                val raw = espn.scorepanel()
                val liveList = EspnNormalizer.normalizeScorepanel(raw)
                val combined = buildList {
                    if (demoItem != null) add(demoItem)
                    addAll(liveList)
                }
                _uiState.update {
                    it.copy(
                        matches = combined,
                        loadingMatches = false,
                        matchesError = null,
                    )
                }
            } catch (e: Exception) {
                val fallback = listOfNotNull(demoItem)
                _uiState.update {
                    it.copy(
                        matches = fallback,
                        loadingMatches = false,
                        matchesError = e.message ?: "Unable to reach ESPN scorepanel",
                    )
                }
            }
        }
    }

    fun createAndOpenStudio(name: String, matchKey: String?): SavedStudio {
        val id = UUID.randomUUID().toString().take(8)
        val studio = SavedStudio(id = id, name = name.trim().ifEmpty { "Cricket Live Show" }, matchKey = matchKey)
        val saved = storage.saveStudio(studio)
        _uiState.update { it.copy(savedStudios = saved) }
        openStudio(studio)
        return studio
    }

    fun deleteSavedStudio(studioId: String) {
        val saved = storage.removeStudio(studioId)
        _uiState.update { it.copy(savedStudios = saved) }
    }

    fun openStudio(saved: SavedStudio) {
        closeStudioLoops()
        val initialDests = storage.getDestinations(saved.id)
        val initialState = StudioState(
            id = saved.id,
            name = saved.name,
            matchKey = saved.matchKey,
            scene = if (saved.matchKey != null) SceneId.SCORE_HERO else SceneId.STARTING_SOON,
            baseScene = if (saved.matchKey != null) SceneId.SCORE_HERO else SceneId.STARTING_SOON,
            brand = Branding(title = saved.name),
        )
        lastSceneChangeMs = System.currentTimeMillis()
        lastChatterMs = 0L
        _uiState.update {
            it.copy(
                studio = initialState,
                matchDetail = null,
                events = emptyList(),
                suggestions = emptyList(),
                commentary = null,
                commentaryHistory = emptyList(),
                destinations = initialDests,
                chatMessages = listOf(
                    ChatMessage(
                        id = "welcome-1",
                        from = "CricStudio",
                        role = "system",
                        text = "Studio ready. Switch scenes, fire rehearsal moments, or enable Auto Live.",
                    )
                ),
            )
        }
        attachMatch(saved.matchKey)
        startStudioTicker()
    }

    fun leaveStudio() {
        closeStudioLoops()
        commentator.stopVoice()
        _uiState.update { it.copy(studio = null, matchDetail = null) }
    }

    private fun closeStudioLoops() {
        matchPollJob?.cancel()
        demoLoopJob?.cancel()
        studioTickJob?.cancel()
    }

    fun attachMatch(matchKey: String?) {
        matchPollJob?.cancel()
        demoLoopJob?.cancel()
        detector = matchKey?.let { EventDetector(it) }
        _uiState.update { st ->
            st.copy(
                studio = st.studio?.copy(matchKey = matchKey),
                matchDetail = null,
                loadingMatchDetail = matchKey != null,
            )
        }
        if (matchKey == null) return

        if (matchKey == DEMO_MATCH_KEY) {
            val demo = demoEngine ?: return
            val detail = demo.detail()
            detector?.prime(demo.balls(), detail.state)
            _uiState.update { it.copy(matchDetail = detail, loadingMatchDetail = false) }
            startDemoLoop()
        } else {
            val parts = matchKey.split(":")
            if (parts.size == 2) {
                startEspnMatchPolling(parts[0], parts[1])
            }
        }
    }

    private fun startDemoLoop() {
        demoLoopJob?.cancel()
        demoLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(4_500L)
                if (_uiState.value.demoAutoPlay && _uiState.value.studio?.matchKey == DEMO_MATCH_KEY) {
                    stepDemo(1)
                }
            }
        }
    }

    fun toggleDemoAutoPlay() {
        _uiState.update { it.copy(demoAutoPlay = !it.demoAutoPlay) }
    }

    fun stepDemo(count: Int) {
        val demo = demoEngine ?: return
        demo.step(count)
        val detail = demo.detail()
        val det = detector ?: EventDetector(DEMO_MATCH_KEY).also { detector = it }
        val newEvents = det.process(demo.balls(), detail.state)
        _uiState.update { st ->
            val updatedMatches = st.matches.map { if (it.isDemo) demo.listItem() else it }
            st.copy(matchDetail = detail, matches = updatedMatches)
        }
        if (newEvents.isNotEmpty()) handleCricketEvents(newEvents)
    }

    private fun startEspnMatchPolling(leagueId: String, eventId: String) {
        matchPollJob?.cancel()
        matchPollJob = viewModelScope.launch {
            var allBalls = emptyList<com.cricstudio.app.model.Ball>()
            while (isActive) {
                try {
                    val summaryJson = espn.summary(leagueId, eventId)
                    val pbpJson = runCatching { espn.playByPlay(leagueId, eventId, 1) }.getOrNull()
                    if (pbpJson != null) {
                        val parsed = EspnNormalizer.normalizeBalls(pbpJson)
                        if (parsed.isNotEmpty()) {
                            val merged = (allBalls + parsed).associateBy { it.seq }.values.sortedBy { it.seq }
                            allBalls = merged
                        }
                    }
                    val detail = EspnNormalizer.buildMatchDetail(leagueId, eventId, summaryJson, allBalls)
                    val det = detector ?: EventDetector("$leagueId:$eventId").also { detector = it }
                    val newEvents = det.process(allBalls, detail.state)
                    _uiState.update { it.copy(matchDetail = detail, loadingMatchDetail = false) }
                    if (newEvents.isNotEmpty()) handleCricketEvents(newEvents)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            loadingMatchDetail = false,
                            toast = "Match sync issue: ${e.message ?: "network error"}",
                        )
                    }
                }
                delay(10_000L)
            }
        }
    }

    private fun startStudioTicker() {
        studioTickJob?.cancel()
        studioTickJob = viewModelScope.launch {
            while (isActive) {
                delay(500L)
                tickStudio()
            }
        }
    }

    private fun tickStudio() {
        val current = _uiState.value
        val st = current.studio ?: return
        val now = System.currentTimeMillis()
        var updated = st

        if (updated.overlay != null && updated.overlay!!.until <= now) {
            updated = updated.copy(overlay = null)
        }
        if (updated.sceneUntil != null && updated.sceneUntil!! <= now) {
            updated = updated.copy(
                scene = if (updated.autopilot.enabled) updated.scene else updated.baseScene,
                sceneUntil = null,
            )
            if (updated.autopilot.enabled) lastSceneChangeMs = 0L
        }

        if (updated.autopilot.enabled) {
            val holdMs = updated.autopilot.sceneSeconds * 1000L
            val busy = updated.sceneUntil != null || (updated.overlay != null && updated.overlay!!.priority >= 4)
            if (!busy && now - lastSceneChangeMs >= holdMs) {
                val nextScene = pickNextAutopilotScene(updated, current.matchDetail)
                if (nextScene != null) {
                    updated = updated.copy(scene = nextScene, baseScene = nextScene)
                    lastSceneChangeMs = now
                }
            }
            if (updated.autopilot.commentator) {
                val chatterMs = updated.autopilot.chatterSeconds * 1000L
                if (now - lastChatterMs >= chatterMs) {
                    emitChatter(updated, current.matchDetail)
                }
            }
        }

        if (updated != st) {
            _uiState.update { it.copy(studio = updated) }
        }
    }

    private fun pickNextAutopilotScene(st: StudioState, match: MatchDetail?): SceneId? {
        val scoreScenes = listOf(SceneId.SCORE_HERO, SceneId.BALL_BY_BALL, SceneId.BATTING_CARD, SceneId.BOWLING_CARD)
        val dataScenes = listOf(
            SceneId.BATTERS,
            SceneId.MANHATTAN,
            SceneId.WORM,
            SceneId.WIN_PREDICTOR,
            SceneId.PROJECTED_SCORE,
            SceneId.PHASE_ANALYSIS,
            SceneId.WAGON_WHEEL,
            SceneId.RUN_RATE_GRAPH,
            SceneId.OVER_HEATMAP,
            SceneId.SCORING_BREAKDOWN,
            SceneId.MOMENTUM,
            SceneId.AROUND_GROUNDS,
            SceneId.FAN_POLL,
        )
        val camScenes = listOf(SceneId.HOST_SOLO, SceneId.PANEL, SceneId.COMMENTARY_DESK)
        if (match == null) {
            val pool = listOf(SceneId.AROUND_GROUNDS, SceneId.UPCOMING_MATCHES, SceneId.FAN_POLL)
            return pool[autopilotStep++ % pool.size]
        }
        autopilotStep++
        val pickScore = (autopilotStep % 10) < (st.autopilot.scoreShare * 10).toInt().coerceIn(1, 9)
        val pool = when {
            pickScore && st.autopilot.includeCameras && autopilotStep % 3 == 0 -> camScenes
            pickScore -> scoreScenes
            else -> dataScenes
        }
        return pool.filter { it != st.scene }.randomOrNull() ?: pool.first()
    }

    private fun emitChatter(st: StudioState, match: MatchDetail?) {
        val line = commentator.chatterLine(match, st.brand.pollQuestion)
        lastChatterMs = System.currentTimeMillis()
        commentator.speakIfEnabled(line.text, st.autopilot.speakTts)
        _uiState.update {
            it.copy(
                commentary = line,
                commentaryHistory = (listOf(line) + it.commentaryHistory).take(20),
            )
        }
    }

    fun setScene(scene: SceneId) {
        lastSceneChangeMs = System.currentTimeMillis()
        _uiState.update { st ->
            st.copy(studio = st.studio?.copy(scene = scene, baseScene = scene, sceneUntil = null))
        }
    }

    fun setAutomationMode(mode: AutomationMode) {
        _uiState.update { st -> st.copy(studio = st.studio?.copy(automation = mode)) }
    }

    fun updateRules(transform: (AutomationRules) -> AutomationRules) {
        _uiState.update { st ->
            st.copy(studio = st.studio?.copy(rules = transform(st.studio.rules)))
        }
    }

    fun updateAutopilot(transform: (AutopilotSettings) -> AutopilotSettings) {
        _uiState.update { st ->
            val current = st.studio ?: return@update st
            val next = transform(current.autopilot)
            if (!next.speakTts) commentator.stopVoice()
            st.copy(studio = current.copy(autopilot = next))
        }
    }

    fun updateBranding(transform: (Branding) -> Branding) {
        _uiState.update { st ->
            st.copy(studio = st.studio?.copy(brand = transform(st.studio.brand)))
        }
    }

    fun toggleBanner(kind: BannerKind) {
        _uiState.update { st ->
            val current = st.studio ?: return@update st
            val next = if (current.banner?.kind == kind) null else ActiveBanner(kind)
            st.copy(studio = current.copy(banner = next))
        }
    }

    fun showCustomOverlay(title: String, subtitle: String?, durationMs: Long = 8000L) {
        val now = System.currentTimeMillis()
        val ov = ActiveOverlay(
            id = OverlayId.CUSTOM,
            title = title,
            subtitle = subtitle?.ifBlank { null },
            startedAt = now,
            until = now + durationMs,
            seq = ++overlaySeq,
            priority = 3,
        )
        _uiState.update { st -> st.copy(studio = st.studio?.copy(overlay = ov)) }
    }

    fun clearOverlay() {
        _uiState.update { st -> st.copy(studio = st.studio?.copy(overlay = null)) }
    }

    fun setCountdownMinutes(minutes: Int?) {
        val target = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        _uiState.update { st ->
            val nextScene = if (target != null) SceneId.STARTING_SOON else st.studio?.scene ?: SceneId.SCORE_HERO
            st.copy(studio = st.studio?.copy(countdownTo = target, scene = nextScene, baseScene = nextScene))
        }
    }

    fun showAnnouncement(title: String, text: String) {
        _uiState.update { st ->
            st.copy(
                studio = st.studio?.copy(
                    announcement = Announcement(title.trim(), text.trim()),
                    scene = SceneId.MESSAGE,
                    baseScene = SceneId.MESSAGE,
                )
            )
        }
    }

    fun toggleParticipantStage(id: String) {
        _uiState.update { st ->
            val current = st.studio ?: return@update st
            val list = current.participants.map { p ->
                if (p.id == id) p.copy(onStage = !p.onStage) else p
            }
            st.copy(studio = current.copy(participants = list))
        }
    }

    fun toggleParticipantMute(id: String) {
        _uiState.update { st ->
            val current = st.studio ?: return@update st
            val list = current.participants.map { p ->
                if (p.id == id) p.copy(muted = !p.muted) else p
            }
            st.copy(studio = current.copy(participants = list))
        }
    }

    fun sayCustomLine(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val st = _uiState.value.studio ?: return
        val line = CommentaryLine(id = "manual-${System.currentTimeMillis()}", text = trimmed, priority = 5)
        lastChatterMs = System.currentTimeMillis()
        commentator.speakIfEnabled(trimmed, st.autopilot.speakTts)
        _uiState.update {
            it.copy(
                commentary = line,
                commentaryHistory = (listOf(line) + it.commentaryHistory).take(20),
            )
        }
    }

    fun simulateEvent(type: CricketEventType) {
        val match = _uiState.value.matchDetail
        val batter = match?.currentBatters?.firstOrNull()?.name ?: "Suryakumar Yadav"
        val bowler = match?.currentBowler?.name ?: "Alzarri Joseph"
        val team = match?.innings?.firstOrNull { it.isCurrent }?.teamAbbr ?: "IND"
        val ev = when (type) {
            CricketEventType.FOUR -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "FOUR!", subtitle = "$batter creams it through covers!", player = batter)
            CricketEventType.SIX -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "SIX!", subtitle = "$batter launches it into the stands!", player = batter)
            CricketEventType.WICKET -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "WICKET!", subtitle = "c Keeper b $bowler · $team in trouble", player = batter)
            CricketEventType.MILESTONE_BAT -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "FIFTY! $batter", subtitle = "52 off 31 balls · 5x4 · 3x6", value = 50, player = batter)
            CricketEventType.MILESTONE_BOWL -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "FIVE-WICKET HAUL! $bowler", subtitle = "5/24 in 4.0 overs", value = 5, player = bowler)
            CricketEventType.PARTNERSHIP -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "50 PARTNERSHIP", subtitle = "$batter & partner", value = 50)
            CricketEventType.HATTRICK_WATCH -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "ON A HAT-TRICK!", subtitle = "$bowler — two in two!", player = bowler)
            CricketEventType.OVER_END -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "End of over", subtitle = "11 runs · $team building momentum", value = 10)
            CricketEventType.INNINGS_END -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "Innings Break", subtitle = "First innings complete", value = 1)
            CricketEventType.MATCH_END -> CricketEvent(type, match?.key ?: DEMO_MATCH_KEY, title = "Match Over", subtitle = match?.summary ?: "What a finish!")
            else -> return
        }
        applyEventToStudio(ev, forceCut = true)
    }

    private fun handleCricketEvents(events: List<CricketEvent>) {
        val st = _uiState.value.studio ?: return
        val match = _uiState.value.matchDetail
        val notable = events.filter { it.type != CricketEventType.BALL }
        _uiState.update { it.copy(events = (notable.reversed() + it.events).take(30)) }

        if (st.autopilot.commentator) {
            notable.lastOrNull()?.let { ev ->
                commentator.lineForEvent(ev, match)?.let { line ->
                    lastChatterMs = System.currentTimeMillis()
                    commentator.speakIfEnabled(line.text, st.autopilot.speakTts)
                    _uiState.update {
                        it.copy(
                            commentary = line,
                            commentaryHistory = (listOf(line) + it.commentaryHistory).take(20),
                        )
                    }
                }
            }
        }

        val effectiveMode = if (st.autopilot.enabled) AutomationMode.AUTO else st.automation
        if (effectiveMode == AutomationMode.MANUAL) return

        for (ev in notable) {
            val plan = planFor(ev, st.rules) ?: continue
            if (effectiveMode == AutomationMode.ASSIST) {
                val sug = Suggestion(
                    id = "${ev.type}-${ev.ts}-${overlaySeq++}",
                    scene = plan.baseScene ?: plan.tempScene,
                    overlay = plan.overlay,
                    reason = listOfNotNull(ev.title, ev.subtitle?.takeIf { it.isNotBlank() }).joinToString(" — "),
                    event = ev,
                )
                _uiState.update { it.copy(suggestions = (listOf(sug) + it.suggestions).take(6)) }
            } else {
                applyEventToStudio(ev, forceCut = st.autopilot.enabled)
            }
        }
    }

    fun acceptSuggestion(s: Suggestion) {
        dismissSuggestion(s.id)
        applyEventToStudio(s.event, forceCut = true)
    }

    fun dismissSuggestion(id: String) {
        _uiState.update { it.copy(suggestions = it.suggestions.filterNot { x -> x.id == id }) }
    }

    private data class EventPlan(
        val overlay: OverlayId,
        val ms: Long,
        val priority: Int,
        val tempScene: SceneId? = null,
        val tempMs: Long = 0L,
        val baseScene: SceneId? = null,
    )

    private fun planFor(ev: CricketEvent, r: AutomationRules): EventPlan? = when (ev.type) {
        CricketEventType.FOUR -> if (r.boundary) EventPlan(OverlayId.FOUR, 4500L, 2) else null
        CricketEventType.SIX -> if (r.boundary) EventPlan(OverlayId.SIX, 5000L, 3) else null
        CricketEventType.WICKET -> if (r.wicket) EventPlan(OverlayId.WICKET, 5500L, 5, SceneId.BATTING_CARD, 9000L) else null
        CricketEventType.MILESTONE_BAT -> if (r.milestones) EventPlan(OverlayId.MILESTONE, 6000L, 4, SceneId.STAR_PERFORMER, 8000L) else null
        CricketEventType.MILESTONE_BOWL -> if (r.milestones) EventPlan(OverlayId.FIVE_WICKETS, 6000L, 4, SceneId.BOWLER_SPOTLIGHT, 8000L) else null
        CricketEventType.PARTNERSHIP -> if (r.milestones) EventPlan(OverlayId.PARTNERSHIP, 4500L, 2, SceneId.BATTERS, 7000L) else null
        CricketEventType.HATTRICK_WATCH -> if (r.wicket) EventPlan(OverlayId.HATTRICK, 5500L, 4) else null
        CricketEventType.OVER_END -> if (r.overEnd) {
            val scene = if ((ev.value ?: 0) % 5 == 0) SceneId.MANHATTAN else SceneId.BALL_BY_BALL
            EventPlan(OverlayId.OVER_SUMMARY, 5500L, 1, scene, 7000L)
        } else null
        CricketEventType.INNINGS_END -> if (r.inningsBreak) EventPlan(OverlayId.INNINGS_END, 5500L, 5, baseScene = SceneId.BREAK) else null
        CricketEventType.MATCH_END -> if (r.inningsBreak) EventPlan(OverlayId.MATCH_END, 6500L, 6, baseScene = SceneId.RESULT) else null
        else -> null
    }

    private fun applyEventToStudio(ev: CricketEvent, forceCut: Boolean) {
        val st = _uiState.value.studio ?: return
        val plan = planFor(ev, st.rules) ?: return
        val now = System.currentTimeMillis()
        val ov = ActiveOverlay(
            id = plan.overlay,
            title = ev.title,
            subtitle = ev.subtitle,
            value = ev.value,
            startedAt = now,
            until = now + plan.ms,
            seq = ++overlaySeq,
            priority = plan.priority,
        )
        var next = st.copy(overlay = ov)
        if (forceCut) {
            when {
                plan.baseScene != null -> {
                    next = next.copy(scene = plan.baseScene, baseScene = plan.baseScene, sceneUntil = null)
                    lastSceneChangeMs = now
                }
                plan.tempScene != null -> {
                    next = next.copy(scene = plan.tempScene, sceneUntil = now + plan.ms + plan.tempMs)
                    lastSceneChangeMs = now
                }
            }
        }
        val line = commentator.lineForEvent(ev, _uiState.value.matchDetail)
        if (line != null && st.autopilot.commentator) {
            commentator.speakIfEnabled(line.text, st.autopilot.speakTts)
        }
        _uiState.update {
            it.copy(
                studio = next,
                events = (listOf(ev) + it.events).take(30),
                commentary = line ?: it.commentary,
                commentaryHistory = if (line != null) (listOf(line) + it.commentaryHistory).take(20) else it.commentaryHistory,
            )
        }
    }

    fun addDestination(platform: StreamPlatform, name: String, url: String, rawKey: String) {
        val studioId = _uiState.value.studio?.id ?: return
        val masked = if (rawKey.length <= 4) "••••" else "••••${rawKey.takeLast(4)}"
        val dest = StreamDestination(
            id = UUID.randomUUID().toString().take(8),
            platform = platform,
            name = name.trim().ifEmpty { platform.label },
            url = url.trim(),
            keyMasked = masked,
            enabled = true,
        )
        val updated = _uiState.value.destinations + dest
        storage.saveDestinations(studioId, updated)
        _uiState.update { it.copy(destinations = updated) }
    }

    fun toggleDestination(destId: String) {
        val studioId = _uiState.value.studio?.id ?: return
        val updated = _uiState.value.destinations.map {
            if (it.id == destId) it.copy(enabled = !it.enabled) else it
        }
        storage.saveDestinations(studioId, updated)
        _uiState.update { it.copy(destinations = updated) }
    }

    fun deleteDestination(destId: String) {
        val studioId = _uiState.value.studio?.id ?: return
        val updated = _uiState.value.destinations.filterNot { it.id == destId }
        storage.saveDestinations(studioId, updated)
        _uiState.update { it.copy(destinations = updated) }
    }

    fun startBroadcast() {
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                studio = it.studio?.copy(isLive = true, liveStartedAt = now),
                toast = "Broadcast preview ON AIR (local studio mode — RTMP relay requires server encoder)",
            )
        }
    }

    fun endBroadcast() {
        _uiState.update {
            it.copy(
                studio = it.studio?.copy(isLive = false, liveStartedAt = null),
                toast = "Broadcast ended",
            )
        }
    }

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val msg = ChatMessage(
            id = "m-${System.currentTimeMillis()}",
            from = "Host",
            role = "host",
            text = trimmed,
        )
        _uiState.update { it.copy(chatMessages = (it.chatMessages + msg).takeLast(100)) }
    }

    override fun onCleared() {
        super.onCleared()
        closeStudioLoops()
        commentator.shutdown()
    }
}
