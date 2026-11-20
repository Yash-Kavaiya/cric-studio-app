package com.cricstudio.app.engine

import android.content.res.AssetManager
import com.cricstudio.app.model.Ball
import com.cricstudio.app.model.BallType
import com.cricstudio.app.model.BatterLine
import com.cricstudio.app.model.BowlerLine
import com.cricstudio.app.model.ChaseNeed
import com.cricstudio.app.model.DEMO_MATCH_KEY
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.MatchListTeam
import com.cricstudio.app.model.MatchStateCode
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Replays the bundled recorded real T20I match (IND v WI) ball by ball so every broadcast
 * scene, celebration, automation rule, and commentator line can be rehearsed anytime.
 */
class DemoReplayEngine(assets: AssetManager, startAt: Int = 78) {

    private val summaryJson: JSONObject
    private val allBalls: List<Ball>
    var cursor: Int
        private set

    private var cachedCursor: Int = -1
    private var cachedDetail: MatchDetail? = null

    init {
        summaryJson = readAssetJson(assets, "summary.json")
        val loaded = mutableListOf<Ball>()
        for (p in 1..8) {
            try {
                val pbp = readAssetJson(assets, "pbp$p.json")
                loaded.addAll(EspnNormalizer.normalizeBalls(pbp))
            } catch (_: Exception) {
                // optional page
            }
        }
        allBalls = loaded.sortedBy { it.seq }
        cursor = min(startAt, allBalls.size).coerceAtLeast(1)
    }

    private fun readAssetJson(assets: AssetManager, name: String): JSONObject {
        val text = try {
            assets.open("demo/$name").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            assets.open(name).bufferedReader().use { it.readText() }
        }
        return JSONObject(text)
    }

    val totalBalls: Int get() = allBalls.size

    fun balls(): List<Ball> = allBalls.subList(0, cursor.coerceIn(0, allBalls.size))

    fun step(count: Int = 1): Boolean {
        var wrapped = false
        repeat(count.coerceAtLeast(1)) {
            cursor++
            if (cursor > allBalls.size) {
                cursor = 1
                wrapped = true
            }
        }
        return !wrapped
    }

    fun listItem(): MatchListItem {
        val d = detail()
        return MatchListItem(
            id = "replay",
            leagueId = "demo",
            leagueName = "Demo replay · recorded T20I",
            name = d.title,
            description = "${d.description} (replay)",
            state = MatchStateCode.IN,
            statusText = d.summary,
            isDemo = true,
            teams = d.teams.map { t ->
                MatchListTeam(
                    id = t.id,
                    name = t.name,
                    abbr = t.abbr,
                    logo = t.logo,
                    score = t.score,
                )
            },
        )
    }

    fun detail(): MatchDetail {
        val existing = cachedDetail
        if (existing != null && cachedCursor == cursor) {
            return existing.copy(lastUpdated = System.currentTimeMillis())
        }
        val built = build()
        cachedCursor = cursor
        cachedDetail = built
        return built
    }

