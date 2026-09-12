package dev.mausam.home.data.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo: forecast (hourly + 7-day + past week), air quality (with history) and marine.
 * Free for non-commercial use, CC-BY 4.0: the UI must show "Weather data by Open-Meteo.com".
 */
interface OpenMeteoApi {
    @GET("https://api.open-meteo.com/v1/forecast?timezone=Asia%2FKolkata&forecast_days=7&past_days=7&current=$CURRENT&hourly=$HOURLY&daily=$DAILY")
    suspend fun forecast(@Query("latitude") lat: Double, @Query("longitude") lon: Double): OmForecast

    @GET("https://air-quality-api.open-meteo.com/v1/air-quality?timezone=Asia%2FKolkata&past_days=2&forecast_days=2&current=$AQ_CURRENT&hourly=$AQ_HOURLY")
    suspend fun airQuality(@Query("latitude") lat: Double, @Query("longitude") lon: Double): OmAirQuality

    @GET("https://marine-api.open-meteo.com/v1/marine?timezone=Asia%2FKolkata&forecast_days=5&current=$MARINE_CURRENT&hourly=$MARINE_HOURLY&daily=$MARINE_DAILY")
    suspend fun marine(@Query("latitude") lat: Double, @Query("longitude") lon: Double): OmMarine

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val ATTRIBUTION = "Weather data by Open-Meteo.com"
        const val CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
        const val HOURLY = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day,soil_moisture_0_to_1cm,soil_moisture_1_to_3cm,soil_moisture_3_to_9cm,soil_moisture_9_to_27cm,soil_temperature_6cm"
        const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max"
        const val AQ_CURRENT = "pm10,pm2_5,ozone,nitrogen_dioxide,us_aqi,european_aqi,uv_index"
        const val AQ_HOURLY = "pm10,pm2_5,us_aqi"
        const val MARINE_CURRENT = "wave_height,wave_direction,wave_period,swell_wave_height,sea_surface_temperature"
        const val MARINE_HOURLY = "wave_height,wave_period,sea_surface_temperature"
        const val MARINE_DAILY = "wave_height_max,wave_direction_dominant,wave_period_max"
    }
}

@Serializable
data class OmForecast(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "Asia/Kolkata",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 19800,
    val current: OmCurrent? = null,
    val hourly: OmHourly? = null,
    val daily: OmDaily? = null,
)

@Serializable
data class OmCurrent(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
)

@Serializable
data class OmHourly(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    // Volumetric water content, m³/m³, per soil layer; absent in older cached payloads.
    @SerialName("soil_moisture_0_to_1cm") val soil0to1: List<Double?> = emptyList(),
    @SerialName("soil_moisture_1_to_3cm") val soil1to3: List<Double?> = emptyList(),
    @SerialName("soil_moisture_3_to_9cm") val soil3to9: List<Double?> = emptyList(),
    @SerialName("soil_moisture_9_to_27cm") val soil9to27: List<Double?> = emptyList(),
    @SerialName("soil_temperature_6cm") val soilTemperature6cm: List<Double?> = emptyList(),
)

@Serializable
data class OmDaily(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
)

@Serializable
data class OmAirQuality(
    val current: OmAqCurrent? = null,
    val hourly: OmAqHourly? = null,
)

@Serializable
data class OmAqCurrent(
    val time: String,
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val ozone: Double? = null,
    @SerialName("nitrogen_dioxide") val no2: Double? = null,
    @SerialName("us_aqi") val usAqi: Int? = null,
    @SerialName("european_aqi") val europeanAqi: Int? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
)

@Serializable
data class OmAqHourly(
    val time: List<String> = emptyList(),
    val pm10: List<Double?> = emptyList(),
    @SerialName("pm2_5") val pm25: List<Double?> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Int?> = emptyList(),
)

@Serializable
data class OmMarine(
    val current: OmMarineCurrent? = null,
    val hourly: OmMarineHourly? = null,
    val daily: OmMarineDaily? = null,
)

@Serializable
data class OmMarineCurrent(
    val time: String,
    @SerialName("wave_height") val waveHeight: Double? = null,
    @SerialName("wave_direction") val waveDirection: Int? = null,
    @SerialName("wave_period") val wavePeriod: Double? = null,
    @SerialName("swell_wave_height") val swellWaveHeight: Double? = null,
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: Double? = null,
)

@Serializable
data class OmMarineHourly(
    val time: List<String> = emptyList(),
    @SerialName("wave_height") val waveHeight: List<Double?> = emptyList(),
    @SerialName("wave_period") val wavePeriod: List<Double?> = emptyList(),
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?> = emptyList(),
)

@Serializable
data class OmMarineDaily(
    val time: List<String> = emptyList(),
    @SerialName("wave_height_max") val waveHeightMax: List<Double?> = emptyList(),
    @SerialName("wave_direction_dominant") val waveDirectionDominant: List<Int?> = emptyList(),
    @SerialName("wave_period_max") val wavePeriodMax: List<Double?> = emptyList(),
)
