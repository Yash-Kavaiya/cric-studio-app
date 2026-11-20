package com.cricstudio.app.engine

import com.cricstudio.app.model.Ball
import com.cricstudio.app.model.BallBatter
import com.cricstudio.app.model.BallBowler
import com.cricstudio.app.model.BallType
import com.cricstudio.app.model.BatterLine
import com.cricstudio.app.model.BowlerLine
import com.cricstudio.app.model.ChaseNeed
import com.cricstudio.app.model.ExtrasBreakdown
import com.cricstudio.app.model.FallOfWicket
import com.cricstudio.app.model.Innings
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.MatchListTeam
import com.cricstudio.app.model.MatchStateCode
import com.cricstudio.app.model.OverSummary
import com.cricstudio.app.model.Partnership
import com.cricstudio.app.model.SquadPlayer
import com.cricstudio.app.model.TeamInfo
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object EspnNormalizer {

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
        "dagger" to "†",
        "Dagger" to "‡",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "bull" to "•",
        "middot" to "·",
        "times" to "×",
    )

    private val DISMISSAL_CODES = mapOf(
        "c" to "caught",
        "b" to "bowled",
        "lbw" to "lbw",
        "st" to "stumped",
        "ro" to "run out",
        "hw" to "hit wicket",
        "rtd" to "retired",
    )

    private val ROLE_NAMES = mapOf(
        "WK" to "Wicketkeeper",
        "WBT" to "Wicketkeeper batter",
        "BT" to "Batter",
        "BAT" to "Batter",
        "AR" to "Allrounder",
        "BL" to "Bowler",
        "BOWL" to "Bowler",
    )

    fun oversToBalls(overs: Double, bpo: Int = 6): Int {
        val whole = floor(overs).toInt()
        val part = ((overs - whole) * 10.0).roundToInt()
        return whole * bpo + part
    }

    fun ballsToOvers(balls: Int, bpo: Int = 6): Double {
        return (balls / bpo).toDouble() + (balls % bpo) / 10.0
    }

    fun decodeEntities(s: String): String {
        val regex = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
        return regex.replace(s) { m ->
            val entity = m.groupValues[1]
            if (entity.startsWith("#")) {
                val code = if (entity.startsWith("#x", ignoreCase = true)) {
                    entity.substring(2).toIntOrNull(16)
                } else {
                    entity.substring(1).toIntOrNull(10)
                }
                if (code != null && code in 1 until 0x110000) String(Character.toChars(code)) else m.value
            } else {
                NAMED_ENTITIES[entity] ?: m.value
            }
        }
    }

    fun stripHtml(s: String): String {
        val noBr = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        val noTags = noBr.replace(Regex("<[^>]+>"), "")
        return decodeEntities(noTags).replace(Regex("\\s+"), " ").trim()
    }

    fun cleanDismissal(text: String): String {
        var t = text.replace(Regex("\\s+\\d+\\s*\\(.*$"), "").trim()
        val match = Regex("\\b(c & b|c\\s|lbw|b\\s|st\\s|run out|hit wicket|retired|obstructing|handled|timed out)").find(t)
        if (match != null) t = t.substring(match.range.first)
        return t.ifEmpty { text }
    }

    private fun optNum(obj: JSONObject?, key: String, default: Double = 0.0): Double {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return default
        val raw = obj.opt(key) ?: return default
        return when (raw) {
            is Number -> raw.toDouble().takeIf { it.isFinite() } ?: default
            is String -> raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
            else -> default
        }
    }

    private fun optInt(obj: JSONObject?, key: String, default: Int = 0): Int =
        optNum(obj, key, default.toDouble()).toInt()

    private fun optStr(obj: JSONObject?, key: String, default: String = ""): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return default
        return obj.optString(key, default) ?: default
    }

    private fun parseState(raw: String): MatchStateCode = when (raw.lowercase()) {
        "in" -> MatchStateCode.IN
        "post" -> MatchStateCode.POST
        else -> MatchStateCode.PRE
    }

    fun normalizeScorepanel(raw: JSONObject): List<MatchListItem> {
        val out = mutableListOf<MatchListItem>()
        val scores = raw.optJSONArray("scores") ?: return out
        for (i in 0 until scores.length()) {
            val group = scores.optJSONObject(i) ?: continue
            val leagues = group.optJSONArray("leagues")
            val league = leagues?.optJSONObject(0)
            val events = group.optJSONArray("events") ?: continue
            for (j in 0 until events.length()) {
                val ev = events.optJSONObject(j) ?: continue
                val comp = ev.optJSONArray("competitions")?.optJSONObject(0)
                val compStatus = comp?.optJSONObject("status")
                val evStatus = ev.optJSONObject("status")
                val stateStr = optStr(compStatus?.optJSONObject("type"), "state")
                    .ifEmpty { optStr(evStatus?.optJSONObject("type"), "state", "pre") }
                val summary = optStr(compStatus, "summary")
                val detail = optStr(compStatus?.optJSONObject("type"), "detail")
                    .ifEmpty { optStr(compStatus?.optJSONObject("type"), "description") }

                val competitorsArr = comp?.optJSONArray("competitors")
                val teamPairs = mutableListOf<Pair<Int, MatchListTeam>>()
                if (competitorsArr != null) {
                    for (k in 0 until competitorsArr.length()) {
                        val c = competitorsArr.optJSONObject(k) ?: continue
                        val teamObj = c.optJSONObject("team")
                        val logo = optStr(teamObj, "logo").ifEmpty {
                            teamObj?.optJSONArray("logos")?.optJSONObject(0)?.let { optStr(it, "href") } ?: ""
                        }.ifEmpty { null }
                        val winner = c.optBoolean("winner", false) || optStr(c, "winner").equals("true", true)
                        teamPairs.add(
                            optInt(c, "order", k) to MatchListTeam(
                                id = optStr(teamObj, "id").ifEmpty { optStr(c, "id") },
                                name = optStr(teamObj, "displayName").ifEmpty { optStr(teamObj, "name") },
                                abbr = optStr(teamObj, "abbreviation"),
                                logo = logo,
                                score = optStr(c, "score"),
                                winner = winner,
                            )
                        )
                    }
                }
                val teams = teamPairs.sortedBy { it.first }.map { it.second }
                out.add(
                    MatchListItem(
                        id = optStr(ev, "id"),
                        leagueId = optStr(league, "id").ifEmpty { optStr(ev, "leagueId") },
                        leagueName = optStr(league, "name").ifEmpty { optStr(league, "abbreviation") },
                        name = optStr(ev, "name"),
                        description = optStr(comp, "description").ifEmpty { optStr(ev, "description") },
                        state = parseState(stateStr),
                        statusText = summary.ifEmpty { detail },
                        startDate = optStr(ev, "date").ifEmpty { optStr(comp, "date") },
                        teams = teams,
                    )
                )
            }
        }
        val rank = mapOf(MatchStateCode.IN to 0, MatchStateCode.PRE to 1, MatchStateCode.POST to 2)
        return out.sortedBy { rank[it.state] ?: 3 }
    }

    private fun parseBallType(item: JSONObject): BallType {
        val playType = item.optJSONObject("playType")
        val id = optStr(playType, "id")
        val desc = optStr(playType, "description").lowercase()
        val dismissal = item.optJSONObject("dismissal")
        if (dismissal?.optBoolean("dismissal", false) == true || id == "9" || desc == "out") return BallType.OUT
        if (id == "4" || desc == "six") return BallType.SIX
        if (id == "3" || desc == "four") return BallType.FOUR
        if (id == "6" || desc.contains("wide")) return BallType.WIDE
        if (id == "5" || desc.contains("no ball")) return BallType.NOBALL
        if (id == "8" || desc.contains("leg bye")) return BallType.LEGBYE
        if (id == "7" || desc == "bye" || desc.contains("byes")) return BallType.BYE
        if (id == "2" || desc == "no run") return BallType.DOT
        return if (optInt(item, "scoreValue") > 0) BallType.RUN else BallType.DOT
    }

    fun ballLabel(type: BallType, runs: Int, batRuns: Int): String = when (type) {
        BallType.DOT -> "•"
        BallType.OUT -> "W"
        BallType.FOUR -> "4"
        BallType.SIX -> "6"
        BallType.WIDE -> if (runs > 1) "${runs}wd" else "wd"
        BallType.NOBALL -> if (batRuns > 0) "${batRuns}nb" else "nb"
        BallType.LEGBYE -> "${runs}lb"
        BallType.BYE -> "${runs}b"
        BallType.RUN -> runs.toString()
    }

    private fun parseBallBatter(b: JSONObject?): BallBatter? {
        val athlete = b?.optJSONObject("athlete") ?: return null
        return BallBatter(
            id = optStr(athlete, "id"),
            name = optStr(athlete, "displayName").ifEmpty { optStr(athlete, "name") },
            runs = optInt(b, "totalRuns"),
            balls = optInt(b, "faced"),
            fours = optInt(b, "fours"),
            sixes = optInt(b, "sixes"),
        )
    }

    fun normalizeBall(item: JSONObject): Ball {
        val type = parseBallType(item)
        val runs = optInt(item, "scoreValue")
        val batRuns = optInt(item.optJSONObject("batsman"), "runs", if (type == BallType.WIDE) 0 else runs)
        val inn = item.optJSONObject("innings")
        val ov = item.optJSONObject("over")
        val actual = if (ov?.has("actual") == true && !ov.isNull("actual")) optNum(ov, "actual") else optNum(ov, "overs")
        val overNumber = optInt(ov, "number", floor(actual).toInt() + 1)
        val ballInOver = optInt(ov, "ball")
        val ballsPerOver = optInt(ov, "balls", 6)
        val overComplete = (ov?.optBoolean("complete", false) == true) ||
            (ballInOver >= ballsPerOver && type != BallType.WIDE && type != BallType.NOBALL)
        val bowlerObj = item.optJSONObject("bowler")
        val bowlerAthlete = bowlerObj?.optJSONObject("athlete")
        val bowler = if (bowlerAthlete != null) {
            BallBowler(
                id = optStr(bowlerAthlete, "id"),
                name = optStr(bowlerAthlete, "displayName").ifEmpty { optStr(bowlerAthlete, "name") },
                overs = optNum(bowlerObj, "overs"),
                conceded = optInt(bowlerObj, "conceded"),
                wickets = optInt(bowlerObj, "wickets"),
                maidens = optInt(bowlerObj, "maidens"),
            )
        } else null
        val dismissal = item.optJSONObject("dismissal")
        val isDismissal = dismissal?.optBoolean("dismissal", false) == true

        return Ball(
            id = optStr(item, "id"),
            seq = optInt(item, "sequence"),
            innings = optInt(inn, "number", optInt(item, "period", 1)),
            over = actual,
            overNumber = overNumber,
            ballInOver = ballInOver,
            type = type,
            runs = runs,
            batRuns = batRuns,
            isWicket = type == BallType.OUT,
            label = ballLabel(type, runs, batRuns),
            short = optStr(item, "shortText"),
            text = stripHtml(optStr(item, "text")),
            battingTeamId = optStr(item.optJSONObject("team"), "id"),
            battingTeamAbbr = optStr(item.optJSONObject("team"), "abbreviation"),
            inningsRuns = optInt(inn, "runs"),
            inningsWickets = optInt(inn, "wickets"),
            runRate = optNum(inn, "runRate"),
            remainingBalls = optInt(inn, "remainingBalls"),
            ballLimit = optInt(inn, "ballLimit"),
            target = optInt(inn, "target"),
            overRuns = optInt(ov, "runs"),
            overComplete = overComplete,
            batsman = parseBallBatter(item.optJSONObject("batsman")),
            nonStriker = parseBallBatter(item.optJSONObject("otherBatsman")),
            bowler = bowler,
            dismissalText = if (isDismissal) stripHtml(optStr(dismissal, "text")) else null,
            dismissalType = if (isDismissal) optStr(dismissal, "type") else null,
            ts = optNum(item, "bbbTimestamp", 0.0).toLong(),
        )
    }

    fun normalizeBalls(pbp: JSONObject): List<Ball> {
        val items = pbp.optJSONObject("commentary")?.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<Ball>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            list.add(normalizeBall(item))
        }
        return list.sortedBy { it.seq }
    }

    private fun statMap(stats: JSONObject?): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val categories = stats?.optJSONArray("categories") ?: return map
        for (i in 0 until categories.length()) {
            val cat = categories.optJSONObject(i) ?: continue
            val arr = cat.optJSONArray("stats") ?: continue
            for (j in 0 until arr.length()) {
                val s = arr.optJSONObject(j) ?: continue
                val name = optStr(s, "name")
                val value = if (s.has("value") && !s.isNull("value")) s.opt("value") else s.opt("displayValue")
                if (name.isNotEmpty()) map[name] = value
            }
        }
        return map
    }

    private fun mapNum(m: Map<String, Any?>, key: String, default: Double = 0.0): Double {
        val v = m[key] ?: return default
        return when (v) {
            is Number -> v.toDouble().takeIf { it.isFinite() } ?: default
            is String -> v.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
            else -> default
        }
    }

    private fun mapInt(m: Map<String, Any?>, key: String, default: Int = 0): Int =
        mapNum(m, key, default.toDouble()).toInt()

    private fun mapStr(m: Map<String, Any?>, key: String, default: String = ""): String =
        m[key]?.toString() ?: default

    private fun squadFromRoster(roster: JSONArray?): List<SquadPlayer> {
        if (roster == null) return emptyList()
        val out = mutableListOf<SquadPlayer>()
        for (i in 0 until roster.length()) {
            val p = roster.optJSONObject(i) ?: continue
            val athlete = p.optJSONObject("athlete")
            val pos = optStr(p.optJSONObject("position"), "abbreviation")
                .ifEmpty { optStr(athlete?.optJSONObject("position"), "abbreviation") }
            val role = ROLE_NAMES[pos]
                ?: optStr(athlete?.optJSONObject("position"), "name", "Player").replace("Unknown", "Player")
            out.add(
                SquadPlayer(
                    id = optStr(athlete, "id"),
                    name = optStr(athlete, "displayName"),
                    role = role,
                    captain = p.optBoolean("captain", false),
                    keeper = pos == "WK" || pos == "WBT",
                    headshot = optStr(athlete?.optJSONObject("headshot"), "href").ifEmpty { null },
                )
            )
        }
        return out
    }

    private fun battingFor(roster: JSONArray?, period: Int, dismissals: Map<String, String>): List<BatterLine> {
        if (roster == null) return emptyList()
        val lines = mutableListOf<BatterLine>()
        for (i in 0 until roster.length()) {
            val p = roster.optJSONObject(i) ?: continue
            val linescores = p.optJSONArray("linescores") ?: continue
            var ls: JSONObject? = null
            for (j in 0 until linescores.length()) {
                val cand = linescores.optJSONObject(j)
                if (optInt(cand, "period") == period) {
                    ls = cand
                    break
                }
            }
            if (ls == null) continue
            val s = statMap(ls.optJSONObject("statistics"))
            val batting = ls.optJSONArray("linescores")?.optJSONObject(0)?.optJSONObject("batting")
            if (mapInt(s, "batted") != 1 && batting?.optBoolean("active", false) != true) continue
            val athlete = p.optJSONObject("athlete")
            val id = optStr(athlete, "id")
            val rawCode = optStr(batting?.optJSONObject("outDetails"), "shortText")
                .ifEmpty { mapStr(s, "dismissalCard", "not out") }
            val code = stripHtml(rawCode)
            val outs = mapInt(s, "outs")
            val dismissal = if (outs == 0) code else dismissals[id] ?: DISMISSAL_CODES[code] ?: code
            val wagonArr = batting?.optJSONArray("wagonZone")
            val wagon = if (wagonArr != null) {
                (0 until wagonArr.length()).map { idx -> optInt(wagonArr.optJSONObject(idx), "runs") }
            } else null

            lines.add(
                BatterLine(
                    id = id,
                    name = optStr(athlete, "displayName"),
                    headshot = optStr(athlete?.optJSONObject("headshot"), "href").ifEmpty { null },
                    position = mapInt(s, "battingPosition", 99),
                    runs = mapInt(s, "runs"),
                    balls = mapInt(s, "ballsFaced"),
                    fours = mapInt(s, "fours"),
                    sixes = mapInt(s, "sixes"),
                    sr = mapNum(s, "strikeRate"),
                    dismissal = dismissal,
                    notOut = outs == 0,
                    batted = true,
                    wagon = wagon,
                    dotPct = if (batting?.has("dotBallPercentage") == true) optNum(batting, "dotBallPercentage") else null,
                    control = if (batting?.has("controlPercentage") == true) optNum(batting, "controlPercentage") else null,
                    preferredShot = optStr(batting?.optJSONObject("preferredShot"), "shotName").ifEmpty { null },
                )
            )
        }
        return lines.sortedBy { it.position }
    }

    private fun bowlingFor(roster: JSONArray?, period: Int): List<BowlerLine> {
        if (roster == null) return emptyList()
        val lines = mutableListOf<Pair<Int, BowlerLine>>()
        for (i in 0 until roster.length()) {
            val p = roster.optJSONObject(i) ?: continue
            val linescores = p.optJSONArray("linescores") ?: continue
            var ls: JSONObject? = null
            for (j in 0 until linescores.length()) {
                val cand = linescores.optJSONObject(j)
                if (optInt(cand, "period") == period) {
                    ls = cand
                    break
                }
            }
            if (ls == null) continue
            val s = statMap(ls.optJSONObject("statistics"))
            if (mapInt(s, "bowled") != 1 && mapInt(s, "balls") == 0) continue
            val athlete = p.optJSONObject("athlete")
            lines.add(
                mapInt(s, "bowlingPosition", 99) to BowlerLine(
                    id = optStr(athlete, "id"),
                    name = optStr(athlete, "displayName"),
                    headshot = optStr(athlete?.optJSONObject("headshot"), "href").ifEmpty { null },
                    overs = mapNum(s, "overs"),
                    maidens = mapInt(s, "maidens"),
                    conceded = mapInt(s, "conceded"),
                    wickets = mapInt(s, "wickets"),
                    econ = mapNum(s, "economyRate"),
                    wides = mapInt(s, "wides"),
                    noballs = mapInt(s, "noballs"),
                    dots = mapInt(s, "dots"),
                )
            )
        }
        return lines.sortedBy { it.first }.map { it.second }
    }

    fun buildOverSummaries(balls: List<Ball>, innings: Int): Pair<List<OverSummary>, List<FallOfWicket>> {
        val byOver = linkedMapOf<Int, MutableOver>()
        val fow = mutableListOf<FallOfWicket>()
        val ib = balls.filter { it.innings == innings }.sortedBy { it.seq }
        for (b in ib) {
            val o = byOver.getOrPut(b.overNumber) { MutableOver(innings, b.overNumber) }
            o.runs += b.runs
            o.balls.add(b.label)
            o.cumulative = b.inningsRuns
            if (b.isWicket) {
                o.wickets += 1
                fow.add(
                    FallOfWicket(
                        wicket = b.inningsWickets,
                        runs = b.inningsRuns,
                        over = b.over,
                        batter = b.batsman?.name.orEmpty(),
                    )
                )
            }
        }
        val overs = byOver.values.sortedBy { it.over }.map { o ->
            val last = ib.lastOrNull { it.overNumber == o.over }
            val runs = if (last != null && last.overRuns >= o.runs) last.overRuns else o.runs
            OverSummary(
                innings = o.innings,
                over = o.over,
                runs = runs,
                wickets = o.wickets,
                cumulative = o.cumulative,
                balls = o.balls.toList(),
            )
        }
        return overs to fow
    }

    private class MutableOver(
        val innings: Int,
        val over: Int,
        var runs: Int = 0,
        var wickets: Int = 0,
        var cumulative: Int = 0,
        val balls: MutableList<String> = mutableListOf(),
    )

    fun buildMatchDetail(
        leagueId: String,
        eventId: String,
        summary: JSONObject,
        balls: List<Ball>,
    ): MatchDetail {
        val header = summary.optJSONObject("header")
        val comp = header?.optJSONArray("competitions")?.optJSONObject(0)
        val status = comp?.optJSONObject("status")
        val state = parseState(optStr(status?.optJSONObject("type"), "state", "pre"))

        val rosterByTeam = mutableMapOf<String, JSONArray>()
        val rosters = summary.optJSONArray("rosters")
        if (rosters != null) {
            for (i in 0 until rosters.length()) {
                val r = rosters.optJSONObject(i) ?: continue
                val tid = optStr(r.optJSONObject("team"), "id")
                val arr = r.optJSONArray("roster")
                if (tid.isNotEmpty() && arr != null) rosterByTeam[tid] = arr
            }
        }

        val competitorsArr = comp?.optJSONArray("competitors")
        val competitors = mutableListOf<JSONObject>()
        if (competitorsArr != null) {
            for (i in 0 until competitorsArr.length()) {
                competitorsArr.optJSONObject(i)?.let { competitors.add(it) }
            }
        }

        val teams = competitors
            .sortedBy { optInt(it, "order") }
            .map { c ->
                val teamObj = c.optJSONObject("team")
                val tid = optStr(teamObj, "id").ifEmpty { optStr(c, "id") }
                val rawColor = optStr(teamObj, "color")
                val color = if (rawColor.isNotEmpty()) {
                    if (rawColor.startsWith("#")) rawColor else "#$rawColor"
                } else "#1a73e8"
                val logo = teamObj?.optJSONArray("logos")?.optJSONObject(0)?.let { optStr(it, "href") }
                    ?.ifEmpty { null }
                    ?: optStr(teamObj, "logo").ifEmpty { null }
                TeamInfo(
                    id = tid,
                    name = optStr(teamObj, "displayName").ifEmpty { optStr(teamObj, "name") },
                    abbr = optStr(teamObj, "abbreviation"),
                    logo = logo,
                    color = color,
                    score = optStr(c, "score"),
                    homeAway = if (optStr(c, "homeAway") == "home") "home" else "away",
                    winner = c.optBoolean("winner", false) || optStr(c, "winner").equals("true", true),
                    squad = squadFromRoster(rosterByTeam[tid]),
                )
            }

        val dismissalMap = mutableMapOf<String, String>()
        for (b in balls) {
            if (b.isWicket && b.batsman != null && !b.dismissalText.isNullOrBlank()) {
                dismissalMap[b.batsman.id] = cleanDismissal(b.dismissalText)
            }
        }

        val inningsList = mutableListOf<Innings>()
        for (c in competitors) {
            val teamObj = c.optJSONObject("team")
            val teamId = optStr(teamObj, "id").ifEmpty { optStr(c, "id") }
            val bowlingTeam = competitors.firstOrNull { o ->
                val oid = optStr(o.optJSONObject("team"), "id").ifEmpty { optStr(o, "id") }
                oid != teamId
            }
            val bowlingTeamId = optStr(bowlingTeam?.optJSONObject("team"), "id")
                .ifEmpty { optStr(bowlingTeam, "id") }
            val lss = c.optJSONArray("linescores") ?: continue
            for (i in 0 until lss.length()) {
                val ls = lss.optJSONObject(i) ?: continue
                val isBatting = ls.optBoolean("isBatting", false) || optInt(ls, "isBatting") == 1
                if (!isBatting) continue
                val period = optInt(ls, "period")
                val s = statMap(ls.optJSONObject("statistics"))
                val (overSummaries, fow) = buildOverSummaries(balls, period)
                val wides = mapInt(s, "wides")
                val noballs = mapInt(s, "noballs")
                val byes = mapInt(s, "byes")
                val legbyes = mapInt(s, "legbyes")
                val extrasTotal = mapInt(s, "extras").let { if (it > 0) it else wides + noballs + byes + legbyes }
                inningsList.add(
                    Innings(
                        number = period,
                        teamId = teamId,
                        teamAbbr = optStr(teamObj, "abbreviation"),
                        teamName = optStr(teamObj, "displayName"),
                        runs = optInt(ls, "runs"),
                        wickets = optInt(ls, "wickets"),
                        overs = optNum(ls, "overs"),
                        target = optInt(ls, "target"),
                        ballLimit = mapInt(s, "ballLimit"),
                        isCurrent = optInt(ls, "isCurrent") == 1 || ls.optBoolean("isCurrent", false),
                        description = optStr(ls, "description"),
                        batting = battingFor(rosterByTeam[teamId], period, dismissalMap),
                        bowling = bowlingFor(rosterByTeam[bowlingTeamId], period),
                        extras = ExtrasBreakdown(wides, noballs, byes, legbyes, extrasTotal),
                        fow = fow,
                        overSummaries = overSummaries,
                    )
                )
            }
        }
        inningsList.sortBy { it.number }
        val cur = inningsList.firstOrNull { it.isCurrent } ?: inningsList.lastOrNull()

        val sortedBalls = balls.sortedBy { it.seq }
        val lastBall = sortedBalls.lastOrNull()
        val curBalls = if (cur != null) sortedBalls.filter { it.innings == cur.number } else emptyList()
        val lastCurBall = curBalls.lastOrNull()

        val currentBatters = mutableListOf<BatterLine>()
        if (cur != null) {
            val notOut = cur.batting.filter { it.notOut && it.batted }
            val ids = listOfNotNull(lastCurBall?.batsman?.id, lastCurBall?.nonStriker?.id)
            for (id in ids) {
                val found = cur.batting.firstOrNull { it.id == id && it.notOut }
                if (found != null) currentBatters.add(found)
            }
            if (currentBatters.size < 2) {
                for (b in notOut) {
                    if (currentBatters.none { it.id == b.id }) currentBatters.add(b)
                }
            }
            if (currentBatters.isEmpty() && lastCurBall != null) {
                for (bb in listOfNotNull(lastCurBall.batsman, lastCurBall.nonStriker)) {
                    currentBatters.add(
                        BatterLine(
                            id = bb.id,
                            name = bb.name,
                            position = 0,
                            runs = bb.runs,
                            balls = bb.balls,
                            fours = bb.fours,
                            sixes = bb.sixes,
                            sr = if (bb.balls > 0) (bb.runs.toDouble() / bb.balls) * 100.0 else 0.0,
                            dismissal = "not out",
                            notOut = true,
                            batted = true,
                        )
                    )
                }
            }
        }

        val currentBowler = if (cur != null && lastCurBall?.bowler != null) {
            val bw = lastCurBall.bowler
            cur.bowling.firstOrNull { it.id == bw.id } ?: BowlerLine(
                id = bw.id,
                name = bw.name,
                overs = bw.overs,
                maidens = bw.maidens,
                conceded = bw.conceded,
                wickets = bw.wickets,
                econ = if (bw.overs > 0) bw.conceded / (oversToBalls(bw.overs) / 6.0) else 0.0,
            )
        } else null

        var partnership = Partnership()
        if (curBalls.isNotEmpty()) {
            var lastW = -1
            curBalls.forEachIndexed { idx, b -> if (b.isWicket) lastW = idx }
            val since = curBalls.subList(lastW + 1, curBalls.size)
            val baseRuns = if (lastW >= 0) curBalls[lastW].inningsRuns else 0
            partnership = Partnership(
                runs = (since.lastOrNull()?.inningsRuns ?: baseRuns) - baseRuns,
                balls = since.count { it.type != BallType.WIDE && it.type != BallType.NOBALL },
            )
        }

        val curStats = if (cur != null) {
            val c = competitors.firstOrNull { x ->
                val xid = optStr(x.optJSONObject("team"), "id").ifEmpty { optStr(x, "id") }
                xid == cur.teamId
            }
            val lss = c?.optJSONArray("linescores")
            var found: JSONObject? = null
            if (lss != null) {
                for (i in 0 until lss.length()) {
                    val l = lss.optJSONObject(i)
                    if (optInt(l, "period") == cur.number) {
                        found = l
                        break
                    }
                }
            }
            statMap(found?.optJSONObject("statistics"))
        } else emptyMap()

        val crr = mapNum(curStats, "runRate", lastBall?.runRate ?: 0.0)
        val target = cur?.target ?: 0
        var need: ChaseNeed? = null
        var rrr: Double? = null
        if (cur != null && target > 0 && state == MatchStateCode.IN) {
            val ballLimit = if (cur.ballLimit > 0) cur.ballLimit else (lastBall?.ballLimit ?: 0)
            val ballsUsed = mapInt(curStats, "balls", oversToBalls(cur.overs))
            val remaining = if (ballLimit > 0) max(0, ballLimit - ballsUsed) else 0
            need = ChaseNeed(runs = max(0, target - cur.runs), balls = remaining)
            val calcRrr = if (remaining > 0) (need.runs.toDouble() / remaining) * 6.0 else 0.0
            rrr = mapNum(curStats, "requiredRunRate", calcRrr)
        }

        val notesArr = summary.optJSONArray("notes")
        var toss = ""
        val matchNotes = mutableListOf<String>()
        if (notesArr != null) {
            for (i in 0 until notesArr.length()) {
                val n = notesArr.optJSONObject(i) ?: continue
                when (optStr(n, "type")) {
                    "toss" -> toss = optStr(n, "text")
                    "matchnote" -> matchNotes.add(stripHtml(optStr(n, "text")))
                }
            }
        }
        val venue = optStr(summary.optJSONObject("gameInfo")?.optJSONObject("venue"), "fullName")
        val series = optStr(header?.optJSONObject("league"), "name")
            .ifEmpty { optStr(header?.optJSONArray("leagues")?.optJSONObject(0), "name") }

        return MatchDetail(
            key = "$leagueId:$eventId",
            id = eventId,
            leagueId = leagueId,
            title = teams.joinToString(" v ") { it.name }.ifEmpty { optStr(header, "name") },
            series = series,
            description = optStr(comp, "description").ifEmpty { optStr(header, "description") },
            venue = venue,
            toss = toss,
            startDate = optStr(comp, "date").ifEmpty { optStr(header, "date") }.ifEmpty { null },
            state = state,
            statusText = optStr(status?.optJSONObject("type"), "description")
                .ifEmpty { optStr(status?.optJSONObject("type"), "detail") },
            summary = optStr(status, "summary"),
            teams = teams,
            innings = inningsList,
            currentInnings = cur?.number ?: 0,
            battingTeamId = cur?.teamId,
            recentBalls = sortedBalls.takeLast(60),
            currentBatters = currentBatters.take(2),
            currentBowler = currentBowler,
            partnership = partnership,
            crr = crr,
            rrr = rrr,
            need = need,
            notes = matchNotes.takeLast(12),
            lastUpdated = System.currentTimeMillis(),
        )
    }
}
