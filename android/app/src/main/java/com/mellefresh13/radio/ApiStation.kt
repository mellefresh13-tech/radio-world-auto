package com.mellefresh13.radio

data class ApiStream(
    val url: String,
    val codec: String?,
    val bitrateKbps: Int?,
    val isHls: Boolean,
    val status: String
)

data class ApiStation(
    val id: String,
    val name: String,
    val country: String,
    val city: String?,
    val languages: List<String>,
    val genres: List<String>,
    val homepage: String?,
    val logo: String?,
    val streams: List<ApiStream>
)
