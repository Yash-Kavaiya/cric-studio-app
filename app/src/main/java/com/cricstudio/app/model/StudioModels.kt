package com.cricstudio.app.model

import kotlinx.serialization.Serializable

enum class SceneGroup(val label: String) {
    SHOW("Show"),
    CAMERAS("Cameras"),
    SCORE("Score"),
    STATS("Stats"),
    INSIGHTS("Insights"),
    ANALYSIS("Analysis"),
    MATCH("Match"),
}

enum class SceneId(
    val displayName: String,
    val shortName: String,
    val group: SceneGroup,
    val usesCameras: Boolean = false,
    val autopilot: Boolean = true,
) {
    STARTING_SOON("Starting Soon", "Starting", SceneGroup.SHOW, false, false),
    INTRO_3D("Match Intro", "Intro", SceneGroup.SHOW, false, false),
    HOST_SOLO("Host Solo", "Solo", SceneGroup.CAMERAS, true, true),
    PANEL("Expert Panel", "Panel", SceneGroup.CAMERAS, true, true),
    COMMENTARY_DESK("Commentary Desk", "Desk", SceneGroup.CAMERAS, true, true),
    SCORE_HERO("Live Score", "Score", SceneGroup.SCORE, false, true),
    BALL_BY_BALL("Ball by Ball", "Balls", SceneGroup.SCORE, false, true),
    BATTING_CARD("Batting Scorecard", "Batting", SceneGroup.SCORE, false, true),
    BOWLING_CARD("Bowling Figures", "Bowling", SceneGroup.SCORE, false, true),
    BATTERS("At the Crease", "Crease", SceneGroup.STATS, false, true),
    BOWLER_SPOTLIGHT("Bowler Spotlight", "Bowler", SceneGroup.STATS, false, true),
    MANHATTAN("Manhattan", "Manhattan", SceneGroup.STATS, false, true),
    WORM("Worm", "Worm", SceneGroup.STATS, false, true),
    FALL_OF_WICKETS("Fall of Wickets", "FoW", SceneGroup.STATS, false, true),
    WIN_PREDICTOR("Win Predictor", "Win %", SceneGroup.INSIGHTS, false, true),
    PROJECTED_SCORE("Projected Score", "Projected", SceneGroup.INSIGHTS, false, true),
    PHASE_ANALYSIS("Phase Analysis", "Phases", SceneGroup.INSIGHTS, false, true),
    BOUNDARY_TRACKER("Boundary Tracker", "Boundaries", SceneGroup.INSIGHTS, false, true),
    MATCHUP("Batter vs Bowler", "Matchup", SceneGroup.INSIGHTS, false, true),
    WAGON_WHEEL("Wagon Wheel", "Wagon", SceneGroup.INSIGHTS, false, true),
    STAR_PERFORMER("Star Performer", "Star", SceneGroup.INSIGHTS, false, true),
    GROUND_3D("Ground Shot Map", "Shot Map", SceneGroup.INSIGHTS, false, true),
    KEY_MOMENTS("Key Moments", "Moments", SceneGroup.INSIGHTS, false, true),
    RUN_RATE_GRAPH("Run Rate Graph", "Run Rate", SceneGroup.ANALYSIS, false, true),
    OVER_HEATMAP("Over Heatmap", "Heatmap", SceneGroup.ANALYSIS, false, true),
    SCORING_BREAKDOWN("Scoring Breakdown", "Scoring", SceneGroup.ANALYSIS, false, true),
    PARTNERSHIPS("Partnerships", "Partners", SceneGroup.ANALYSIS, false, true),
    BATTING_IMPACT("Batting Impact", "Impact", SceneGroup.ANALYSIS, false, true),
    BOWLING_ANALYSIS("Bowling Analysis", "Bowl Stats", SceneGroup.ANALYSIS, false, true),
    DISMISSAL_ANALYSIS("How Wickets Fell", "Dismissals", SceneGroup.ANALYSIS, false, true),
    EXTRAS_DISCIPLINE("Extras & Discipline", "Extras", SceneGroup.ANALYSIS, false, true),
    MOMENTUM("Momentum Swing", "Momentum", SceneGroup.ANALYSIS, false, true),
    INNINGS_COMPARISON("At This Stage", "Compare", SceneGroup.ANALYSIS, false, true),
    CHASE_CALCULATOR("Chase Calculator", "Chase", SceneGroup.ANALYSIS, false, true),
    MATCH_REPORT("Match Report", "Report", SceneGroup.ANALYSIS, false, true),
    PLAYING_XI("Playing XIs & Toss", "XIs", SceneGroup.MATCH, false, true),
    AROUND_GROUNDS("Around the Grounds", "Grounds", SceneGroup.MATCH, false, true),
    UPCOMING_MATCHES("Upcoming Matches", "Upcoming", SceneGroup.MATCH, false, true),
    FAN_POLL("Fan Poll", "Poll", SceneGroup.MATCH, false, true),
    SCREEN_SHARE("Screen Share + PiP", "Screen", SceneGroup.CAMERAS, true, false),
    MESSAGE("Announcement", "Message", SceneGroup.SHOW, false, false),
    BREAK("Innings Break / BRB", "Break", SceneGroup.SHOW, false, false),
    RESULT("Result & Thanks", "Result", SceneGroup.SHOW, false, false),
}

