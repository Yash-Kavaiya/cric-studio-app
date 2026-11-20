package com.cricstudio.app.engine

import android.content.Context
import com.cricstudio.app.model.SavedStudio
import com.cricstudio.app.model.StreamDestination
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StudioStorage(context: Context) {
    private val prefs = context.getSharedPreferences("cricstudio_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("dark_theme", dark).apply()
    }

    fun getSavedStudios(): List<SavedStudio> {
        val raw = prefs.getString("saved_studios", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedStudio>>(raw) }.getOrDefault(emptyList())
    }

    fun saveStudio(studio: SavedStudio): List<SavedStudio> {
        val current = getSavedStudios().filterNot { it.id == studio.id }
        val updated = listOf(studio) + current
        prefs.edit().putString("saved_studios", json.encodeToString(updated)).apply()
        return updated
    }

    fun removeStudio(studioId: String): List<SavedStudio> {
        val updated = getSavedStudios().filterNot { it.id == studioId }
        prefs.edit().putString("saved_studios", json.encodeToString(updated)).apply()
        return updated
    }

    fun getDestinations(studioId: String): List<StreamDestination> {
        val raw = prefs.getString("dests_$studioId", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StreamDestination>>(raw) }.getOrDefault(emptyList())
    }

    fun saveDestinations(studioId: String, destinations: List<StreamDestination>) {
        prefs.edit().putString("dests_$studioId", json.encodeToString(destinations)).apply()
    }
}
