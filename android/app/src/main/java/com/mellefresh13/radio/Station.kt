package com.mellefresh13.radio

data class Station(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val genre: String,
    val language: String,
    val streams: List<String>,
    val songTitle: String? = null,
    val artist: String? = null,
    val logo: String? = null,
    val website: String? = null,
    var favorite: Boolean = false
)
