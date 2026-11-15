package io.github.diegopq.hacktv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** The weather now where the TV is: temperature and WMO weather code from Open-Meteo. */
data class WeatherNow(
    val temperature: Int,
    val fahrenheit: Boolean,
    val code: Int,
    val isDay: Boolean,
) {
    val kind: WeatherKind get() = WeatherKind.of(code, isDay)
}

/** The weather codes Open-Meteo uses (WMO), grouped into what an icon and a word can say. */
enum class WeatherKind {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM;

    companion object {
        fun of(code: Int, isDay: Boolean) = when (code) {
            0 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

/**
 * The TV has no GPS, so its approximate place comes from its internet connection (ipwho.is, the city of
 * the provider's network, asked once a day), and the weather from Open-Meteo, every half hour. Neither
 * needs an account or a key.
 */
object Weather {

    private const val TAG = "Weather"
    private const val PREFS = "weather"
    private const val PLACE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val WEATHER_MAX_AGE_MS = 30 * 60 * 1000L

    /** The last weather, even if old, so the clock shows something right away. */
    fun cached(context: Context): WeatherNow? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("temp")) return null
        return WeatherNow(prefs.getInt("temp", 0), prefs.getBoolean("f", false), prefs.getInt("code", 0), prefs.getBoolean("day", true))
    }

    fun isFresh(context: Context): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("time", 0) < WEATHER_MAX_AGE_MS

    /** Downloads the weather now. Null when offline; the cached value stays. */
    suspend fun refresh(context: Context): WeatherNow? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            var lat = prefs.getFloat("lat", Float.NaN)
            var lon = prefs.getFloat("lon", Float.NaN)
            if (lat.isNaN() || System.currentTimeMillis() - prefs.getLong("placeTime", 0) > PLACE_MAX_AGE_MS) {
                val place = get("https://ipwho.is/?fields=success,latitude,longitude")
                if (place.optBoolean("success")) {
                    lat = place.getDouble("latitude").toFloat()
                    lon = place.getDouble("longitude").toFloat()
                    prefs.edit().putFloat("lat", lat).putFloat("lon", lon).putLong("placeTime", System.currentTimeMillis()).apply()
                }
            }
            if (lat.isNaN()) return@withContext null
            // Fahrenheit where people use it: the United States and a few others, by the TV's country.
            val fahrenheit = Tmdb.region() in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
            val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val json = get(
                String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f", lat, lon) +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto$unit",
            ).getJSONObject("current")
            val now = WeatherNow(
                temperature = Math.round(json.getDouble("temperature_2m")).toInt(),
                fahrenheit = fahrenheit,
                code = json.getInt("weather_code"),
                isDay = json.optInt("is_day", 1) == 1,
            )
            prefs.edit().putInt("temp", now.temperature).putBoolean("f", fahrenheit).putInt("code", now.code)
                .putBoolean("day", now.isDay).putLong("time", System.currentTimeMillis()).apply()
            now
        } catch (e: Exception) {
            Log.w(TAG, "Weather failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "hack_tv launcher")
        try {
            if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
