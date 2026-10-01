package com.mellefresh13.radio

object DemoCatalog {

    val stations = mutableListOf(
        Station(
            id = "rock-antenne",
            name = "ROCK ANTENNE",
            country = "Germany",
            countryCode = "DE",
            city = "Munich",
            genre = "Rock",
            language = "German",
            streams = listOf("https://stream.rockantenne.de/rockantenne/stream/mp3"),
            songTitle = "Rock broadcast",
            artist = "ROCK ANTENNE",
            website = "rockantenne.de",
            favorite = true
        ),
        Station(
            id = "radio-21",
            name = "Radio 21",
            country = "Germany",
            countryCode = "DE",
            city = "Garbsen",
            genre = "Rock",
            language = "German",
            streams = listOf("https://streams.radio21.de/radio21/mp3-192"),
            songTitle = "Live broadcast",
            artist = "Radio 21",
            website = "radio21.de",
            favorite = false
        ),
        Station(
            id = "fip",
            name = "FIP",
            country = "France",
            countryCode = "FR",
            city = "Paris",
            genre = "Eclectic",
            language = "French",
            streams = listOf("https://icecast.radiofrance.fr/fip-hifi.aac"),
            songTitle = "Live broadcast",
            artist = "FIP",
            website = "radiofrance.fr/fip",
            favorite = true
        ),
        Station(
            id = "rmf",
            name = "RMF FM",
            country = "Poland",
            countryCode = "PL",
            city = "Krakow",
            genre = "Pop",
            language = "Polish",
            streams = listOf("https://rs8-krk2.rmfstream.pl/RMFFM"),
            songTitle = "Live broadcast",
            artist = "RMF FM",
            website = "rmf.fm",
            favorite = true
        ),
        Station(
            id = "bbc-6",
            name = "BBC Radio 6 Music",
            country = "United Kingdom",
            countryCode = "GB",
            city = "London",
            genre = "Alternative",
            language = "English",
            streams = listOf("https://stream.live.vc.bbcmedia.co.uk/bbc_6music"),
            songTitle = "Live broadcast",
            artist = "BBC Radio 6 Music",
            website = "bbc.co.uk/6music",
            favorite = false
        ),
        Station(
            id = "kexp",
            name = "KEXP 90.3 FM",
            country = "United States",
            countryCode = "US",
            city = "Seattle",
            genre = "Indie",
            language = "English",
            streams = listOf("https://kexp-mp3-128.streamguys1.com/kexp128.mp3"),
            songTitle = "Live broadcast",
            artist = "KEXP",
            website = "kexp.org",
            favorite = true
        ),
        Station(
            id = "cade-ser",
            name = "Cadena SER",
            country = "Spain",
            countryCode = "ES",
            city = "Madrid",
            genre = "News",
            language = "Spanish",
            streams = listOf("https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASERAAC.m3u8"),
            songTitle = "Hoy por Hoy",
            artist = "Cadena SER",
            website = "cadenaser.com",
            favorite = false
        )
    )
}
