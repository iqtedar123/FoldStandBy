package com.techseven.foldstandby.data

data class WeatherInfo(
    val temperatureC: Double,
    val weatherCode: Int,
    val description: String,
    val locationLabel: String
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
)