enum class BackgroundGroup(val label: String) {
    BRAND("Design systems"),
    CRICKET("Cricket themes"),
    STUDIO("Studio backgrounds"),
}

enum class BackgroundId(
    val displayName: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val defaultAccentHex: Long,
    val group: BackgroundGroup,
    val isLight: Boolean = false,
) {
    STADIUM_NIGHT("Stadium Night", 0xFF050A1A, 0xFF1B2A52, 0xFF1A73E8, BackgroundGroup.STUDIO),
    PITCH_GREEN("Pitch Green", 0xFF06210F, 0xFF1F5D2C, 0xFF34A853, BackgroundGroup.STUDIO),
    LIQUID_WAVES("Liquid Waves", 0xFF06132B, 0xFF1A73E8, 0xFF1A73E8, BackgroundGroup.STUDIO),
    HEX_TECH("Hex Tech", 0xFF03121C, 0xFF0E7490, 0xFF12B5CB, BackgroundGroup.STUDIO),
    PRISM("Prism Glass", 0xFF120C24, 0xFF7B1FA2, 0xFFEA4335, BackgroundGroup.STUDIO),
    SEAM_LINES("Ball Seam", 0xFF2A0606, 0xFFB91C1C, 0xFFEA4335, BackgroundGroup.STUDIO),
    WORLD_CUP_23("World Cup '23", 0xFF1D0C58, 0xFFE6007E, 0xFFE6007E, BackgroundGroup.CRICKET),
    IPL_NIGHT("T20 League Night", 0xFF0B1A66, 0xFF1E3A8A, 0xFFFFB300, BackgroundGroup.CRICKET),
    LORDS_SCOREBOARD("Old Scoreboard", 0xFF143322, 0xFF204B33, 0xFFD9C98F, BackgroundGroup.CRICKET),
    BBL_NEON("Big Bash Neon", 0xFF07070D, 0xFF142814, 0xFF39FF14, BackgroundGroup.CRICKET),
    WTC_FINAL("Test Championship", 0xFF06142E, 0xFF142A4F, 0xFFD4AF37, BackgroundGroup.CRICKET),
    ASHES("The Ashes", 0xFF005B2B, 0xFF0B1F4B, 0xFFC8102E, BackgroundGroup.CRICKET),
    GOOGLE_MATERIAL("Google · Material", 0xFFF8FAFD, 0xFFE3EDFA, 0xFF0B57D0, BackgroundGroup.BRAND, true),
    MICROSOFT_FLUENT("Microsoft · Fluent", 0xFF0B0F1A, 0xFF162442, 0xFF0078D4, BackgroundGroup.BRAND),
    CLAUDE_PAPER("Claude · Paper", 0xFFF0EEE6, 0xFFE3DDD0, 0xFFD97757, BackgroundGroup.BRAND, true),
    X_MONO("X · Mono", 0xFF000000, 0xFF16181C, 0xFF1D9BF0, BackgroundGroup.BRAND),
}

