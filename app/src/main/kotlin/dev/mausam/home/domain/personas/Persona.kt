package dev.mausam.home.domain.personas

/**
 * Eight personas plus General. `shapeKey` names a Material expressive shape so the picker tiles
 * are distinguishable at a glance; the UI resolves the key to an actual MaterialShapes polygon.
 */
enum class Persona(
    val key: String,
    val title: String,
    val tagline: String,
    val shapeKey: String,
) {
    HEALTH("health", "Health-conscious", "Air quality, humidity and UV", "cookie"),
    FITNESS("fitness", "Outdoor fitness", "Best hours to run or ride", "sunny"),
    BEACH("beach", "Beachgoers & surfers", "Sea state, waves and tides", "clover"),
    TRAVEL("travel", "Travellers", "Weather where you are going", "burst"),
    PARENTS("parents", "Parents & families", "School run and afternoon rain", "flower"),
    AGRICULTURE("agriculture", "Agriculture & gardeners", "Rainfall, frost and advisories", "puffy"),
    COMMUTERS("commuters", "Commuters", "Fog, storms and leave-early nudges", "gem"),
    EVENTS("events", "Event planners", "Comfort and the best day this week", "softBurst"),
    GENERAL("general", "General", "Hourly, 7-day and warnings", "circle");

    val isPickable: Boolean get() = this != GENERAL

    companion object {
        fun fromKey(key: String): Persona? = entries.firstOrNull { it.key == key }
        val pickable: List<Persona> get() = entries.filter { it.isPickable }
    }
}