    private fun build(): MatchDetail {
        val currentBalls = balls()
        val base = EspnNormalizer.buildMatchDetail(
            leagueId = "demo",
            eventId = "replay",
            summary = summaryJson,
            balls = currentBalls,
        )
        val last = currentBalls.lastOrNull()
        val curInn = last?.innings ?: 1
        val firstInningsRuns = base.innings.firstOrNull()?.runs ?: 0

        val innings = base.innings
            .filter { it.number <= curInn }
            .map { inn ->
                val ib = currentBalls.filter { it.innings == inn.number }
                val lb = ib.lastOrNull()
                val legal = ib.count { it.type != BallType.WIDE && it.type != BallType.NOBALL }
                inn.copy(
                    runs = lb?.inningsRuns ?: 0,
                    wickets = lb?.inningsWickets ?: 0,
                    overs = EspnNormalizer.ballsToOvers(legal),
                    isCurrent = inn.number == curInn,
                    target = if (inn.number == 2) firstInningsRuns + 1 else 0,
                    batting = battingFromBalls(ib),
                    bowling = bowlingFromBalls(ib),
                )
            }

        val cur = innings.firstOrNull { it.isCurrent }
        val teams = base.teams.map { t ->
            val parts = innings.filter { it.teamId == t.id }.map { i ->
                "${i.runs}/${i.wickets}" + if (i.isCurrent) " (${i.overs} ov)" else ""
            }
            t.copy(score = parts.joinToString(" & "))
        }

        var need: ChaseNeed? = null
        var rrr: Double? = null
        var summaryText = "${cur?.teamAbbr ?: ""} batting".trim()
        if (cur != null && cur.number == 2) {
            val remaining = max(0, 120 - EspnNormalizer.oversToBalls(cur.overs))
            need = ChaseNeed(runs = max(0, cur.target - cur.runs), balls = remaining)
            rrr = if (remaining > 0) (need.runs.toDouble() / remaining) * 6.0 else 0.0
            summaryText = "${cur.teamName} need ${need.runs} runs from ${need.balls} balls"
        }

        val currentBatters = listOfNotNull(last?.batsman, last?.nonStriker)
            .mapNotNull { bb -> cur?.batting?.firstOrNull { it.id == bb.id } }
        val currentBowler = last?.bowler?.let { bw -> cur?.bowling?.firstOrNull { it.id == bw.id } }

        return base.copy(
            key = DEMO_MATCH_KEY,
            isDemo = true,
            state = MatchStateCode.IN,
            statusText = "Live (replay)",
            summary = summaryText,
            teams = teams,
            innings = innings,
            currentInnings = curInn,
            battingTeamId = cur?.teamId,
            currentBatters = currentBatters,
            currentBowler = currentBowler,
            crr = last?.runRate ?: 0.0,
            need = need,
            rrr = rrr,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    private fun battingFromBalls(ib: List<Ball>): List<BatterLine> {
        val map = linkedMapOf<String, BatterLine>()
        var pos = 0
        for (b in ib) {
            for (bb in listOfNotNull(b.batsman, b.nonStriker)) {
                val ex = map[bb.id]
                val position = ex?.position ?: ++pos
                val sr = if (bb.balls > 0) round((bb.runs.toDouble() / bb.balls) * 10000.0) / 100.0 else 0.0
                map[bb.id] = BatterLine(
                    id = bb.id,
                    name = bb.name,
                    position = position,
                    runs = bb.runs,
                    balls = bb.balls,
                    fours = bb.fours,
                    sixes = bb.sixes,
                    sr = sr,
                    dismissal = ex?.dismissal ?: "not out",
                    notOut = ex?.notOut ?: true,
                    batted = true,
                )
            }
            if (b.isWicket && b.batsman != null) {
                val existing = map[b.batsman.id]
                if (existing != null) {
                    map[b.batsman.id] = existing.copy(
                        notOut = false,
                        dismissal = EspnNormalizer.cleanDismissal(b.dismissalText ?: "out"),
                    )
                }
            }
        }
        return map.values.sortedBy { it.position }
    }

    private fun bowlingFromBalls(ib: List<Ball>): List<BowlerLine> {
        data class ExtraAcc(var wides: Int = 0, var noballs: Int = 0, var dots: Int = 0)
        val extras = mutableMapOf<String, ExtraAcc>()
        val orderMap = mutableMapOf<String, Int>()
        val lines = mutableMapOf<String, BowlerLine>()
        var order = 0
        for (b in ib) {
            val bw = b.bowler ?: continue
            val acc = extras.getOrPut(bw.id) { ExtraAcc() }
            if (b.type == BallType.WIDE) acc.wides += b.runs
            if (b.type == BallType.NOBALL) acc.noballs += 1
            if (b.type == BallType.DOT) acc.dots += 1
            if (!orderMap.containsKey(bw.id)) orderMap[bw.id] = ++order
            val ballsBowled = EspnNormalizer.oversToBalls(bw.overs)
            val econ = if (ballsBowled > 0) round((bw.conceded.toDouble() / ballsBowled) * 600.0) / 100.0 else 0.0
            lines[bw.id] = BowlerLine(
                id = bw.id,
                name = bw.name,
                overs = bw.overs,
                maidens = bw.maidens,
                conceded = bw.conceded,
                wickets = bw.wickets,
                econ = econ,
                wides = acc.wides,
                noballs = acc.noballs,
                dots = acc.dots,
            )
        }
        return lines.values.sortedBy { orderMap[it.id] ?: 99 }
    }
}