enum class AutomationMode(val label: String) {
    AUTO("Auto"),
    ASSIST("Assist"),
    MANUAL("Manual"),
}

enum class OverlayId {
    FOUR,
    SIX,
    WICKET,
    MILESTONE,
    FIVE_WICKETS,
    PARTNERSHIP,
    OVER_SUMMARY,
    HATTRICK,
    INNINGS_END,
    MATCH_END,
    CUSTOM,
}

data class ActiveOverlay(
    val id: OverlayId,
    val title: String,
    val subtitle: String? = null,
    val value: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val until: Long = System.currentTimeMillis() + 5500L,
    val seq: Int = 0,
    val priority: Int = 1,
)

data class AutomationRules(
    val wicket: Boolean = true,
    val boundary: Boolean = true,
    val overEnd: Boolean = true,
    val milestones: Boolean = true,
    val inningsBreak: Boolean = true,
)

data class AutopilotSettings(
    val enabled: Boolean = false,
    val sceneSeconds: Int = 18,
    val scoreShare: Float = 0.7f,
    val includeCameras: Boolean = false,
    val commentator: Boolean = true,
    val speakTts: Boolean = false,
    val chatterSeconds: Int = 25,
)

enum class BannerKind { SPONSOR, QUICK_SCORE }

data class ActiveBanner(
    val kind: BannerKind,
    val since: Long = System.currentTimeMillis(),
)

data class Branding(
    val title: String = "CricStudio Live",
    val ticker: String = "Live cricket scores, stats & ball-by-ball analysis — drop your predictions in chat!",
    val accentHex: Long = 0xFF1A73E8,
    val showScorebug: Boolean = true,
    val showTicker: Boolean = true,
    val captions: Boolean = true,
    val background: BackgroundId = BackgroundId.STADIUM_NIGHT,
    val pollQuestion: String = "Who wins this match?",
    val sponsors: List<String> = listOf("Powered by CricStudio Broadcast", "Subscribe for live ball-by-ball coverage"),
)

data class Announcement(
    val title: String,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
)

data class Suggestion(
    val id: String,
    val scene: SceneId? = null,
    val overlay: OverlayId? = null,
    val reason: String,
    val event: CricketEvent,
)

data class CommentaryLine(
    val id: String,
    val text: String,
    val priority: Int = 1,
    val ts: Long = System.currentTimeMillis(),
)

