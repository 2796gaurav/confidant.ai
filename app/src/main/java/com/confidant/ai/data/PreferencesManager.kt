package com.confidant.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Top-level DataStore delegate - ensures singleton
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "confidant_prefs")

/**
 * PreferencesManager - Manages app preferences using DataStore
 * IMPORTANT: This class accesses a singleton DataStore instance
 */
class PreferencesManager private constructor(private val context: Context) {
    
    // Keys
    private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    private val KEY_TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
    private val KEY_TELEGRAM_CHAT_ID = longPreferencesKey("telegram_chat_id")
    private val KEY_MODEL_PATH = stringPreferencesKey("model_path")
    // REMOVED: Kite and Upstox integration keys (trading integrations removed)
    private val KEY_USER_AGE = intPreferencesKey("user_age")
    private val KEY_USER_OCCUPATION = stringPreferencesKey("user_occupation")
    private val KEY_USER_LOCATION = stringPreferencesKey("user_location")
    private val KEY_PROACTIVE_ENABLED = booleanPreferencesKey("proactive_enabled")
    private val KEY_NOTIFICATION_CAPTURE_ENABLED = booleanPreferencesKey("notification_capture_enabled")
    private val KEY_THERMAL_MONITORING_ENABLED = booleanPreferencesKey("thermal_monitoring_enabled")
    private val KEY_LAST_MEMORY_CONSOLIDATION = longPreferencesKey("last_memory_consolidation")
    private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
    
    // Sleep mode configuration (ENABLED BY DEFAULT for battery savings)
    private val KEY_SLEEP_MODE_ENABLED = booleanPreferencesKey("sleep_mode_enabled")
    private val KEY_SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
    private val KEY_SLEEP_START_MINUTE = intPreferencesKey("sleep_start_minute")
    private val KEY_SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
    private val KEY_SLEEP_END_MINUTE = intPreferencesKey("sleep_end_minute")
    private val KEY_SLEEP_AUTO_DETECT = booleanPreferencesKey("sleep_auto_detect")
    
    // System messaging preferences
    private val KEY_QUIET_MODE_ENABLED = booleanPreferencesKey("quiet_mode_enabled")
    private val KEY_NOTIFY_SERVER_EVENTS = booleanPreferencesKey("notify_server_events")
    private val KEY_NOTIFY_THERMAL_EVENTS = booleanPreferencesKey("notify_thermal_events")
    
    // Server auto-start preference (24/7 operation)
    private val KEY_SERVER_AUTO_START = booleanPreferencesKey("server_auto_start")
    
