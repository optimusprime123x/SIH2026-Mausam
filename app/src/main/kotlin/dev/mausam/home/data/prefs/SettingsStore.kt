package dev.mausam.home.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.model.Units
import dev.mausam.home.domain.personas.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mausam_settings")

/** Persona choice, notification times, quiet hours, effects, text size, language, units. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val personas = stringSetPreferencesKey("personas")
        val units = stringPreferencesKey("units")
        val largeText = booleanPreferencesKey("large_text")
        val effects = booleanPreferencesKey("effects")
        val language = stringPreferencesKey("language")
        val morning = stringPreferencesKey("brief_morning")
        val evening = stringPreferencesKey("brief_evening")
        val quietStart = stringPreferencesKey("quiet_start")
        val quietEnd = stringPreferencesKey("quiet_end")
        val commuteStart = stringPreferencesKey("commute_start")
        val commuteEnd = stringPreferencesKey("commute_end")
        val onboarded = booleanPreferencesKey("onboarded")
        val primaryLocation = stringPreferencesKey("primary_location")
        val notifiedWarnings = stringSetPreferencesKey("notified_warnings")
    }

    private val store get() = context.settingsDataStore

    val settings: Flow<UserSettings> = store.data.map { p -> p.toSettings() }
    val onboarded: Flow<Boolean> = store.data.map { it[Keys.onboarded] ?: false }
    val primaryLocationId: Flow<String?> = store.data.map { it[Keys.primaryLocation] }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { p ->
            val s = transform(p.toSettings())
            p[Keys.personas] = s.personas.map { it.key }.toSet()
            p[Keys.units] = s.units.name
            p[Keys.largeText] = s.largeText
            p[Keys.effects] = s.effectsEnabled
            p[Keys.language] = s.language
            p[Keys.morning] = s.morningBrief.toString()
            p[Keys.evening] = s.eveningBrief.toString()
            p[Keys.quietStart] = s.quietStart.toString()
            p[Keys.quietEnd] = s.quietEnd.toString()
            p[Keys.commuteStart] = s.commuteStart.toString()
            p[Keys.commuteEnd] = s.commuteEnd.toString()
        }
    }

    suspend fun setOnboarded(done: Boolean) = store.edit { it[Keys.onboarded] = done }
    suspend fun setPrimaryLocation(id: String) = store.edit { it[Keys.primaryLocation] = id }
    suspend fun notifiedWarnings(): Set<String> = store.data.first()[Keys.notifiedWarnings] ?: emptySet()
    suspend fun markNotified(ids: Set<String>) = store.edit { p ->
        // Keep the set bounded; ids are unique per warning issue.
        p[Keys.notifiedWarnings] = ((p[Keys.notifiedWarnings] ?: emptySet()) + ids).toList().takeLast(200).toSet()
    }

    private fun Preferences.toSettings(): UserSettings {
        val d = UserSettings()
        fun time(key: Preferences.Key<String>, fallback: LocalTime) =
            this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
        return UserSettings(
            personas = this[Keys.personas]?.mapNotNull(Persona::fromKey)?.toSet()?.ifEmpty { d.personas } ?: d.personas,
            units = this[Keys.units]?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: d.units,
            largeText = this[Keys.largeText] ?: d.largeText,
            effectsEnabled = this[Keys.effects] ?: d.effectsEnabled,
            language = this[Keys.language] ?: d.language,
            morningBrief = time(Keys.morning, d.morningBrief),
            eveningBrief = time(Keys.evening, d.eveningBrief),
            quietStart = time(Keys.quietStart, d.quietStart),
            quietEnd = time(Keys.quietEnd, d.quietEnd),
            commuteStart = time(Keys.commuteStart, d.commuteStart),
            commuteEnd = time(Keys.commuteEnd, d.commuteEnd),
        )
    }
}
