package dev.mausam.home.domain.geo

/**
 * Whether free-text area copy names a place. Whole words only, so "Digha" never matches
 * "Dighaghat" and "Puri" never matches "Purnia"; multi-word names must appear as a phrase.
 */
object PlaceMatch {
    private val split = Regex("[^\\p{L}\\p{N}]+")

    fun mentions(area: String, place: String?): Boolean {
        if (place.isNullOrBlank()) return false
        val words = split.split(area.lowercase()).filter { it.isNotEmpty() }
        val needle = split.split(place.lowercase()).filter { it.isNotEmpty() }
        if (needle.isEmpty() || needle.size > words.size) return false
        return words.windowed(needle.size).any { it == needle }
    }
}
