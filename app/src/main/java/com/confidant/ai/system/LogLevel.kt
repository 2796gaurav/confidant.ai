package com.confidant.ai.system

/**
 * Log level enum for system logs
 */
enum class LogLevel {
    DEBUG, INFO, SUCCESS, WARN, ERROR
}

/**
 * Log entry data class
 */
data class LogEntry(
    val id: String = "${System.currentTimeMillis()}-${System.nanoTime()}", // Unique ID
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: String,
    val message: String
) {
    fun getFormattedTime(): String {
        val dateTime = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}
