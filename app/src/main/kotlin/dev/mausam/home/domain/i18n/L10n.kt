package dev.mausam.home.domain.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** The app's own language setting, independent of the system locale. */
enum class Lang(val code: String, val locale: Locale) {
    EN("en", Locale("en", "IN")),
    HI("hi", Locale("hi", "IN"));

    companion object {
        fun of(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

/**
 * Copy lives in Kotlin (the domain is pure Kotlin and cannot read Android resources), so
 * translation is a lookup keyed by the English source text. [lang] is Compose state: every
 * composable that reads a translated string recomposes when the setting changes, and the
 * domain reads the same value when it composes card copy, briefs and notifications.
 * A missing entry falls back to English, never to a blank.
 */
object L10n {
    var lang: Lang by mutableStateOf(Lang.EN)

    fun tr(en: String, lang: Lang = this.lang): String = when (lang) {
        Lang.EN -> en
        Lang.HI -> Hindi.table[en] ?: en
    }
}

/** Translate a literal. */
fun String.tr(): String = L10n.tr(this)

/** Translate a template with `%s` / `%d` placeholders, then fill it. Latin digits everywhere. */
fun String.trf(vararg args: Any?): String = String.format(Locale.ENGLISH, L10n.tr(this), *args)