    // Setup completion
    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = complete }
    }
    
    suspend fun isSetupComplete(): Boolean {
        return context.dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }.first()
    }
    
    // Telegram
    suspend fun setTelegramBotToken(token: String?) {
        context.dataStore.edit { 
            if (token != null) {
                it[KEY_TELEGRAM_BOT_TOKEN] = token
            } else {
                it.remove(KEY_TELEGRAM_BOT_TOKEN)
            }
        }
    }
    
    suspend fun getTelegramBotToken(): String? {
        return context.dataStore.data.map { it[KEY_TELEGRAM_BOT_TOKEN] }.first()
    }
    
    suspend fun setTelegramChatId(chatId: Long) {
        context.dataStore.edit { it[KEY_TELEGRAM_CHAT_ID] = chatId }
    }
    
    suspend fun getTelegramChatId(): Long? {
        return context.dataStore.data.map { it[KEY_TELEGRAM_CHAT_ID] }.first()
    }
    
    // Model
    suspend fun setModelPath(path: String) {
        context.dataStore.edit { it[KEY_MODEL_PATH] = path }
    }
    
    suspend fun getModelPath(): String? {
        return context.dataStore.data.map { it[KEY_MODEL_PATH] }.first()
    }
    
    // REMOVED: Kite and Upstox integration methods (trading integrations removed)
    
    // User profile (interests only - no names)
    suspend fun setUserAge(age: Int) {
        context.dataStore.edit { it[KEY_USER_AGE] = age }
    }
    
    suspend fun getUserAge(): Int? {
        return context.dataStore.data.map { it[KEY_USER_AGE] }.first()
    }
    
    suspend fun setUserOccupation(occupation: String) {
        context.dataStore.edit { it[KEY_USER_OCCUPATION] = occupation }
    }
    
    suspend fun getUserOccupation(): String? {
        return context.dataStore.data.map { it[KEY_USER_OCCUPATION] }.first()
    }
    
    suspend fun setUserLocation(location: String) {
        context.dataStore.edit { it[KEY_USER_LOCATION] = location }
    }
    
    suspend fun getUserLocation(): String? {
        return context.dataStore.data.map { it[KEY_USER_LOCATION] }.first()
    }
    
    // Feature toggles
    suspend fun setProactiveEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PROACTIVE_ENABLED] = enabled }
    }
    
    suspend fun isProactiveEnabled(): Boolean {
        return context.dataStore.data.map { it[KEY_PROACTIVE_ENABLED] ?: true }.first()
    }
    
    suspend fun setNotificationCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_CAPTURE_ENABLED] = enabled }
    }
    
    suspend fun isNotificationCaptureEnabled(): Boolean {
        return context.dataStore.data.map { it[KEY_NOTIFICATION_CAPTURE_ENABLED] ?: true }.first()
    }
    
    suspend fun setThermalMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_THERMAL_MONITORING_ENABLED] = enabled }
    }
    
    suspend fun isThermalMonitoringEnabled(): Boolean {
        return context.dataStore.data.map { it[KEY_THERMAL_MONITORING_ENABLED] ?: true }.first()
    }
    
    // Memory consolidation
    suspend fun setLastMemoryConsolidation(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_MEMORY_CONSOLIDATION] = timestamp }
    }
    
    suspend fun getLastMemoryConsolidation(): Long? {
        return context.dataStore.data.map { it[KEY_LAST_MEMORY_CONSOLIDATION] }.first()
    }
    
    // Debug mode
    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEBUG_MODE] = enabled }
    }
    
    suspend fun isDebugMode(): Boolean {
        return context.dataStore.data.map { it[KEY_DEBUG_MODE] ?: false }.first()
    }
    
    // Sleep mode configuration
    suspend fun setSleepModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SLEEP_MODE_ENABLED] = enabled }
    }
    
    suspend fun isSleepModeEnabled(): Boolean {
        return context.dataStore.data.map { it[KEY_SLEEP_MODE_ENABLED] ?: true }.first() // DEFAULT: true
    }
    
    suspend fun setSleepStartTime(hour: Int, minute: Int) {
        context.dataStore.edit { 
            it[KEY_SLEEP_START_HOUR] = hour
            it[KEY_SLEEP_START_MINUTE] = minute
        }
    }
    
    suspend fun getSleepStartHour(): Int {
        return context.dataStore.data.map { it[KEY_SLEEP_START_HOUR] ?: 23 }.first()
    }
    
    suspend fun getSleepStartMinute(): Int {
        return context.dataStore.data.map { it[KEY_SLEEP_START_MINUTE] ?: 0 }.first()
    }
    
    suspend fun setSleepEndTime(hour: Int, minute: Int) {
        context.dataStore.edit { 
            it[KEY_SLEEP_END_HOUR] = hour
            it[KEY_SLEEP_END_MINUTE] = minute
        }
    }
    
    suspend fun getSleepEndHour(): Int {
        return context.dataStore.data.map { it[KEY_SLEEP_END_HOUR] ?: 7 }.first()
    }
    
    suspend fun getSleepEndMinute(): Int {
        return context.dataStore.data.map { it[KEY_SLEEP_END_MINUTE] ?: 0 }.first()
    }
    
    suspend fun setSleepAutoDetect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SLEEP_AUTO_DETECT] = enabled }
    }
    
    suspend fun isSleepAutoDetect(): Boolean {
        return context.dataStore.data.map { it[KEY_SLEEP_AUTO_DETECT] ?: false }.first() // DEFAULT: false (manual)
    }
    
    // System messaging preferences
    suspend fun setQuietModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_QUIET_MODE_ENABLED] = enabled }
    }
    
    suspend fun isQuietModeEnabled(): Boolean {
        return context.dataStore.data.map { it[KEY_QUIET_MODE_ENABLED] ?: true }.first()
    }
    
    suspend fun setNotifyServerEvents(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFY_SERVER_EVENTS] = enabled }
    }
    
    suspend fun shouldNotifyServerEvents(): Boolean {
        return context.dataStore.data.map { it[KEY_NOTIFY_SERVER_EVENTS] ?: false }.first()
    }
    
    suspend fun setNotifyThermalEvents(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFY_THERMAL_EVENTS] = enabled }
    }
    
    suspend fun shouldNotifyThermalEvents(): Boolean {
        return context.dataStore.data.map { it[KEY_NOTIFY_THERMAL_EVENTS] ?: false }.first()
    }
    
    // Server auto-start preference (24/7 operation)
    suspend fun setServerAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SERVER_AUTO_START] = enabled }
    }
    
    fun getServerAutoStart(): Boolean {
        return kotlinx.coroutines.runBlocking {
            context.dataStore.data.map { it[KEY_SERVER_AUTO_START] ?: true }.first()
        }
    }
    
    // Flows for reactive UI
    val setupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }
    val telegramConfiguredFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_TELEGRAM_BOT_TOKEN] != null }
    val sleepModeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SLEEP_MODE_ENABLED] ?: true }
    val quietModeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_QUIET_MODE_ENABLED] ?: true }
    val serverAutoStartFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SERVER_AUTO_START] ?: true }
    
    companion object {
        @Volatile
        private var INSTANCE: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}