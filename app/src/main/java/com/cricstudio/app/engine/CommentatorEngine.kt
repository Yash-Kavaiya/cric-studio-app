package com.cricstudio.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import com.cricstudio.app.model.CommentaryLine
import com.cricstudio.app.model.CricketEvent
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchStateCode
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Context-aware cricket commentator ported from apps/server/src/commentary/commentator.ts,
 * paired with Android's on-device TextToSpeech when voice output is enabled.
 */
class CommentatorEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var seq = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                ttsReady = true
            }
        }
    }

    fun speakIfEnabled(text: String, speakVoice: Boolean) {
        if (speakVoice && ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cric_${++seq}")
        }
    }

    fun stopVoice() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun lineForEvent(ev: CricketEvent, match: MatchDetail?): CommentaryLine? {
        val tpl = when (ev.type) {
            CricketEventType.FOUR -> FOUR_LINES.random()
            CricketEventType.SIX -> SIX_LINES.random()
            CricketEventType.WICKET -> WICKET_LINES.random()
            CricketEventType.OVER_END -> OVER_END_LINES.random()
            CricketEventType.MILESTONE_BAT -> if ((ev.value ?: 50) >= 100) CENTURY_LINES.random() else FIFTY_LINES.random()
            CricketEventType.MILESTONE_BOWL -> FIVE_FOR_LINES.random()
            CricketEventType.PARTNERSHIP -> PARTNERSHIP_LINES.random()
            CricketEventType.HATTRICK_WATCH -> HATTRICK_LINES.random()
            CricketEventType.NOBALL -> NOBALL_LINES.random()
            CricketEventType.INNINGS_END -> INNINGS_END_LINES.random()
            CricketEventType.MATCH_END -> MATCH_END_LINES.random()
            else -> return null
        }
        val text = fill(tpl, ev, match)
        val priority = when (ev.type) {
            CricketEventType.WICKET, CricketEventType.MATCH_END, CricketEventType.MILESTONE_BAT -> 5
            CricketEventType.SIX, CricketEventType.FOUR -> 3
            else -> 2
        }
        return CommentaryLine(id = "c-${++seq}", text = text, priority = priority)
    }

    fun chatterLine(match: MatchDetail?, pollQuestion: String): CommentaryLine {
        if (match == null) {
            return CommentaryLine(
                id = "c-${++seq}",
                text = "Welcome to CricStudio! Attach a live match or start the T20I demo replay to get full ball-by-ball commentary.",
            )
        }
        val candidates = mutableListOf<String>()
        val teamA = match.teams.getOrNull(0)?.name ?: "the home side"
        val teamB = match.teams.getOrNull(1)?.name ?: "the visitors"
        val venueShort = match.venue.substringBefore(",").trim()

        when (match.state) {
            MatchStateCode.PRE -> {
                candidates.add("$teamA and $teamB meet in the ${match.description.ifEmpty { "big game" }}. Plenty riding on this one.")
                if (venueShort.isNotEmpty()) {
                    candidates.add("What's a winning total at $venueShort today? Drop your par score in the chat before the first ball.")
                }
                if (match.toss.isNotEmpty()) {
                    candidates.add("Toss news: ${match.toss.trimEnd('.')}. Good call, or would you have done the opposite?")
                }
                candidates.add("Who are you backing today, $teamA or $teamB? Vote in our fan poll!")
            }
            MatchStateCode.POST -> {
                if (match.summary.isNotEmpty()) {
                    candidates.add("That's the result: ${match.summary}. Who was your player of the match?")
                }
                candidates.add("Thanks for watching $teamA versus $teamB with CricStudio!")
            }
            MatchStateCode.IN -> {
                match.need?.takeIf { it.balls > 0 }?.let { need ->
                    val chaseTeam = match.teams.firstOrNull { it.id == match.battingTeamId }?.name ?: "The chasing side"
                    val rrr = match.rrr?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                    val ratio = need.runs.toDouble() / max(1, need.balls)
                    candidates.add(
                        when {
                            ratio > 1.8 -> "$chaseTeam need ${need.runs} from ${need.balls} balls. Required rate is $rrr, getting really tough now."
                            ratio < 0.9 -> "$chaseTeam are cruising. Just ${need.runs} needed from ${need.balls} balls."
                            else -> "Here's the equation: ${need.runs} runs from ${need.balls} balls. Going right down to the wire!"
                        }
                    )
                }
                match.currentBatters.firstOrNull()?.let { a ->
                    val b = match.currentBatters.getOrNull(1)
                    val p = match.partnership
                    candidates.add(
                        if (b != null) {
                            "${a.name} is on ${a.runs} from ${a.balls}, ${b.name} has ${b.runs}. Partnership stands at ${p.runs} off ${p.balls} balls."
                        } else {
                            "${a.name} is batting on ${a.runs} from ${a.balls} balls."
                        }
                    )
                }
                match.currentBowler?.let { bw ->
                    val econStr = String.format(Locale.US, "%.1f", bw.econ)
                    candidates.add("${bw.name} has figures of ${bw.wickets} for ${bw.conceded} from ${bw.overs} overs, going at $econStr an over.")
                }
                val inn = match.innings.firstOrNull { it.number == match.currentInnings }
                if (inn != null) {
                    val balls = EspnNormalizer.oversToBalls(inn.overs)
                    val limit = if (inn.ballLimit > 0) inn.ballLimit else 120
                    if (match.need == null && balls >= 12) {
                        val proj = ((inn.runs.toDouble() / balls) * limit).roundToInt()
                        candidates.add("At this rate, ${inn.teamName} are heading for around $proj${if (venueShort.isNotEmpty()) " at $venueShort" else ""}.")
                    }
                    candidates.add("Welcome if you've just joined: ${inn.teamName} are ${inn.runs} for ${inn.wickets} after ${inn.overs} overs.")
                }
                val pollText = if (pollQuestion.isBlank() || pollQuestion.startsWith("Who wins", ignoreCase = true)) {
                    "Fan poll time: who wins this one, $teamA or $teamB? Vote in the chat!"
                } else {
                    "Time for our fan poll: $pollQuestion Drop your answer in the chat!"
                }
                candidates.add(pollText)
            }
        }
        val chosen = candidates.ifEmpty { listOf("Live coverage continues on CricStudio!") }.random()
        return CommentaryLine(id = "c-${++seq}", text = chosen, priority = 1)
    }

    private fun fill(tpl: String, ev: CricketEvent, d: MatchDetail?): String {
        val b = ev.ball
        val inn = d?.innings?.firstOrNull { it.number == d.currentInnings }
        val score = when {
            inn != null -> "${inn.runs} for ${inn.wickets}"
            b != null -> "${b.inningsRuns} for ${b.inningsWickets}"
            else -> ""
        }
        val map = mapOf(
            "bat" to (b?.batsman?.name ?: ev.player ?: "the batter"),
            "bowler" to (b?.bowler?.name ?: ev.player ?: "the bowler"),
            "team" to (inn?.teamName ?: b?.battingTeamAbbr ?: "the batting side"),
            "score" to score,
            "overs" to (inn?.overs?.toString() ?: b?.over?.toString() ?: ""),
            "overRuns" to (b?.overRuns?.toString() ?: "0"),
            "dismissal" to (ev.subtitle?.replace(Regex("\\s\\d+\\s\\(.*$"), "") ?: ""),
            "value" to (ev.value?.toString() ?: ""),
            "summary" to (d?.summary?.ifEmpty { "a fantastic finish" } ?: "a fantastic finish"),
        )
        return Regex("\\{(\\w+)\\}").replace(tpl) { m -> map[m.groupValues[1]] ?: "" }
    }

    companion object {
        private val FOUR_LINES = listOf(
            "Shot! {bat} finds the gap and that races away to the boundary. Four runs.",
            "Oh, that is gorgeous from {bat}! Timed to perfection, four more.",
            "Cracking shot! {bat} threads it through the field, and nobody is stopping that. Four.",
            "That's a boundary! {bat} puts it away. {team} move to {score}.",
        )
        private val SIX_LINES = listOf(
            "That is huge! {bat} has launched it into the stands. Six!",
            "Oh my word! {bat} has sent that into orbit. Maximum!",
            "Gone, gone, all the way! {bat} clears the rope with ease. That's six!",
            "What a hit! The crowd is on its feet. {bat} with a massive six, and {team} are {score}.",
        )
        private val WICKET_LINES = listOf(
            "Gone! That's a big wicket. {dismissal}. {team} are {score}.",
            "Out! The bowler gets the breakthrough. {dismissal}.",
            "And that's the end of {bat}! {dismissal}. Huge moment in this game.",
            "Wicket! {bowler} strikes, and {bat} has to walk back. {team} now {score}.",
        )
        private val OVER_END_LINES = listOf(
            "End of the over. {overRuns} from it, and {team} are {score} after {overs} overs.",
            "That completes the over. {overRuns} runs, {team} {score}.",
        )
        private val FIFTY_LINES = listOf(
            "Fifty up for {bat}! A terrific knock, raise the bat, take a bow.",
            "And there it is, a half-century for {bat}! Quality innings.",
        )
        private val CENTURY_LINES = listOf(
            "Century! What an innings from {bat}! Absolutely magnificent, take a bow!",
            "A hundred for {bat}! The helmet comes off, the bat goes up!",
        )
        private val FIVE_FOR_LINES = listOf(
            "Five wickets! {bowler} has a five-for, and what a spell this has been!",
        )
        private val PARTNERSHIP_LINES = listOf(
            "That brings up the {value} partnership. These two are building something special.",
            "The {value} stand is up, and the bowlers need answers.",
        )
        private val HATTRICK_LINES = listOf(
            "Two in two! {bowler} is on a hat-trick. Everyone's on the edge of their seats.",
        )
        private val NOBALL_LINES = listOf(
            "No ball! And that means a free hit is coming up. Here's a chance for the batter.",
        )
        private val INNINGS_END_LINES = listOf(
            "That's the end of the innings. Stay with us, the experts are about to break it all down.",
        )
        private val MATCH_END_LINES = listOf(
            "And that's the match! {summary}. What a game of cricket that was!",
        )
    }
}
