package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WeatherTool - Real-time weather data using Open-Meteo API
 * 
 * 2026 OPTIMIZATIONS:
 * - Open-Meteo: Free, no API key, open-source
 * - Real-time + 7-day forecast
 * - Temperature, wind, precipitation, conditions
 * - Geocoding for location lookup
 * - Caching for repeated queries
 */
class WeatherTool(private val context: Context? = null) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val searchCache = context?.let { SearchCache(it) }
    
    fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = "get_weather",
            description = "Get current weather and 7-day forecast for any location. Returns temperature, conditions, wind, precipitation, and forecast. Use for weather queries, forecasts, or climate information.",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = "string",
                    description = "City name or location (e.g., 'London', 'New York', 'Mumbai')",
                    required = true
                ),
                ToolParameter(
                    name = "units",
                    type = "string",
                    description = "Temperature units: 'celsius' or 'fahrenheit' (default: celsius)",
                    required = false
                ),
                ToolParameter(
                    name = "forecast_days",
                    type = "integer",
                    description = "Number of forecast days (default: 3, max: 7)",
                    required = false
                )
            )
        )
    }
    
    suspend fun execute(
        arguments: Map<String, String>,
        statusCallback: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Weather Tool (Open-Meteo) ===")
            
            val location = arguments["location"] ?: return@withContext Result.failure(
                Exception("Missing required parameter: location")
            )
            val units = arguments["units"] ?: "celsius"
            val forecastDays = arguments["forecast_days"]?.toIntOrNull()?.coerceIn(1, 7) ?: 3
            
            Log.i(TAG, "Getting weather for: $location (units: $units, forecast: $forecastDays days)")
            
            // Check cache
            val cacheKey = "weather:$location:$units:$forecastDays"
            val cached = searchCache?.get(cacheKey)
            if (cached != null) {
                Log.i(TAG, "✓ Using cached weather data")
                statusCallback?.invoke("✓ Found cached weather")
                return@withContext Result.success(cached)
            }
            
            statusCallback?.invoke("🌤️ Getting weather for $location...")
            
            // Step 1: Geocode location to get coordinates
            val coordinates = geocodeLocation(location)
            if (coordinates == null) {
                statusCallback?.invoke("❌ Location not found")
                return@withContext Result.failure(Exception("Could not find location: $location"))
            }
            
            Log.i(TAG, "Coordinates: ${coordinates.first}, ${coordinates.second}")
            statusCallback?.invoke("📍 Found location: ${coordinates.third}")
            
            // Step 2: Get weather data
            val weatherData = getWeatherData(
                latitude = coordinates.first,
                longitude = coordinates.second,
                locationName = coordinates.third,
                units = units,
                forecastDays = forecastDays
            )
            
            statusCallback?.invoke("✅ Weather data retrieved")
            
            // Cache result
            if (weatherData.isNotEmpty()) {
                searchCache?.put(cacheKey, weatherData, ttlMinutes = 30) // 30 min cache for weather
            }
            
            Log.i(TAG, "✓ Returning ${weatherData.length} chars of weather data")
            Result.success(weatherData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Weather fetch failed: ${e.message}", e)
            statusCallback?.invoke("❌ Weather fetch failed")
            Result.failure(e)
        }
    }
    
    /**
     * Geocode location name to coordinates using Open-Meteo Geocoding API
     * Returns (latitude, longitude, formatted_name)
     */
    private fun geocodeLocation(location: String): Triple<Double, Double, String>? {
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${location.replace(" ", "+")}&count=1&language=en&format=json"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return null
            
            val jsonObj = JSONObject(json)
            val results = jsonObj.optJSONArray("results") ?: return null
            
            if (results.length() == 0) return null
            
            val firstResult = results.getJSONObject(0)
            val latitude = firstResult.getDouble("latitude")
            val longitude = firstResult.getDouble("longitude")
            val name = firstResult.getString("name")
            val country = firstResult.optString("country", "")
            
            val formattedName = if (country.isNotEmpty()) "$name, $country" else name
            
            return Triple(latitude, longitude, formattedName)
            
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed: ${e.message}")
            return null
        }
    }
    
    /**
     * Get weather data from Open-Meteo API
     */
    private fun getWeatherData(
        latitude: Double,
        longitude: Double,
        locationName: String,
        units: String,
        forecastDays: Int
    ): String {
        try {
            val tempUnit = if (units == "fahrenheit") "fahrenheit" else "celsius"
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast?")
                append("latitude=$latitude&longitude=$longitude")
                append("&current_weather=true")
                append("&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weathercode")
                append("&forecast_days=$forecastDays")
                append("&temperature_unit=$tempUnit")
                append("&timezone=auto")
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return "Failed to fetch weather data"
            
            val jsonObj = JSONObject(json)
            
            // Parse current weather
            val current = jsonObj.getJSONObject("current_weather")
            val currentTemp = current.getDouble("temperature")
            val currentWindSpeed = current.getDouble("windspeed")
            val currentWeatherCode = current.getInt("weathercode")
            val currentTime = current.getString("time")
            
            // Parse daily forecast
            val daily = jsonObj.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val precipitation = daily.getJSONArray("precipitation_sum")
            val weatherCodes = daily.getJSONArray("weathercode")
            
            // Format response
            return buildString {
                appendLine("🌤️ Weather for $locationName")
                appendLine()
                appendLine("📍 Current Conditions ($currentTime)")
                appendLine("Temperature: $currentTemp°${if (tempUnit == "fahrenheit") "F" else "C"}")
                appendLine("Conditions: ${getWeatherDescription(currentWeatherCode)}")
                appendLine("Wind Speed: $currentWindSpeed km/h")
                appendLine()
                appendLine("📅 ${forecastDays}-Day Forecast:")
                
                for (i in 0 until minOf(forecastDays, dates.length())) {
                    val date = dates.getString(i)
                    val maxTemp = maxTemps.getDouble(i)
                    val minTemp = minTemps.getDouble(i)
                    val precip = precipitation.getDouble(i)
                    val code = weatherCodes.getInt(i)
                    
                    appendLine()
                    appendLine("${i + 1}. $date")
                    appendLine("   High: $maxTemp° | Low: $minTemp°")
                    appendLine("   ${getWeatherDescription(code)}")
                    if (precip > 0) {
                        appendLine("   Precipitation: $precip mm")
                    }
                }
                
                appendLine()
                appendLine("Data source: Open-Meteo.com")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Weather data parsing failed: ${e.message}")
            return "Failed to parse weather data: ${e.message}"
        }
    }
    
    /**
     * Convert WMO weather code to description
     * Based on WMO 4677 standard
     */
    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "☀️ Clear sky"
            1 -> "🌤️ Mainly clear"
            2 -> "⛅ Partly cloudy"
            3 -> "☁️ Overcast"
            45, 48 -> "🌫️ Foggy"
            51, 53, 55 -> "🌧️ Drizzle"
            61, 63, 65 -> "🌧️ Rain"
            71, 73, 75 -> "❄️ Snow"
            77 -> "🌨️ Snow grains"
            80, 81, 82 -> "🌦️ Rain showers"
            85, 86 -> "🌨️ Snow showers"
            95 -> "⛈️ Thunderstorm"
            96, 99 -> "⛈️ Thunderstorm with hail"
            else -> "Unknown ($code)"
        }
    }
    
    companion object {
        private const val TAG = "WeatherTool"
    }
}
