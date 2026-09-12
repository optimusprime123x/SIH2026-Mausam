package dev.mausam.home.data.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Tolerant readers for GeoServer / CAP payloads whose field types drift between layers
 * (SYNOP sends numbers, METAR sends the same fields as strings, blanks appear as "" or null).
 */
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }

fun JsonObject.dbl(key: String): Double? = str(key)?.toDoubleOrNull()

fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

/** GeoJSON `features[*].properties` as plain objects. */
fun JsonObject.featureProperties(): List<JsonObject> =
    arr("features")?.mapNotNull { (it as? JsonObject)?.obj("properties") } ?: emptyList()

fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
