package com.cricstudio.app.engine

import com.cricstudio.app.model.Ball
import com.cricstudio.app.model.BallType
import com.cricstudio.app.model.CricketEvent
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.MatchStateCode

class EventDetector(private val matchKey: String) {

    companion object {
        private val BAT_MILESTONES = listOf(50, 100, 150, 200, 250, 300)
        private val PARTNERSHIP_MILESTONES = listOf(50, 100, 150, 200, 250)
    }

    private var lastSeq = -1
    private var lastInnings = 0
    private var lastState: MatchStateCode? = null
    private val partnershipBase = mutableMapOf<Int, Int>()
    private val bowlerStreak = mutableMapOf<String, Int>()
    var primed: Boolean = false
        private set

    private fun isLegal(b: Ball): Boolean = b.type != BallType.WIDE && b.type != BallType.NOBALL

    fun prime(balls: List<Ball>, state: MatchStateCode) {
        val sorted = balls.sortedBy { it.seq }
        for (b in sorted) track(b)
        lastSeq = sorted.lastOrNull()?.seq ?: -1
        lastInnings = sorted.lastOrNull()?.innings ?: 0
        lastState = state
        primed = true
    }

    private fun track(b: Ball) {
        if (b.isWicket) partnershipBase[b.innings] = b.inningsRuns
        if (b.bowler != null && isLegal(b)) {
            val k = b.bowler.id
            val isRunOut = b.dismissalType?.contains("run out", ignoreCase = true) == true
            if (b.isWicket && !isRunOut) {
                bowlerStreak[k] = (bowlerStreak[k] ?: 0) + 1
            } else {
                bowlerStreak[k] = 0
            }
        }
    }

    fun process(balls: List<Ball>, state: MatchStateCode, now: Long = System.currentTimeMillis()): List<CricketEvent> {
        if (!primed) {
            prime(balls, state)
            return emptyList()
        }
        val events = mutableListOf<CricketEvent>()
        val fresh = balls.filter { it.seq > lastSeq }.sortedBy { it.seq }

        for (b in fresh) {
            if (lastInnings > 0 && b.innings > lastInnings) {
                events.add(
                    CricketEvent(
                        type = CricketEventType.INNINGS_END,
                        matchKey = matchKey,
                        title = "Innings Break",
                        subtitle = "Innings $lastInnings complete",
                        value = lastInnings,
                        ts = now,
                    )
                )
            }
            lastInnings = b.innings
            val prevPartnership = b.inningsRuns - b.runs - (partnershipBase[b.innings] ?: 0)

            events.add(
                CricketEvent(
                    type = CricketEventType.BALL,
                    matchKey = matchKey,
                    ball = b,
                    title = b.short,
                    subtitle = b.text,
                    ts = now,
                )
            )

            when (b.type) {
                BallType.FOUR -> events.add(
                    CricketEvent(
                        type = CricketEventType.FOUR,
                        matchKey = matchKey,
                        ball = b,
                        title = "FOUR!",
                        subtitle = b.short,
                        player = b.batsman?.name,
                        ts = now,
                    )
                )
                BallType.SIX -> events.add(
                    CricketEvent(
                        type = CricketEventType.SIX,
                        matchKey = matchKey,
                        ball = b,
                        title = "SIX!",
                        subtitle = b.short,
                        player = b.batsman?.name,
                        ts = now,
                    )
                )
                BallType.OUT -> events.add(
                    CricketEvent(
                        type = CricketEventType.WICKET,
                        matchKey = matchKey,
                        ball = b,
                        title = "WICKET!",
                        subtitle = b.dismissalText ?: b.short,
                        player = b.batsman?.name,
                        value = b.inningsWickets,
                        ts = now,
                    )
                )
                BallType.WIDE -> events.add(
                    CricketEvent(
                        type = CricketEventType.WIDE,
                        matchKey = matchKey,
                        ball = b,
                        title = "Wide",
                        subtitle = b.short,
                        ts = now,
                    )
                )
                BallType.NOBALL -> events.add(
                    CricketEvent(
                        type = CricketEventType.NOBALL,
                        matchKey = matchKey,
                        ball = b,
                        title = "No Ball — Free Hit!",
                        subtitle = b.short,
                        ts = now,
                    )
                )
                else -> Unit
            }

            if (b.batsman != null && b.batRuns > 0) {
                val before = b.batsman.runs - b.batRuns
                for (m in BAT_MILESTONES) {
                    if (before < m && b.batsman.runs >= m) {
                        events.add(
                            CricketEvent(
                                type = CricketEventType.MILESTONE_BAT,
                                matchKey = matchKey,
                                ball = b,
                                value = m,
                                player = b.batsman.name,
                                title = if (m >= 100) "CENTURY! ${b.batsman.name}" else "FIFTY! ${b.batsman.name}",
                                subtitle = "${b.batsman.runs} off ${b.batsman.balls} balls · ${b.batsman.fours}x4 · ${b.batsman.sixes}x6",
                                ts = now,
                            )
                        )
                    }
                }
            }

            if (b.isWicket && b.bowler != null && b.bowler.wickets == 5) {
                events.add(
                    CricketEvent(
                        type = CricketEventType.MILESTONE_BOWL,
                        matchKey = matchKey,
                        ball = b,
                        value = 5,
                        player = b.bowler.name,
                        title = "FIVE-WICKET HAUL! ${b.bowler.name}",
                        subtitle = "${b.bowler.wickets}/${b.bowler.conceded} in ${b.bowler.overs} overs",
                        ts = now,
                    )
                )
            }

            track(b)

            if (b.isWicket && b.bowler != null && bowlerStreak[b.bowler.id] == 2) {
                events.add(
                    CricketEvent(
                        type = CricketEventType.HATTRICK_WATCH,
                        matchKey = matchKey,
                        ball = b,
                        player = b.bowler.name,
                        title = "ON A HAT-TRICK!",
                        subtitle = "${b.bowler.name} — two in two",
                        ts = now,
                    )
                )
            }

            if (!b.isWicket) {
                val nowP = b.inningsRuns - (partnershipBase[b.innings] ?: 0)
                for (m in PARTNERSHIP_MILESTONES) {
                    if (prevPartnership < m && nowP >= m) {
                        val pair = listOfNotNull(b.batsman?.name, b.nonStriker?.name).joinToString(" & ")
                        events.add(
                            CricketEvent(
                                type = CricketEventType.PARTNERSHIP,
                                matchKey = matchKey,
                                ball = b,
                                value = m,
                                title = "$m PARTNERSHIP",
                                subtitle = pair,
                                ts = now,
                            )
                        )
                    }
                }
            }

            if (b.overComplete && isLegal(b)) {
                val plural = if (b.overRuns == 1) "" else "s"
                events.add(
                    CricketEvent(
                        type = CricketEventType.OVER_END,
                        matchKey = matchKey,
                        ball = b,
                        value = b.overNumber,
                        title = "End of over ${b.overNumber}",
                        subtitle = "${b.overRuns} run$plural · ${b.battingTeamAbbr} ${b.inningsRuns}/${b.inningsWickets}",
                        ts = now,
                    )
                )
            }

            lastSeq = b.seq
        }

        if (state == MatchStateCode.POST && lastState != null && lastState != MatchStateCode.POST) {
            events.add(
                CricketEvent(
                    type = CricketEventType.MATCH_END,
                    matchKey = matchKey,
                    title = "Match Over",
                    subtitle = "",
                    ts = now,
                )
            )
        }
        lastState = state
        return events
    }
}
