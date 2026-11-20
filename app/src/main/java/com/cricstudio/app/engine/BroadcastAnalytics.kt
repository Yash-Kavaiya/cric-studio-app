package com.cricstudio.app.engine

import com.cricstudio.app.model.Ball
import com.cricstudio.app.model.BallType
import com.cricstudio.app.model.Innings
import com.cricstudio.app.model.MatchDetail
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WinProbability(
    val teamAAbbr: String,
    val teamBAbbr: String,
    val teamAPct: Int,
    val teamBPct: Int,
    val headline: String,
)

data class PhaseBucket(
    val label: String,
    val range: String,
    val runs: Int,
    val wickets: Int,
    val overs: Int,
    val runRate: Double,
)

data class ScoringSlice(
    val label: String,
    val runs: Int,
    val count: Int,
    val colorHex: Long,
)

object BroadcastAnalytics {

    fun winProbability(match: MatchDetail): WinProbability {
        val teamA = match.teams.getOrNull(0)
        val teamB = match.teams.getOrNull(1)
        val abbrA = teamA?.abbr ?: "T1"
        val abbrB = teamB?.abbr ?: "T2"
        val cur = match.innings.firstOrNull { it.number == match.currentInnings } ?: match.innings.lastOrNull()
        if (cur == null) {
            return WinProbability(abbrA, abbrB, 50, 50, "Even contest before first ball")
        }

        val battingIsA = cur.teamId == teamA?.id
        var batWinProb = 0.5
        if (match.need != null && match.need.balls > 0) {
            val need = match.need
            val rrr = (need.runs.toDouble() / max(1, need.balls)) * 6.0
            val wicketsInHand = (10 - cur.wickets).coerceIn(0, 10)
            val pressure = (rrr - 8.5) * 0.42 - (wicketsInHand - 5) * 0.28
            batWinProb = (1.0 / (1.0 + exp(pressure))).coerceIn(0.04, 0.96)
        } else {
            val wicketsInHand = (10 - cur.wickets).coerceIn(0, 10)
            val rateDelta = (match.crr - 7.8) * 0.25 + (wicketsInHand - 6) * 0.14
            batWinProb = (1.0 / (1.0 + exp(-rateDelta))).coerceIn(0.15, 0.85)
        }

        val pctA = ((if (battingIsA) batWinProb else 1.0 - batWinProb) * 100.0).roundToInt().coerceIn(2, 98)
        val pctB = 100 - pctA
        val fav = if (pctA >= pctB) abbrA else abbrB
        val favPct = max(pctA, pctB)
        return WinProbability(abbrA, abbrB, pctA, pctB, "$fav favoured at $favPct%")
    }

    fun projectedScores(match: MatchDetail): List<Pair<String, Int>> {
        val cur = match.innings.firstOrNull { it.number == match.currentInnings } ?: return emptyList()
        val limit = if (cur.ballLimit > 0) cur.ballLimit else 120
        val used = EspnNormalizer.oversToBalls(cur.overs).coerceAtLeast(1)
        val remaining = max(0, limit - used)
        val crr = if (match.crr > 0) match.crr else (cur.runs.toDouble() / used) * 6.0
        return listOf(
            "At current RR (${String.format("%.2f", crr)})" to cur.runs + ((remaining / 6.0) * crr).roundToInt(),
            "At 8.0 rpo" to cur.runs + ((remaining / 6.0) * 8.0).roundToInt(),
            "At 10.0 rpo" to cur.runs + ((remaining / 6.0) * 10.0).roundToInt(),
            "At 12.0 rpo" to cur.runs + ((remaining / 6.0) * 12.0).roundToInt(),
        )
    }

    fun phaseBreakdown(inn: Innings?): List<PhaseBucket> {
        if (inn == null) return emptyList()
        val overs = inn.overSummaries
        val isT20 = (inn.ballLimit in 1..120) || overs.size <= 20
        val buckets = if (isT20) {
            listOf(
                Triple("Powerplay", "Ov 1–6", 1..6),
                Triple("Middle", "Ov 7–15", 7..15),
                Triple("Death", "Ov 16–20", 16..20),
            )
        } else {
            listOf(
                Triple("Powerplay", "Ov 1–10", 1..10),
                Triple("Middle", "Ov 11–40", 11..40),
                Triple("Death", "Ov 41–50", 41..50),
            )
        }
        return buckets.map { (label, range, r) ->
            val matched = overs.filter { it.over in r }
            val runs = matched.sumOf { it.runs }
            val wkts = matched.sumOf { it.wickets }
            val count = matched.size
            PhaseBucket(
                label = label,
                range = range,
                runs = runs,
                wickets = wkts,
                overs = count,
                runRate = if (count > 0) runs.toDouble() / count else 0.0,
            )
        }
    }

    fun scoringBreakdown(balls: List<Ball>): List<ScoringSlice> {
        var dots = 0
        var singles = 0
        var twosThrees = 0
        var fours = 0
        var sixes = 0
        var extras = 0
        for (b in balls) {
            when (b.type) {
                BallType.DOT -> dots++
                BallType.FOUR -> fours++
                BallType.SIX -> sixes++
                BallType.WIDE, BallType.NOBALL, BallType.BYE, BallType.LEGBYE -> extras += b.runs
                BallType.RUN -> {
                    if (b.batRuns == 1) singles++
                    else if (b.batRuns in 2..3) twosThrees += b.batRuns
                }
                BallType.OUT -> Unit
            }
        }
        return listOf(
            ScoringSlice("Dots", 0, dots, 0xFF64748B),
            ScoringSlice("1s", singles, singles, 0xFF38BDF8),
            ScoringSlice("2s & 3s", twosThrees, twosThrees / 2, 0xFF818CF8),
            ScoringSlice("4s", fours * 4, fours, 0xFF34A853),
            ScoringSlice("6s", sixes * 6, sixes, 0xFFFBBC04),
            ScoringSlice("Extras", extras, extras, 0xFFEA4335),
        )
    }

    fun momentumIndex(inn: Innings?): Int {
        if (inn == null || inn.overSummaries.isEmpty()) return 50
        val recent = inn.overSummaries.takeLast(3)
        val runs = recent.sumOf { it.runs }
        val wkts = recent.sumOf { it.wickets }
        val raw = 50 + (runs - 22) * 2 - wkts * 16
        return min(98, max(2, raw))
    }
}
