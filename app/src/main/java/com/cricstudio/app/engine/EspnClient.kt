package com.cricstudio.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class EspnClient {
    companion object {
        const val ESPN_SITE_WEB = "https://site.web.api.espn.com/apis/site/v2/sports/cricket"
        const val ESPN_SITE = "https://site.api.espn.com/apis/site/v2/sports/cricket"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CricStudio/1.0 (+live cricket broadcast studio)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("ESPN HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty ESPN response")
            JSONObject(body)
        }
    }

    suspend fun scorepanel(): JSONObject = getJson("$ESPN_SITE_WEB/scorepanel")

    suspend fun summary(leagueId: String, eventId: String): JSONObject {
        val l = URLEncoder.encode(leagueId, "UTF-8")
        val e = URLEncoder.encode(eventId, "UTF-8")
        return getJson("$ESPN_SITE_WEB/$l/summary?event=$e")
    }

    suspend fun playByPlay(leagueId: String, eventId: String, page: Int = 1): JSONObject {
        val l = URLEncoder.encode(leagueId, "UTF-8")
        val e = URLEncoder.encode(eventId, "UTF-8")
        return getJson("$ESPN_SITE/$l/playbyplay?event=$e&page=$page")
    }
}
