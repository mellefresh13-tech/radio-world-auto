package com.mellefresh13.radio

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mellefresh13.radio.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val demoCatalog = DemoCatalog.stations
    private var catalog: MutableList<Station> = demoCatalog
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var userStateStore: UserStateStore
    private val favoriteIds = mutableSetOf<String>()
    private var remoteCountries: List<CountryItem> = emptyList()
    private var remoteGenres: List<GenreItem> = emptyList()
    private var currentStation: Station? = null
    private var currentStreamIndex = 0
    private val recentIds = ArrayDeque<String>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayerButton()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayerButton()
        }

        override fun onPlayerError(error: PlaybackException) {
            val station = currentStation ?: return

            if (currentStreamIndex + 1 < station.streams.size) {
                currentStreamIndex++
                playCurrentStream()
            } else {
                showPlayerState("STREAM UNAVAILABLE", "No working backup stream")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        userStateStore = UserStateStore(this)
        favoriteIds.clear()
        favoriteIds.addAll(userStateStore.loadFavoriteIds())
        recentIds.addAll(userStateStore.loadRecentIds().take(10))

        catalogRepository = ApiCatalogRepository()
        loadRemoteCatalog()

        val token = SessionToken(
            this,
            ComponentName(this, RadioPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                renderPlayer()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun loadRemoteCatalog() {
        catalogRepository.loadStations(limit = 200) { result ->
            result.onSuccess { stations ->
                if (stations.isNotEmpty()) {
                    catalog = stations.toMutableList()
                    applyPersistedState()
                    currentStation = currentStation ?: catalog.firstOrNull()
                    renderPlayer()
                }
            }
        }

        catalogRepository.loadCountries { result ->
            result.onSuccess { countries ->
                remoteCountries = countries
            }
        }

        catalogRepository.loadGenres { result ->
            result.onSuccess { genres ->
                remoteGenres = genres
            }
        }
    }

    private fun applyPersistedState() {
        catalog.forEach { station ->
            station.favorite = favoriteIds.contains(station.id)
        }
    }

    private fun persistFavorites() {
        userStateStore.saveFavoriteIds(favoriteIds)
    }

    private fun persistRecents() {
        userStateStore.saveRecentIds(recentIds)
    }

    private fun setupNavigation() {
        binding.navPlayer.setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
        binding.navCountries.setOnClickListener { showScreen("COUNTRIES") { renderCountries() } }
        binding.navGenres.setOnClickListener { showScreen("GENRES") { renderGenres() } }
        binding.navFavorites.setOnClickListener { showScreen("FAVORITES") { renderFavorites() } }
        binding.navRecents.setOnClickListener { showScreen("RECENT") { renderRecents() } }
        binding.navSearch.setOnClickListener { showScreen("SEARCH") { renderSearch() } }

        setActiveNav(R.id.navPlayer)
    }

    private fun showScreen(title: String, content: () -> Unit) {
        binding.activeAppTitle.text = "◉  RADIO WORLD AUTO  •  " + title
        setActiveNav(
            when (title) {
                "PLAYER" -> R.id.navPlayer
                "COUNTRIES" -> R.id.navCountries
                "GENRES" -> R.id.navGenres
                "FAVORITES" -> R.id.navFavorites
                "RECENT" -> R.id.navRecents
                else -> R.id.navSearch
            }
        )
        binding.contentContainer.removeAllViews()
        content()
    }

    private fun setActiveNav(activeId: Int) {
        val ids = intArrayOf(
            R.id.navPlayer,
            R.id.navCountries,
            R.id.navGenres,
            R.id.navFavorites,
            R.id.navRecents,
            R.id.navSearch
        )

        ids.forEach { id ->
            val button = findViewById<Button>(id)
            button.setTextColor(
                getColor(
                    if (id == activeId) R.color.auto_bg else R.color.auto_text_main
                )
            )
            button.setBackgroundResource(
                if (id == activeId) R.drawable.bg_accent else R.drawable.bg_card
            )
        }
    }

    private fun renderPlayer() {
        val station = currentStation ?: catalog.first()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val stationHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_surface)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val flag = TextView(this).apply {
            text = flagFor(station.countryCode)
            textSize = 30f
        }
        stationHeader.addView(flag, LinearLayout.LayoutParams(dp(44), dp(50)))

        val headerText = verticalText(
            station.country.uppercase(),
            station.city + " • " + station.genre,
            16f,
            12f
        )
        stationHeader.addView(headerText, LinearLayout.LayoutParams(0, dp(60), 1f))

        val details = Button(this).apply {
            text = "STATION DETAILS"
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_card)
            setOnClickListener { showStationDetails(station) }
            minHeight = dp(52)
        }
        stationHeader.addView(details, LinearLayout.LayoutParams(dp(190), dp(56)))

        root.addView(stationHeader, LinearLayout.LayoutParams(-1, dp(76)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }

        val logo = TextView(this).apply {
            text = station.name.firstOrNull()?.uppercase() ?: "R"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.auto_bg))
            setBackgroundResource(R.drawable.bg_accent)
        }
        body.addView(logo, LinearLayout.LayoutParams(dp(190), dp(190)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }

        info.addView(
            TextView(this).apply {
                text = "RADIO STREAM"
                textSize = 11f
                setTextColor(getColor(R.color.auto_accent))
            },
            LinearLayout.LayoutParams(-1, dp(22))
        )

        info.addView(
            TextView(this).apply {
                text = station.name
                textSize = 29f
                maxLines = 1
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(-1, dp(52))
        )

        val trackBox = verticalText(
            station.songTitle ?: station.name,
            station.artist ?: "Live Broadcast Stream",
            19f,
            13f
        ).apply {
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        info.addView(trackBox, LinearLayout.LayoutParams(-1, dp(76)))

        val volume = SeekBar(this).apply {
            max = 100
            progress = 80
            contentDescription = "Volume"
        }
        info.addView(volume, LinearLayout.LayoutParams(-1, dp(50)))

        body.addView(info, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundResource(R.drawable.bg_surface)
        }

        controls.addView(actionButton("BROWSE") { showScreen("COUNTRIES") { renderCountries() } })
        controls.addView(actionButton("◀") { playPrevious() })
        controls.addView(
            actionButton(if (controller?.isPlaying == true) "❚❚" else "▶") {
                togglePlayPause()
            },
            LinearLayout.LayoutParams(0, dp(72), 1f)
        )
        controls.addView(actionButton("▶") { playNext() })
        controls.addView(actionButton("SHUFFLE") { playStation(catalog.random()) })

        root.addView(controls, LinearLayout.LayoutParams(-1, dp(80)))

        val favorite = Button(this).apply {
            text = if (station.favorite) "★  FAVORITED" else "☆  FAVORITE"
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_card)
            setOnClickListener {
                station.favorite = !station.favorite
                if (station.favorite) {
                    favoriteIds.add(station.id)
                } else {
                    favoriteIds.remove(station.id)
                }
                persistFavorites()
                renderPlayer()
            }
            minHeight = dp(54)
        }

        root.addView(
            favorite,
            LinearLayout.LayoutParams(-1, dp(58)).apply {
                topMargin = dp(8)
            }
        )

        binding.contentContainer.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    private fun renderCountries(filter: String = "") {
        val all = if (remoteCountries.isNotEmpty()) {
            remoteCountries.sortedBy { it.name }
        } else {
            catalog
                .groupBy { it.countryCode }
                .map { entry ->
                    val stations = entry.value
                    CountryItem(
                        name = stations.first().country,
                        code = entry.key,
                        flag = flagFor(entry.key),
                        stationCount = stations.size
                    )
                }
                .sortedBy { it.name }
        }

        val items = if (filter.isBlank()) {
            all
        } else {
            all.filter { it.name.contains(filter, ignoreCase = true) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleBlock("WORLDWIDE COUNTRIES", "Select country to view stations"))

        val search = EditText(this).apply {
            hint = "Filter countries..."
            setTextColor(Color.WHITE)
            setHintTextColor(getColor(R.color.auto_text_muted))
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), 0, dp(14), 0)
            singleLine = true
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                bottomMargin = dp(8)
            }
        )

        val recycler = RecyclerView(this)
        recycler.layoutManager = GridLayoutManager(
            this,
            if (resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            ) 2 else 1
        )

        val adapter = CountryAdapter(items) { country ->
            loadAndRenderStations(
                title = country.name.uppercase() + " STATIONS",
                country = country.code,
                onBack = { renderCountries(search.text.toString()) }
            )
        }

        recycler.adapter = adapter
        search.addTextChangedListener(
            SimpleTextWatcher {
                val value = it.toString()
                adapter.submitList(
                    all.filter { item ->
                        item.name.contains(value, ignoreCase = true)
                    }
                )
            }
        )

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.addView(root)
    }

    private fun renderGenres() {
        val genres = if (remoteGenres.isNotEmpty()) {
            remoteGenres.sortedBy { it.name }
        } else {
            catalog
                .groupBy { it.genre }
                .map { GenreItem(it.key, it.value.size) }
                .sortedBy { it.name }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleBlock("BROWSE BY GENRE", "Music genres and talk categories"))

        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(
                this@MainActivity,
                if (resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ) 4 else 2
            )
            adapter = GenreAdapter(genres) { genre ->
                loadAndRenderStations(
                    title = genre.name.uppercase() + " STATIONS",
                    genre = genre.name,
                    onBack = { renderGenres() }
                )
            }
        }

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.addView(root)
    }

    private fun loadAndRenderStations(
        title: String,
        country: String? = null,
        genre: String? = null,
        onBack: () -> Unit
    ) {
        binding.activeAppTitle.text = "◉  RADIO WORLD AUTO  •  $title"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(titleBlock(title, "Loading worldwide catalog..."))
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(root)

        catalogRepository.loadStations(
                country = country,
                genre = genre,
                limit = 200
            ) { result ->
                result.onSuccess { stations ->
                    catalog.addAll(stations.filter { station ->
                        catalog.none { it.id == station.id }
                    })
                    applyPersistedState()
                    renderStationList(title, stations, onBack)
                }.onFailure {
                    renderStationList(title, emptyList(), onBack)
                    showPlayerState("CATALOG ERROR", "Unable to load stations")
                }
        }
    }

    private fun renderFavorites() {
        renderStationList(
            title = "FAVORITE STATIONS",
            stations = catalog.filter { it.favorite },
            onBack = { renderPlayer() }
        )
    }

    private fun renderRecents() {
        val stations = recentIds.mapNotNull { id ->
            catalog.find { it.id == id }
        }

        renderStationList(
            title = "RECENTLY PLAYED",
            stations = stations,
            onBack = { renderPlayer() }
        )
    }

    private fun renderStationList(
        title: String,
        stations: List<Station>,
        onBack: () -> Unit
    ) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(
            actionButton("‹") { onBack() },
            LinearLayout.LayoutParams(dp(62), dp(58))
        )

        top.addView(
            verticalText(
                title,
                "One-tap selection • " + stations.size + " stations",
                19f,
                11f
            ),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        root.addView(top, LinearLayout.LayoutParams(-1, dp(64)))

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = StationAdapter(
                stations,
                onPlay = { playStation(it) },
                onFavorite = {
                    it.favorite = !it.favorite
                    if (it.favorite) {
                        favoriteIds.add(it.id)
                    } else {
                        favoriteIds.remove(it.id)
                    }
                    persistFavorites()
                    renderStationList(title, stations, onBack)
                }
            )
        }

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.addView(root)
    }

    private fun renderSearch() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleBlock("AUTOMOTIVE SEARCH", "Station name, country or genre"))

        val input = EditText(this).apply {
            hint = "Type search..."
            setTextColor(Color.WHITE)
            setHintTextColor(getColor(R.color.auto_text_muted))
            textSize = 18f
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), 0, dp(14), 0)
            singleLine = true
        }

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(8)
            }
        )

        val results = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        val adapter = StationAdapter(
            emptyList(),
            onPlay = { playStation(it) },
            onFavorite = {
                it.favorite = !it.favorite
                results.adapter?.notifyDataSetChanged()
            }
        )

        results.adapter = adapter

        root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(buildSearchKeypad(input, adapter))

        input.addTextChangedListener(
            SimpleTextWatcher {
                val query = it.toString().trim()
                if (query.isBlank()) {
                    adapter.submitList(emptyList())
                } else {
                    catalogRepository.loadStations(
                        query = query,
                        limit = 50
                    ) { result ->
                        result.onSuccess { stations ->
                            catalog.addAll(stations.filter { station ->
                                catalog.none { it.id == station.id }
                            })
                            adapter.submitList(stations)
                        }
                    }
                }
            }
        )

        binding.contentContainer.addView(root)
    }

    private fun buildSearchKeypad(
        input: EditText,
        adapter: StationAdapter
    ): View {
        val grid = GridLayout(this).apply {
            columnCount = 10
            rowCount = 4
            setBackgroundResource(R.drawable.bg_surface)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { letter ->
            val button = keyButton(letter.toString()) {
                input.append(letter.toString())
                updateSearchResults(input.text.toString(), adapter)
            }

            grid.addView(
                button,
                GridLayout.LayoutParams().apply {
                    width = dp(46)
                    height = dp(44)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        val space = keyButton("SPACE") {
            input.append(" ")
            updateSearchResults(input.text.toString(), adapter)
        }

        grid.addView(
            space,
            GridLayout.LayoutParams().apply {
                width = dp(184)
                height = dp(44)
                columnSpec = GridLayout.spec(0, 4)
            }
        )

        val backspace = keyButton("⌫") {
            if (input.text.isNotEmpty()) {
                input.text.delete(input.text.length - 1, input.text.length)
            }
        }

        grid.addView(
            backspace,
            GridLayout.LayoutParams().apply {
                width = dp(92)
                height = dp(44)
                columnSpec = GridLayout.spec(4, 2)
            }
        )

        val clear = keyButton("CLEAR") {
            input.text.clear()
        }

        grid.addView(
            clear,
            GridLayout.LayoutParams().apply {
                width = dp(138)
                height = dp(44)
                columnSpec = GridLayout.spec(6, 3)
            }
        )

        return grid
    }

    private fun updateSearchResults(query: String, adapter: StationAdapter) {
        val q = query.trim()

        if (q.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        adapter.submitList(
            catalog.filter {
                it.name.contains(q, true) ||
                    it.country.contains(q, true) ||
                    it.genre.contains(q, true) ||
                    it.city.contains(q, true)
            }
        )
    }

    private fun playStation(station: Station) {
        currentStation = station
        currentStreamIndex = 0

        recentIds.remove(station.id)
        recentIds.addFirst(station.id)

        while (recentIds.size > 10) {
            recentIds.removeLast()
        }
        persistRecents()

        playCurrentStream()
        showScreen("PLAYER") { renderPlayer() }
    }

    private fun playCurrentStream() {
        val player = controller ?: return
        val station = currentStation ?: return

        if (currentStreamIndex >= station.streams.size) {
            showPlayerState("STREAM UNAVAILABLE", "No working stream")
            return
        }

        showPlayerState(
            "CONNECTING...",
            "Opening stream " + (currentStreamIndex + 1)
        )

        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(station.id + "-" + currentStreamIndex)
                .setUri(station.streams[currentStreamIndex])
                .build()
        )
        player.prepare()
        player.play()
    }

    private fun togglePlayPause() {
        val player = controller ?: return

        if (player.isPlaying) {
            player.pause()
        } else if (player.currentMediaItem == null) {
            playCurrentStream()
        } else {
            player.play()
        }

        renderPlayer()
    }

    private fun playNext() {
        val index = catalog.indexOf(currentStation)
        val next = catalog[(index + 1) % catalog.size]
        playStation(next)
    }

    private fun playPrevious() {
        val index = catalog.indexOf(currentStation)
        val previous = catalog[(index - 1 + catalog.size) % catalog.size]
        playStation(previous)
    }

    private fun updatePlayerButton() {
        if (::binding.isInitialized && binding.contentContainer.childCount > 0) {
            renderPlayer()
        }
    }

    private fun showPlayerState(title: String, message: String) {
        binding.activeAppTitle.text =
            "◉  RADIO WORLD AUTO  •  " + title + "  •  " + message
    }

    private fun showStationDetails(station: Station) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        content.addView(
            verticalText(
                flagFor(station.countryCode) + "  " + station.name,
                station.country + " • " + station.city,
                22f,
                13f
            )
        )

        content.addView(
            verticalText(
                "Genre: " + station.genre,
                "Language: " + station.language,
                15f,
                13f
            ).apply {
                setPadding(0, dp(18), 0, dp(12))
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Fallback streams: " + station.streams.size
                setTextColor(getColor(R.color.auto_success))
                textSize = 13f
            }
        )

        AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton("PLAY") { _, _ -> playStation(station) }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun actionButton(text: String, click: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = if (text.length <= 2) 24f else 11f
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_card)
            minHeight = dp(64)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }

    private fun keyButton(text: String, click: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_card)
            minHeight = dp(44)
            setOnClickListener { click() }
        }

    private fun titleBlock(title: String, subtitle: String): View =
        verticalText(title, subtitle, 20f, 12f).apply {
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }

    private fun verticalText(
        title: String,
        subtitle: String,
        titleSize: Float,
        subtitleSize: Float
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = titleSize
                    setTextColor(Color.WHITE)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(-1, dp(32))
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = subtitleSize
                    setTextColor(getColor(R.color.auto_accent))
                    maxLines = 1
                },
                LinearLayout.LayoutParams(-1, dp(24))
            )
        }

    private fun flagFor(code: String): String {
        if (code.length != 2) return "🌐"

        val upper = code.uppercase()
        val first = Character.codePointAt(upper, 0)
        val second = Character.codePointAt(upper, 1)

        return String(
            Character.toChars(0x1F1E6 + first - 'A'.code)
        ) + String(
            Character.toChars(0x1F1E6 + second - 'A'.code)
        )
    }


    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        catalogRepository.close()
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        super.onDestroy()
    }

    private class SimpleTextWatcher(
        private val onChanged: (CharSequence) -> Unit
    ) : android.text.TextWatcher {

        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = Unit

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            onChanged(s ?: "")
        }

        override fun afterTextChanged(
            s: android.text.Editable?
        ) = Unit
    }
