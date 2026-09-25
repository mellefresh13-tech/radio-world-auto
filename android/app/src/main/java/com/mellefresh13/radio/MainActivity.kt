package com.mellefresh13.radio

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
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
    private lateinit var catalogCacheStore: CatalogCacheStore
    private val favoriteIds = mutableSetOf<String>()
    private var remoteCountries: List<CountryItem> = emptyList()
    private var remoteGenres: List<GenreItem> = emptyList()
    private var currentStation: Station? = null
    private var currentStreamIndex = 0
    private var searchRequestId = 0
    private var streamRetryCount = 0
    private var bufferingSinceMs: Long? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private val searchHandler = Handler(Looper.getMainLooper())
    private val recentIds = ArrayDeque<String>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                bufferingSinceMs = null
            }
            updatePlayerButton()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (bufferingSinceMs == null) {
                        bufferingSinceMs = System.currentTimeMillis()
                        retryHandler.postDelayed(
                            {
                                val station = currentStation ?: return@postDelayed
                                val player = controller ?: return@postDelayed
                                if (
                                    player.playbackState == Player.STATE_BUFFERING &&
                                    currentStation?.id == station.id &&
                                    currentStreamIndex + 1 < station.streams.size
                                ) {
                                    switchToNextStream("BUFFER TIMEOUT")
                                }
                            },
                            8_000L
                        )
                    }
                }
                Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> {
                    bufferingSinceMs = null
                }
            }
            updatePlayerButton()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val stationId = mediaItem?.mediaId ?: return
            if (stationId == currentStation?.id) return

            val station = catalog.firstOrNull { it.id == stationId } ?: return
            currentStation = station
            currentStreamIndex = 0
            streamRetryCount = 0
            bufferingSinceMs = null
            addRecentStation(station)
            renderPlayer()
        }

        override fun onMetadata(metadata: Metadata) {
            for (index in 0 until metadata.length()) {
                when (val entry = metadata[index]) {
                    is IcyInfo -> {
                        val title = entry.title?.trim().orEmpty()
                        if (title.isNotEmpty()) {
                            updateNowPlayingTitle(title)
                        }
                    }

                    is TextInformationFrame -> {
                        val value = entry.values.firstOrNull()?.trim().orEmpty()
                        when (entry.id) {
                            "TIT2" -> if (value.isNotEmpty()) {
                                updateNowPlayingTitle(value)
                            }
                            "TPE1" -> if (value.isNotEmpty()) {
                                updateNowPlayingArtist(value)
                            }
                        }
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (currentStation != null) {
                renderPlayer()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val station = currentStation ?: return

            if (currentStreamIndex + 1 < station.streams.size) {
                switchToNextStream("STREAM ERROR")
            } else if (streamRetryCount < 2) {
                streamRetryCount++
                showPlayerState(
                    "RECONNECTING...",
                    "Retry " + streamRetryCount + "/2"
                )
                retryHandler.postDelayed(
                    {
                        if (currentStation?.id == station.id) {
                            playCurrentStream()
                        }
                    },
                    if (streamRetryCount == 1) 1_500L else 3_500L
                )
            } else {
                showPlayerState("STREAM UNAVAILABLE", "No working stream")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyCarSafeArea()

        setupNavigation()

        userStateStore = UserStateStore(this)
        catalogCacheStore = CatalogCacheStore(this)
        catalogCacheStore.load()?.let { cached ->
            if (cached.stations.isNotEmpty()) {
                catalog = cached.stations.toMutableList()
                currentStation = catalog.firstOrNull()
            }
            remoteCountries = cached.countries
            remoteGenres = cached.genres
        }
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
                syncPlayerPlaylist()
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
                    currentStation = currentStation?.let { current ->
                        catalog.firstOrNull { it.id == current.id } ?: catalog.firstOrNull()
                    } ?: catalog.firstOrNull()
                    syncPlayerPlaylist()
                    catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
                    renderPlayer()
                }
            }
        }

        catalogRepository.loadCountries { result ->
            result.onSuccess { countries ->
                remoteCountries = countries
                catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
            }
        }

        catalogRepository.loadGenres { result ->
            result.onSuccess { genres ->
                remoteGenres = genres
                catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
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

    private fun applyCarSafeArea() {
        val profile = UiProfile.from(resources)
        if (profile.isCarReference) {
            binding.root.setPadding(
                profile.carSafeInsetPx,
                binding.root.paddingTop,
                binding.root.paddingRight,
                binding.root.paddingBottom
            )
        }
    }

    private fun setupNavigation() {
        binding.navPlayer.setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
        binding.navCountries.setOnClickListener { showScreen("COUNTRIES") { renderCountries() } }
        binding.navGenres.setOnClickListener { showScreen("GENRES") { renderGenres() } }
        binding.navFavorites.setOnClickListener { showScreen("FAVORITES") { renderFavorites() } }
        binding.navRecents.setOnClickListener { showScreen("RECENT") { renderRecents() } }
        binding.navSearch.setOnClickListener { showScreen("SEARCH") { renderSearch() } }

        styleNavigationButtons()
        setActiveNav(R.id.navPlayer)
    }

    private fun styleNavigationButtons() {
        val ids = intArrayOf(
            R.id.navPlayer,
            R.id.navCountries,
            R.id.navGenres,
            R.id.navFavorites,
            R.id.navRecents,
            R.id.navSearch
        )

        ids.forEach { id ->
            findViewById<Button>(id).apply {
                minWidth = 0
                minHeight = 0
                stateListAnimator = null
                includeFontPadding = false
                isAllCaps = false
                setPadding(dp(4), dp(2), dp(4), dp(2))
                textSize = if (uiProfile.isLandscape) 12f else 11f
            }
        }
    }

    private fun showScreen(title: String, content: () -> Unit) {
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
                if (id == activeId) R.drawable.bg_accent else R.drawable.bg_button
            )
        }
    }

    private fun renderPlayer() {
        val station = currentStation ?: catalog.firstOrNull() ?: return
        val profile = uiProfile

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(profile.contentPaddingDp),
                dp(profile.contentPaddingDp),
                dp(profile.contentPaddingDp),
                dp(profile.contentPaddingDp)
            )
        }

        val stationHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_surface)
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }

        val flag = TextView(this).apply {
            text = flagFor(station.countryCode)
            textSize = if (profile.isLandscape) 26f else 24f
            gravity = Gravity.CENTER
        }
        stationHeader.addView(flag, LinearLayout.LayoutParams(dp(42), -1))

        val headerText = verticalText(
            station.country.uppercase(),
            listOf(station.city, station.genre).filter { it.isNotBlank() }.joinToString(" • "),
            if (profile.isLandscape) 15f else 14f,
            11f
        )
        stationHeader.addView(headerText, LinearLayout.LayoutParams(0, -1, 1f))

        val favoriteHeader = iconButton(
            if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
            if (station.favorite) "Remove favorite" else "Add favorite"
        ) {
            station.favorite = !station.favorite
            if (station.favorite) favoriteIds.add(station.id) else favoriteIds.remove(station.id)
            persistFavorites()
            renderPlayer()
        }.apply {
            textSize = 22f
            contentDescription = if (station.favorite) "Remove favorite" else "Add favorite"
        }
        stationHeader.addView(
            favoriteHeader,
            LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                marginStart = dp(6)
            }
        )

        val details = actionButton("DETAILS", R.drawable.ic_info) { showStationDetails(station) }
        stationHeader.addView(
            details,
            LinearLayout.LayoutParams(
                if (profile.isLandscape) dp(100) else dp(86),
                dp(52)
            ).apply {
                marginStart = dp(6)
            }
        )

        root.addView(
            stationHeader,
            LinearLayout.LayoutParams(-1, dp(if (profile.isLandscape) 68 else 64))
        )

        if (profile.isLandscape) {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(14), dp(12), dp(14))
            }

            val logo = TextView(this).apply {
                text = station.name.firstOrNull()?.uppercase() ?: "R"
                textSize = if (profile.isCarReference) 72f else 52f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(getColor(R.color.auto_bg))
                setBackgroundResource(R.drawable.bg_accent)
            }
            body.addView(
                logo,
                LinearLayout.LayoutParams(dp(profile.playerLogoDp), dp(profile.playerLogoDp)).apply {
                    marginEnd = dp(if (profile.isCarReference) 28 else 18)
                }
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            info.addView(
                TextView(this).apply {
                    text = "NOW PLAYING"
                    textSize = 11f
                    setTextColor(getColor(R.color.auto_accent))
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(-1, dp(22))
            )

            info.addView(
                TextView(this).apply {
                    text = station.name
                    textSize = if (profile.isCarReference) 30f else 24f
                    setTextColor(getColor(R.color.auto_text_main))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(-1, dp(if (profile.isCarReference) 44 else 38))
            )

            val trackBox = verticalText(
                station.songTitle ?: station.name,
                station.artist ?: "Live Broadcast Stream",
                if (profile.isCarReference) 22f else 18f,
                13f
            ).apply {
                setBackgroundResource(R.drawable.bg_card)
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            info.addView(
                trackBox,
                LinearLayout.LayoutParams(-1, dp(if (profile.isCarReference) 88 else 74))
            )

            info.addView(buildVolumeSeekBar(), LinearLayout.LayoutParams(-1, dp(46)))

            body.addView(info, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_surface)
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }

            controls.addView(
                playerControlButton("SOURCE", R.drawable.ic_list, 1.20f, 56) {
                    showScreen("COUNTRIES") { renderCountries() }
                }
            )
            controls.addView(
                playerControlButton("", R.drawable.ic_skip_previous, 0.85f, 64) { playPrevious() }
            )
            controls.addView(
                playerControlButton(
                    "",
                    if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play,
                    1.00f,
                    72,
                    accent = true
                ) {
                    togglePlayPause()
                }
            )
            controls.addView(
                playerControlButton("", R.drawable.ic_skip_next, 0.85f, 64) { playNext() }
            )
            controls.addView(
                playerControlButton("SHUFFLE", R.drawable.ic_shuffle, 1.20f, 56) {
                    catalog.randomOrNull()?.let { playStation(it) }
                }
            )

            root.addView(controls, LinearLayout.LayoutParams(-1, dp(84)))
        } else {
            val hero = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(6))
            }

            val logo = TextView(this).apply {
                text = station.name.firstOrNull()?.uppercase() ?: "R"
                textSize = 48f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(getColor(R.color.auto_bg))
                setBackgroundResource(R.drawable.bg_accent)
            }
            hero.addView(
                logo,
                LinearLayout.LayoutParams(
                    dp(profile.playerLogoDp),
                    dp(profile.playerLogoDp)
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(8)
                }
            )

            hero.addView(
                TextView(this).apply {
                    text = station.name
                    textSize = 23f
                    setTextColor(getColor(R.color.auto_text_main))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, dp(36))
            )

            hero.addView(
                verticalText(
                    station.songTitle ?: station.name,
                    station.artist ?: "Live Broadcast Stream",
                    17f,
                    12f
                ).apply {
                    setBackgroundResource(R.drawable.bg_card)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                },
                LinearLayout.LayoutParams(-1, dp(66)).apply {
                    topMargin = dp(4)
                }
            )

            hero.addView(buildVolumeSeekBar(), LinearLayout.LayoutParams(-1, dp(42)))
            root.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.bg_surface)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            controls.addView(actionButton("◀") { playPrevious() })
            controls.addView(
                actionButton(if (controller?.isPlaying == true) "❚❚" else "▶") {
                    togglePlayPause()
                }
            )
            controls.addView(actionButton("▶") { playNext() })
            root.addView(
                controls,
                LinearLayout.LayoutParams(-1, dp(profile.controlHeightDp))
            )

            val secondary = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            secondary.addView(
                actionButton("BROWSE") {
                    showScreen("COUNTRIES") { renderCountries() }
                },
                LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    marginEnd = dp(4)
                }
            )
            secondary.addView(
                actionButton("SHUFFLE") {
                    catalog.randomOrNull()?.let { playStation(it) }
                },
                LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    marginStart = dp(4)
                }
            )
            root.addView(secondary, LinearLayout.LayoutParams(-1, dp(58)))
        }

        binding.contentContainer.setScreenContent(root)
    }

    private fun buildVolumeSeekBar(): SeekBar =
        SeekBar(this).apply {
            max = 100
            progress = ((controller?.volume ?: 0.8f) * 100).toInt()
            contentDescription = "Volume"
            setPadding(dp(4), 0, dp(4), 0)
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            controller?.volume = progress / 100f
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                }
            )
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

        root.addView(titleBlock("WORLDWIDE COUNTRIES", "Select country to view stations", R.drawable.ic_globe))

        val search = EditText(this).apply {
            hint = "Filter countries..."
            setTextColor(Color.WHITE)
            setHintTextColor(getColor(R.color.auto_text_muted))
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), 0, dp(14), 0)
            setSingleLine(true)
            setShowSoftInputOnFocus(false)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dp(10)
            compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted))
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                bottomMargin = dp(8)
            }
        )

        val recycler = RecyclerView(this)
        recycler.layoutManager = GridLayoutManager(this@MainActivity, 3)

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
        binding.contentContainer.setScreenContent(root)
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

        root.addView(titleBlock("BROWSE BY GENRE", "Music genres and talk categories", R.drawable.ic_grid))

        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = GenreAdapter(genres) { genre ->
                loadAndRenderStations(
                    title = genre.name.uppercase() + " STATIONS",
                    genre = genre.name,
                    onBack = { renderGenres() }
                )
            }
        }

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }

    private fun loadAndRenderStations(
        title: String,
        country: String? = null,
        genre: String? = null,
        onBack: () -> Unit
    ) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(titleBlock(title, "Loading worldwide catalog...", R.drawable.ic_list))
        binding.contentContainer.removeAllViews()
        binding.contentContainer.setScreenContent(root)

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
                    catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
                    syncPlayerPlaylist()
                    renderStationList(
                    title,
                    stations,
                    onBack,
                    country = country,
                    genre = genre,
                    canLoadMore = stations.size == 200
                )
                }.onFailure {
                    renderStationList(title, emptyList(), onBack)
                    showPlayerState("CATALOG ERROR", "Unable to load stations")
                }
        }
    }

    private fun renderFavorites() {
        renderSavedStations(
            ids = favoriteIds.toList(),
            title = "FAVORITE STATIONS",
            columns = 3
        )
    }

    private fun renderRecents() {
        renderSavedStations(
            ids = recentIds.toList(),
            title = "RECENTLY PLAYED",
            columns = 3
        )
    }

    private fun renderSavedStations(
        ids: List<String>,
        title: String,
        columns: Int = 1
    ) {
        val loaded = ids.mapNotNull { id ->
            catalog.find { it.id == id } ?: catalogCacheStore.findStation(id)
        }.toMutableList()
        val missing = ids.filterNot { id -> loaded.any { it.id == id } }

        if (missing.isEmpty()) {
            renderStationList(
                title = title,
                stations = loaded,
                onBack = { renderPlayer() },
                columns = columns
            )
            return
        }

        setActiveNav(
            if (title.startsWith("FAVORITE")) R.id.navFavorites
            else R.id.navRecents
        )
        binding.contentContainer.removeAllViews()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(titleBlock(title, "Loading saved stations...", if (title.startsWith("FAVORITE")) R.drawable.ic_star_filled else R.drawable.ic_history))
        binding.contentContainer.setScreenContent(root)

        fun loadMissing(index: Int) {
            if (index >= missing.size) {
                applyPersistedState()
                renderStationList(
                    title = title,
                    stations = ids.mapNotNull { id ->
                        catalog.find { it.id == id }
                    },
                    onBack = { renderPlayer() },
                    columns = columns
                )
                return
            }

            catalogCacheStore.findStation(missing[index])?.let { cached ->
                if (catalog.none { it.id == cached.id }) {
                    catalog.add(cached)
                }
                ensureStationInPlaylist(cached)
                loadMissing(index + 1)
                return
            }

            catalogRepository.loadStation(missing[index]) { result ->
                result.onSuccess { station ->
                    if (catalog.none { it.id == station.id }) {
                        catalog.add(station)
                    }
                    catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
                    ensureStationInPlaylist(station)
                }
                loadMissing(index + 1)
            }
        }

        loadMissing(0)
    }

    private fun renderStationList(
        title: String,
        stations: List<Station>,
        onBack: () -> Unit,
        country: String? = null,
        genre: String? = null,
        canLoadMore: Boolean = false,
        columns: Int = 1
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
            LinearLayout.LayoutParams(dp(if (uiProfile.isCarReference) 70 else 58), dp(56))
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
            layoutManager = GridLayoutManager(this@MainActivity, columns)
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
                    renderStationList(
                        title,
                        stations,
                        onBack,
                        country,
                        genre,
                        canLoadMore
                    )
                }
            )
        }

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        if (canLoadMore && (country != null || genre != null)) {
            root.addView(
                actionButton("LOAD MORE") {
                    catalogRepository.loadStations(
                        country = country,
                        genre = genre,
                        limit = 200,
                        offset = stations.size
                    ) { result ->
                        result.onSuccess { nextPage ->
                            val merged = (stations + nextPage)
                                .distinctBy { it.id }
                            catalog.addAll(nextPage.filter { station ->
                                catalog.none { it.id == station.id }
                            })
                            applyPersistedState()
                            catalogCacheStore.save(catalog, remoteCountries, remoteGenres)
                            syncPlayerPlaylist()
                            renderStationList(
                                title,
                                merged,
                                onBack,
                                country,
                                genre,
                                canLoadMore = nextPage.size == 200
                            )
                        }
                    }
                },
                LinearLayout.LayoutParams(-1, dp(66))
            )
        }

        binding.contentContainer.setScreenContent(root)
    }

    private fun renderSearch() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleBlock("AUTOMOTIVE SEARCH", "Station name, country or genre", R.drawable.ic_search))

        val input = EditText(this).apply {
            hint = "Type search..."
            setTextColor(Color.WHITE)
            setHintTextColor(getColor(R.color.auto_text_muted))
            textSize = if (uiProfile.isCarReference) 20f else 17f
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), 0, dp(14), 0)
            setSingleLine(true)
            setShowSoftInputOnFocus(uiProfile.useOnScreenKeypad.not())
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dp(10)
            compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted))
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
                if (it.favorite) {
                    favoriteIds.add(it.id)
                } else {
                    favoriteIds.remove(it.id)
                }
                persistFavorites()
                results.adapter?.notifyDataSetChanged()
            }
        )

        results.adapter = adapter

        root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))

        if (uiProfile.useOnScreenKeypad) {
            root.addView(buildSearchKeypad(input, adapter))
        }

        input.addTextChangedListener(
            SimpleTextWatcher {
                val query = it.toString().trim()
                val requestId = ++searchRequestId
                searchHandler.removeCallbacksAndMessages(null)

                if (query.isBlank()) {
                    adapter.submitList(emptyList())
                } else if (query.length < 2) {
                    updateSearchResults(query, adapter)
                } else {
                    searchHandler.postDelayed(
                        {
                            catalogRepository.loadStations(
                                query = query,
                                limit = 50
                            ) { result ->
                                if (requestId == searchRequestId) {
                                    result.onSuccess { stations ->
                                        catalog.addAll(stations.filter { station ->
                                            catalog.none { it.id == station.id }
                                        })
                                        applyPersistedState()
                                        adapter.submitList(stations)
                                    }
                                }
                            }
                        },
                        250L
                    )
                }
            }
        )

        binding.contentContainer.setScreenContent(root)
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
        streamRetryCount = 0
        bufferingSinceMs = null
        retryHandler.removeCallbacksAndMessages(null)

        ensureStationInPlaylist(station)
        controller?.let { player ->
            val index = (0 until player.mediaItemCount)
                .firstOrNull { player.getMediaItemAt(it).mediaId == station.id }
            if (index != null) {
                player.seekTo(index, 0L)
                player.play()
            } else {
                playCurrentStream()
            }
        }

        addRecentStation(station)
        showScreen("PLAYER") { renderPlayer() }
    }

    private fun playCurrentStream() {
        val player = controller ?: return
        val station = currentStation ?: return

        if (station.streams.isEmpty()) {
            showPlayerState("STREAM UNAVAILABLE", "No working stream")
            return
        }

        ensureStationInPlaylist(station)

        val index = (0 until player.mediaItemCount)
            .firstOrNull { player.getMediaItemAt(it).mediaId == station.id }
            ?: return

        showPlayerState(
            "CONNECTING...",
            "Opening stream " + (currentStreamIndex + 1)
        )

        player.replaceMediaItem(
            index,
            stationToMediaItem(station, currentStreamIndex)
        )
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
    }

    private fun addRecentStation(station: Station) {
        recentIds.remove(station.id)
        recentIds.addFirst(station.id)
        while (recentIds.size > 10) {
            recentIds.removeLast()
        }
        persistRecents()
    }

    private fun syncPlayerPlaylist() {
        val player = controller ?: return
        if (player.mediaItemCount > 0) return

        val items = catalog
            .filter { it.streams.isNotEmpty() }
            .distinctBy { it.id }
            .take(200)
            .map { stationToMediaItem(it, 0) }

        if (items.isNotEmpty()) {
            player.setMediaItems(items, false)
        }
    }

    private fun ensureStationInPlaylist(station: Station) {
        val player = controller ?: return
        val exists = (0 until player.mediaItemCount)
            .any { player.getMediaItemAt(it).mediaId == station.id }
        if (!exists) {
            player.addMediaItem(stationToMediaItem(station, 0))
        }
    }

    private fun updateNowPlayingArtist(artist: String) {
        val station = currentStation ?: return
        val updated = station.copy(artist = artist)
        currentStation = updated
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()

        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return

        val current = player.getMediaItemAt(index)
        val metadata = current.mediaMetadata.buildUpon()
            .setTitle(updated.songTitle ?: updated.name)
            .setDisplayTitle(updated.songTitle ?: updated.name)
            .setArtist(artist)
            .setAlbumTitle(updated.name)
            .setStation(updated.name)
            .setGenre(updated.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()

        player.replaceMediaItem(
            index,
            current.buildUpon().setMediaMetadata(metadata).build()
        )
        renderPlayer()
    }

    private fun stationToMediaItem(station: Station, streamIndex: Int): MediaItem {
        val stream = station.streams.getOrNull(streamIndex) ?: station.streams.first()
        val title = station.songTitle?.takeIf { it.isNotBlank() } ?: station.name
        val artist = station.artist?.takeIf { it.isNotBlank() } ?: station.name
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(stream)
            .setTag(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(station.name)
                    .setStation(station.name)
                    .setGenre(station.genre)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build()
            )
            .build()
    }

    private fun switchToNextStream(reason: String) {
        val station = currentStation ?: return
        val player = controller ?: return
        if (currentStreamIndex + 1 >= station.streams.size) return

        currentStreamIndex++
        showPlayerState(reason, "Opening stream " + (currentStreamIndex + 1))
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex))
        player.prepare()
        player.play()
    }

    private fun updateNowPlayingTitle(rawTitle: String) {
        val station = currentStation ?: return
        val parts = rawTitle.split(" - ", limit = 2)
        val updated = if (parts.size == 2) {
            station.copy(songTitle = parts[1].trim(), artist = parts[0].trim())
        } else {
            station.copy(songTitle = rawTitle, artist = station.artist ?: station.name)
        }
        currentStation = updated
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()

        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return
        val current = player.getMediaItemAt(index)
        val metadata = current.mediaMetadata.buildUpon()
            .setTitle(updated.songTitle ?: updated.name)
            .setDisplayTitle(updated.songTitle ?: updated.name)
            .setArtist(updated.artist ?: updated.name)
            .setAlbumTitle(updated.name)
            .setStation(updated.name)
            .setGenre(updated.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
        renderPlayer()
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
        val player = controller
        if (player != null && player.mediaItemCount > 1) {
            player.seekToNextMediaItem()
            player.play()
            return
        }
        val index = catalog.indexOf(currentStation).coerceAtLeast(0)
        if (catalog.isNotEmpty()) {
            playStation(catalog[(index + 1) % catalog.size])
        }
    }

    private fun playPrevious() {
        val player = controller
        if (player != null && player.mediaItemCount > 1) {
            player.seekToPreviousMediaItem()
            player.play()
            return
        }
        val index = catalog.indexOf(currentStation).coerceAtLeast(0)
        if (catalog.isNotEmpty()) {
            playStation(catalog[(index - 1 + catalog.size) % catalog.size])
        }
    }

    private fun updatePlayerButton() {
        if (::binding.isInitialized && binding.contentContainer.childCount > 0) {
            renderPlayer()
        }
    }

    private fun showPlayerState(title: String, message: String) {
        Toast.makeText(this, "$title • $message", Toast.LENGTH_SHORT).show()
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

    private fun playerControlButton(
        text: String,
        iconRes: Int,
        weight: Float,
        heightDp: Int,
        accent: Boolean = false,
        click: () -> Unit
    ): Button =
        Button(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            setBackgroundResource(if (accent) R.drawable.bg_giant_play else R.drawable.bg_icon_button)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            includeFontPadding = false
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            compoundDrawablePadding = dp(6)
            compoundDrawableTintList = ColorStateList.valueOf(
                getColor(if (accent) R.color.auto_bg else R.color.auto_text_main)
            )
            setOnClickListener { click() }
            contentDescription = if (text.isBlank()) "Player control" else text
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
        }

    private fun iconButton(iconRes: Int, description: String, click: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_icon_button)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            setOnClickListener { click() }
        }

    private fun FrameLayout.setScreenContent(view: View) {
        view.alpha = 0f
        removeAllViews()
        addView(view, FrameLayout.LayoutParams(-1, -1))
        view.animate().alpha(1f).setDuration(160L).start()
    }

    private fun actionButton(text: String, iconRes: Int? = null, click: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = when {
                text.length <= 2 -> if (uiProfile.isLandscape) 23f else 21f
                text.length >= 8 -> 10f
                else -> 12f
            }
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_button)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            includeFontPadding = false
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener { click() }
            if (iconRes != null) {
                setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
                compoundDrawablePadding = dp(6)
                compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
            }
            contentDescription = text
            layoutParams = LinearLayout.LayoutParams(0, dp(uiProfile.controlHeightDp), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }

    private fun keyButton(text: String, click: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_button)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            includeFontPadding = false
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { click() }
        }

    private fun titleBlock(title: String, subtitle: String, iconRes: Int? = null): View {
        val text = verticalText(title, subtitle, if (uiProfile.isCarReference) 23f else 20f, 12f)
        if (iconRes == null) return text.apply { setPadding(dp(4), dp(4), dp(4), dp(10)) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                },
                LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
            )
            addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        }
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
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(-1, dp(28))
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = subtitleSize
                    setTextColor(getColor(R.color.auto_accent))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(-1, dp(22))
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


    private val uiProfile: UiProfile
        get() = UiProfile.from(resources)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        retryHandler.removeCallbacksAndMessages(null)
        searchHandler.removeCallbacksAndMessages(null)
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
}