data class ChatMessage(
    val id: String,
    val from: String,
    val role: String,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
enum class StreamPlatform(
    val label: String,
    val presetUrl: String,
    val colorHex: Long,
    val helpText: String,
) {
    YOUTUBE(
        "YouTube",
        "rtmp://a.rtmp.youtube.com/live2",
        0xFFFF0000,
        "YouTube Studio -> Go live -> Stream -> copy Stream key.",
    ),
    FACEBOOK(
        "Facebook",
        "rtmps://live-api-s.facebook.com:443/rtmp/",
        0xFF1877F2,
        "Facebook -> Live Producer -> Streaming software -> copy Stream key.",
    ),
    X(
        "X (Twitter)",
        "rtmps://va.pscp.tv:443/x",
        0xFF111111,
        "X Media Studio -> Producer -> Sources -> copy RTMP URL + key.",
    ),
    TWITCH(
        "Twitch",
        "rtmp://live.twitch.tv/app",
        0xFF9146FF,
        "Twitch Creator Dashboard -> Settings -> Stream -> copy Primary Stream key.",
    ),
    CUSTOM(
        "Custom RTMP",
        "rtmp://",
        0xFF5F6368,
        "Any RTMP/RTMPS ingest (Kick, LinkedIn, Restream, or your own server).",
    ),
}

@Serializable
data class StreamDestination(
    val id: String,
    val platform: StreamPlatform,
    val name: String,
    val url: String,
    val keyMasked: String,
    val streamKey: String = "",
    val enabled: Boolean = true,
)

enum class StreamQuality(
    val width: Int,
    val height: Int,
    val videoBitrateBps: Int,
    val fps: Int,
    val label: String,
) {
    HD_720P(1280, 720, 3_000_000, 30, "720p30 · 3.0 Mbps"),
    SD_480P(854, 480, 1_500_000, 30, "480p30 · 1.5 Mbps"),
}

enum class BroadcastStatus(val label: String) {
    IDLE("PREVIEW"),
    CONNECTING("CONNECTING"),
    LIVE("ON AIR"),
    RECONNECTING("RECONNECTING"),
    ERROR("STREAM ERROR"),
}

data class DestinationStatus(
    val destinationId: String,
    val name: String,
    val status: BroadcastStatus = BroadcastStatus.IDLE,
    val message: String = "Ready",
    val bitrateKbps: Int = 0,
)

data class StreamTelemetry(
    val status: BroadcastStatus = BroadcastStatus.IDLE,
    val bitrateKbps: Int = 0,
    val fps: Int = 0,
    val droppedFrames: Int = 0,
    val elapsedSeconds: Long = 0L,
    val micEnabled: Boolean = true,
    val micLevel: Float = 0f,
    val quality: StreamQuality = StreamQuality.HD_720P,
    val destinationStatuses: Map<String, DestinationStatus> = emptyMap(),
    val lastError: String? = null,
    val logs: List<String> = emptyList(),
)

data class CompositorSnapshot(
    val studio: StudioState,
    val match: MatchDetail?,
    val allMatches: List<MatchListItem>,
    val commentary: CommentaryLine?,
    val sceneChangedAt: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val micLevel: Float = 0f,
)

@Serializable
data class SavedStudio(
    val id: String,
    val name: String,
    val matchKey: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

data class StageParticipant(
    val id: String,
    val name: String,
    val role: String,
    val onStage: Boolean,
    val muted: Boolean,
    val camOff: Boolean,
)

data class StudioState(
    val id: String,
    val name: String,
    val matchKey: String? = null,
    val scene: SceneId = SceneId.SCORE_HERO,
    val baseScene: SceneId = SceneId.SCORE_HERO,
    val sceneUntil: Long? = null,
    val overlay: ActiveOverlay? = null,
    val automation: AutomationMode = AutomationMode.AUTO,
    val rules: AutomationRules = AutomationRules(),
    val autopilot: AutopilotSettings = AutopilotSettings(),
    val brand: Branding = Branding(),
    val banner: ActiveBanner? = null,
    val announcement: Announcement? = null,
    val countdownTo: Long? = null,
    val isLive: Boolean = false,
    val liveStartedAt: Long? = null,
    val participants: List<StageParticipant> = listOf(
        StageParticipant("host-1", "Host", "host", onStage = true, muted = false, camOff = false),
        StageParticipant("guest-1", "Expert 1", "guest", onStage = true, muted = false, camOff = false),
        StageParticipant("guest-2", "Expert 2", "guest", onStage = false, muted = true, camOff = false),
    ),
)

val ACCENT_SWATCHES: List<Long> = listOf(
    0xFF1A73E8,
    0xFFEA4335,
    0xFF34A853,
    0xFFF9AB00,
    0xFFA142F4,
    0xFF12B5CB,
    0xFFE8710A,
    0xFFD01884,
)
