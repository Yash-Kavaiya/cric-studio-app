package com.cricstudio.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class MatchStateCode { PRE, IN, POST }

@Serializable
data class MatchListTeam(
    val id: String,
    val name: String,
    val abbr: String,
    val logo: String? = null,
    val score: String = "",
    val winner: Boolean = false,
)

@Serializable
data class MatchListItem(
    val id: String,
    val leagueId: String,
    val leagueName: String,
    val name: String,
    val description: String,
    val state: MatchStateCode,
    val statusText: String,
    val startDate: String = "",
    val teams: List<MatchListTeam> = emptyList(),
    val isDemo: Boolean = false,
) {
    val matchKey: String
        get() = if (isDemo) DEMO_MATCH_KEY else "$leagueId:$id"
}

const val DEMO_MATCH_KEY = "demo:replay"

@Serializable
enum class BallType { DOT, RUN, FOUR, SIX, WIDE, NOBALL, LEGBYE, BYE, OUT }

@Serializable
data class BallBatter(
    val id: String,
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
)

@Serializable
data class BallBowler(
    val id: String,
    val name: String,
    val overs: Double,
    val conceded: Int,
    val wickets: Int,
    val maidens: Int,
)

@Serializable
data class Ball(
    val id: String,
    val seq: Int,
    val innings: Int,
    val over: Double,
    val overNumber: Int,
    val ballInOver: Int,
    val type: BallType,
    val runs: Int,
    val batRuns: Int,
    val isWicket: Boolean,
    val label: String,
    val short: String,
    val text: String,
    val battingTeamId: String,
    val battingTeamAbbr: String,
    val inningsRuns: Int,
    val inningsWickets: Int,
    val runRate: Double,
    val remainingBalls: Int,
    val ballLimit: Int,
    val target: Int,
    val overRuns: Int,
    val overComplete: Boolean,
    val batsman: BallBatter? = null,
    val nonStriker: BallBatter? = null,
    val bowler: BallBowler? = null,
    val dismissalText: String? = null,
    val dismissalType: String? = null,
    val ts: Long = 0L,
)

@Serializable
data class BatterLine(
    val id: String,
    val name: String,
    val headshot: String? = null,
    val position: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val sr: Double,
    val dismissal: String,
    val notOut: Boolean,
    val batted: Boolean,
    val wagon: List<Int>? = null,
    val dotPct: Double? = null,
    val control: Double? = null,
    val preferredShot: String? = null,
)

@Serializable
data class BowlerLine(
    val id: String,
    val name: String,
    val headshot: String? = null,
    val overs: Double,
    val maidens: Int,
    val conceded: Int,
    val wickets: Int,
    val econ: Double,
    val wides: Int = 0,
    val noballs: Int = 0,
    val dots: Int = 0,
)

@Serializable
data class FallOfWicket(
    val wicket: Int,
    val runs: Int,
    val over: Double,
    val batter: String,
)

@Serializable
data class OverSummary(
    val innings: Int,
    val over: Int,
    val runs: Int,
    val wickets: Int,
    val cumulative: Int,
    val balls: List<String>,
)

@Serializable
data class ExtrasBreakdown(
    val wides: Int = 0,
    val noballs: Int = 0,
    val byes: Int = 0,
    val legbyes: Int = 0,
    val total: Int = 0,
)

@Serializable
data class Innings(
    val number: Int,
    val teamId: String,
    val teamAbbr: String,
    val teamName: String,
    val runs: Int,
    val wickets: Int,
    val overs: Double,
    val target: Int,
    val ballLimit: Int,
    val isCurrent: Boolean,
    val description: String,
    val batting: List<BatterLine>,
    val bowling: List<BowlerLine>,
    val extras: ExtrasBreakdown,
    val fow: List<FallOfWicket>,
    val overSummaries: List<OverSummary>,
)

@Serializable
data class SquadPlayer(
    val id: String,
    val name: String,
    val role: String,
    val captain: Boolean,
    val keeper: Boolean,
    val headshot: String? = null,
)

@Serializable
data class TeamInfo(
    val id: String,
    val name: String,
    val abbr: String,
    val logo: String? = null,
    val color: String = "#1a73e8",
    val score: String = "",
    val homeAway: String = "home",
    val winner: Boolean = false,
    val squad: List<SquadPlayer> = emptyList(),
)

@Serializable
data class Partnership(
    val runs: Int = 0,
    val balls: Int = 0,
)

@Serializable
data class ChaseNeed(
    val runs: Int,
    val balls: Int,
)

@Serializable
data class MatchDetail(
    val key: String,
    val id: String,
    val leagueId: String,
    val title: String,
    val series: String,
    val description: String,
    val venue: String,
    val toss: String,
    val startDate: String? = null,
    val state: MatchStateCode,
    val statusText: String,
    val summary: String,
    val teams: List<TeamInfo>,
    val innings: List<Innings>,
    val currentInnings: Int,
    val battingTeamId: String? = null,
    val recentBalls: List<Ball>,
    val currentBatters: List<BatterLine>,
    val currentBowler: BowlerLine? = null,
    val partnership: Partnership,
    val crr: Double,
    val rrr: Double? = null,
    val need: ChaseNeed? = null,
    val notes: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false,
)

enum class CricketEventType {
    BALL,
    FOUR,
    SIX,
    WICKET,
    WIDE,
    NOBALL,
    OVER_END,
    INNINGS_END,
    MATCH_END,
    MILESTONE_BAT,
    MILESTONE_BOWL,
    PARTNERSHIP,
    HATTRICK_WATCH,
}

data class CricketEvent(
    val type: CricketEventType,
    val matchKey: String,
    val ball: Ball? = null,
    val value: Int? = null,
    val player: String? = null,
    val title: String,
    val subtitle: String? = null,
    val ts: Long = System.currentTimeMillis(),
)
