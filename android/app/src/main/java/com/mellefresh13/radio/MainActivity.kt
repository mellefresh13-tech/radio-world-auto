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
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val cacheExecutor = Executors.newSingleThreadExecutor()
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

    private var playerLogoView: ImageView? = null
    private var playerTrackView: TextView? = null
    private var playerArtistView: TextView? = null
    private var playerStatusView: TextView? = null
    private var playPauseIcon: ImageView? = null
    private var playPauseLabel: TextView? = null

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
            val station = currentStation ?: return
            val title = mediaMetadata.title?.toString()?.trim().orEmpty()
            val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()

            if (title.isNotBlank() && title != station.name) {
                playerTrackView?.text = title
                updateMarquee(playerTrackView)
            }
            if (artist.isNotBlank() && artist != station.name) {
                playerArtistView?.text = artist
                updateMarquee(playerArtistView)
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
        configureImmersiveWindow()
        applyCarSafeArea()

        setupNavigation()
        renderPlayer()

        userStateStore = UserStateStore(this)
        catalogCacheStore = CatalogCacheStore(this)
        cacheExecutor.execute {
            val cached = catalogCacheStore.load()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                cached?.let {
                    if (it.stations.isNotEmpty()) {
                        catalog = it.stations.toMutableList()
                        applyPersistedState()
                        currentStation = catalog.firstOrNull()
                        syncPlayerPlaylist()
                    }
                    remoteCountries = it.countries
                    remoteGenres = it.genres
                    renderPlayer()
                }
            }
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
                    saveCatalogCacheAsync()
                    renderPlayer()
                }
            }
        }

        catalogRepository.loadCountries { result ->
            result.onSuccess { countries ->
                remoteCountries = countries
                saveCatalogCacheAsync()
            }
        }

        catalogRepository.loadGenres { result ->
            result.onSuccess { genres ->
                remoteGenres = genres
                saveCatalogCacheAsync()
            }
        }
    }

    private fun saveCatalogCacheAsync() {
        val stations = catalog.toList()
        val countries = remoteCountries.toList()
        val genres = remoteGenres.toList()
        cacheExecutor.execute {
            catalogCacheStore.save(stations, countries, genres)
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

    private fun styleNavigationButtons() = Unit

    private fun configureImmersiveWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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
            R.id.navPlayer, R.id.navCountries, R.id.navGenres,
            R.id.navFavorites, R.id.navRecents, R.id.navSearch
        )
        ids.forEach { id ->
            findViewById<Button>(id).apply {
                val active = id == activeId
                setTextColor(getColor(if (active) R.color.auto_bg else R.color.auto_text_main))
                setBackgroundResource(
                    if (active) R.drawable.bg_nav_item_active else R.drawable.bg_nav_item
                )
                compoundDrawableTintList = ColorStateList.valueOf(
                    getColor(if (active) R.color.auto_bg else R.color.auto_text_muted)
                )
                alpha = 1f
                setHorizontallyScrolling(true)
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
            }
        }
    }

    private fun renderPlayer() {
        playerLogoView = null
        playerTrackView = null
        playerArtistView = null
        playerStatusView = null
        playPauseIcon = null
        playPauseLabel = null

        val station = currentStation ?: catalog.firstOrNull()
        if (station == null) {
            val root = screenRoot()
            root.addView(topBar(
                "RADIO WORLD", "Ready to tune in",
                "Choose a station to start listening", R.drawable.ic_radio
            ))
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundResource(R.drawable.bg_card)
            }
            empty.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_radio)
                imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
                setBackgroundResource(R.drawable.bg_icon_badge)
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(86), dp(86)))
            empty.addView(
                marqueeTextView("Your radio is ready", 25f, R.color.auto_text_main, true, false),
                LinearLayout.LayoutParams(-1, dp(38))
            )
            empty.addView(TextView(this).apply {
                text = "Browse the worldwide catalog or search directly for a station."
                textSize = 13f
                setTextColor(getColor(R.color.auto_text_muted))
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
            })
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, 0)
            }
            actions.addView(actionButton("COUNTRIES", R.drawable.ic_globe) {
                showScreen("COUNTRIES") { renderCountries() }
            }, LinearLayout.LayoutParams(dp(170), dp(52)).apply { marginEnd = dp(8) })
            actions.addView(actionButton("SEARCH", R.drawable.ic_search) {
                showScreen("SEARCH") { renderSearch() }
            }, LinearLayout.LayoutParams(dp(170), dp(52)))
            empty.addView(actions)
            root.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
            binding.contentContainer.setScreenContent(root)
            return
        }

        val root = screenRoot()
        val favorite = iconButton(
            if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
            "Favorite"
        ) {
            station.favorite = !station.favorite
            if (station.favorite) favoriteIds.add(station.id) else favoriteIds.remove(station.id)
            persistFavorites()
            renderPlayer()
        }
        val details = iconButton(R.drawable.ic_info, "Station details") {
            showStationDetails(station)
        }

        root.addView(topBar(
            "NOW PLAYING",
            station.name,
            listOf(station.country, station.city, station.genre)
                .filter { it.isNotBlank() }.joinToString("  •  "),
            R.drawable.ic_radio,
            listOf(favorite, details)
        ))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundResource(R.drawable.bg_card)
        }

        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundResource(R.drawable.bg_logo)
            contentDescription = station.name + " logo"
            setImageResource(R.drawable.ic_radio)
            imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
            tag = station.id
        }
        playerLogoView = logo
        loadStationLogo(station, logo)
        hero.addView(
            logo,
            LinearLayout.LayoutParams(dp(180), dp(180)).apply { marginEnd = dp(24) }
        )

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label("LIVE RADIO"))
        info.addView(
            marqueeTextView(station.name, 27f, R.color.auto_text_main, true),
            LinearLayout.LayoutParams(-1, dp(40))
        )
        info.addView(
            marqueeTextView(
                listOf(station.country, station.city, station.genre)
                    .filter { it.isNotBlank() }.joinToString("  •  "),
                13f, R.color.auto_text_muted
            ),
            LinearLayout.LayoutParams(-1, dp(28))
        )

        val track = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setBackgroundResource(R.drawable.bg_surface)
        }
        val trackTitle = marqueeTextView(
            station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast",
            21f, R.color.auto_text_main, true
        )
        val trackArtist = marqueeTextView(
            station.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata",
            12f, R.color.auto_text_muted
        )
        playerTrackView = trackTitle
        playerArtistView = trackArtist
        track.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(32)))
        track.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(22)))
        info.addView(track, LinearLayout.LayoutParams(-1, dp(72)))

        val status = TextView(this).apply {
            textSize = 10f
            includeFontPadding = false
            setPadding(0, dp(10), 0, 0)
        }
        playerStatusView = status
        info.addView(status, LinearLayout.LayoutParams(-1, dp(30)))
        hero.addView(info, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        val play = controlTile(
            if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play,
            if (controller?.isPlaying == true) "PAUSE" else "PLAY", true
        ) { togglePlayPause() }
        playPauseIcon = play.getChildAt(0) as ImageView
        playPauseLabel = play.getChildAt(1) as TextView
        controls.addView(
            play, LinearLayout.LayoutParams(0, dp(78), 1.8f).apply { marginEnd = dp(8) }
        )
        controls.addView(
            controlTile(R.drawable.ic_shuffle, "SHUFFLE") {
                catalog.randomOrNull()?.let { playStation(it) }
            },
            LinearLayout.LayoutParams(0, dp(78), 1f)
        )
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(94)))
        updatePlayerButton()
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
        val all = if (remoteCountries.isNotEmpty()) remoteCountries.sortedBy { it.name }
        else catalog.groupBy { it.countryCode }.map { CountryItem(it.value.first().country, it.key, flagFor(it.key), it.value.size) }.sortedBy { it.name }
        val root = screenRoot()
        root.addView(topBar("BROWSE", "Countries", "${all.size} countries with available radio", R.drawable.ic_globe))
        val search = EditText(this).apply {
            hint = "Search countries"; setTextColor(getColor(R.color.auto_text_main)); setHintTextColor(getColor(R.color.auto_text_muted))
            textSize = 16f; setSingleLine(true); setShowSoftInputOnFocus(false); setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), 0, dp(16), 0); setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dp(10); compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(12) })
        val columns = if (uiProfile.isLandscape) 3 else 2
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, columns)
            adapter = CountryAdapter(all.filter { filter.isBlank() || it.name.contains(filter, true) }) { country ->
                loadAndRenderStations(country.name, country = country.code, onBack = { renderCountries(search.text.toString()) })
            }
            setPadding(0, 0, 0, dp(8)); clipToPadding = false
        }
        search.addTextChangedListener(SimpleTextWatcher { q ->
            (recycler.adapter as CountryAdapter).submitList(all.filter { q.toString().isBlank() || it.name.contains(q.toString(), true) })
        })
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }

    private fun renderGenres() {
        val genres = if (remoteGenres.isNotEmpty()) remoteGenres.sortedBy { it.name }
        else catalog.groupBy { it.genre }.map { GenreItem(it.key, it.value.size) }.sortedBy { it.name }
        val root = screenRoot()
        root.addView(topBar("BROWSE", "Genres", "${genres.size} genres in the catalog", R.drawable.ic_music_note))
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, if (uiProfile.isLandscape) 4 else 2)
            adapter = GenreAdapter(genres) { genre -> loadAndRenderStations(genre.name, genre = genre.name, onBack = { renderGenres() }) }
            setPadding(0, 0, 0, dp(8)); clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }

    private fun loadAndRenderStations(title: String, country: String? = null, genre: String? = null, onBack: () -> Unit) {
        val root = screenRoot()
        root.addView(topBar("STATIONS", title, "Loading stations…", R.drawable.ic_list))
        binding.contentContainer.setScreenContent(root)
        catalogRepository.loadStations(country = country, genre = genre, limit = 200) { result ->
            result.onSuccess { stations ->
                catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } })
                applyPersistedState(); saveCatalogCacheAsync(); syncPlayerPlaylist()
                renderStationList(title, stations, onBack, country, genre, stations.size == 200)
            }.onFailure {
                renderStationList(title, emptyList(), onBack)
                showPlayerState("CATALOG ERROR", "Unable to load stations")
            }
        }
    }

    private fun renderFavorites() {
        renderSavedStations(ids = favoriteIds.toList(), title = "Favorites", columns = 2)
    }

    private fun renderRecents() {
        renderSavedStations(ids = recentIds.toList(), title = "Recently played", columns = 2)
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
                    saveCatalogCacheAsync()
                    ensureStationInPlaylist(station)
                }
                loadMissing(index + 1)
            }
        }

        loadMissing(0)
    }

    private fun renderStationList(
        title: String, stations: List<Station>, onBack: () -> Unit,
        country: String? = null, genre: String? = null, canLoadMore: Boolean = false, columns: Int = 1
    ) {
        val root = screenRoot()
        root.addView(topBar("STATIONS", title,
            if (stations.isEmpty()) "No stations found" else "${stations.size} stations available",
            R.drawable.ic_list, listOf(iconButton(R.drawable.ic_skip_previous, "Back") { onBack() })))
        val stationAdapter = StationAdapter(stations, onPlay = { playStation(it) }, onFavorite = {
            it.favorite = !it.favorite
            if (it.favorite) favoriteIds.add(it.id) else favoriteIds.remove(it.id)
            persistFavorites()
        })
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, if (uiProfile.isLandscape) 2 else 1)
            adapter = stationAdapter
            setPadding(0, 0, 0, dp(8)); clipToPadding = false
        }
        if (stations.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Nothing to show here yet."; textSize = 16f; gravity = Gravity.CENTER
                setTextColor(getColor(R.color.auto_text_muted))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        } else root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        if (canLoadMore && (country != null || genre != null)) {
            root.addView(actionButton("LOAD MORE") {
                catalogRepository.loadStations(country = country, genre = genre, limit = 200, offset = stations.size) { result ->
                    result.onSuccess { nextPage ->
                        val merged = (stations + nextPage).distinctBy { it.id }
                        catalog.addAll(nextPage.filter { station -> catalog.none { it.id == station.id } })
                        applyPersistedState(); syncPlayerPlaylist(); saveCatalogCacheAsync()
                        renderStationList(title, merged, onBack, country, genre, nextPage.size == 200)
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        }
        binding.contentContainer.setScreenContent(root)
    }

    private fun renderSearch() {
        val root = screenRoot()
        root.addView(topBar("FIND", "Search", "Station, city, country or genre", R.drawable.ic_search))
        val input = EditText(this).apply {
            hint = "Search radio stations"; setTextColor(getColor(R.color.auto_text_main)); setHintTextColor(getColor(R.color.auto_text_muted))
            textSize = 17f; setSingleLine(true); setShowSoftInputOnFocus(uiProfile.useOnScreenKeypad.not()); setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), 0, dp(16), 0); setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dp(10); compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
        val results = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, if (uiProfile.isLandscape) 2 else 1)
        }
        val adapter = StationAdapter(emptyList(), onPlay = { playStation(it) }, onFavorite = {
            it.favorite = !it.favorite
            if (it.favorite) favoriteIds.add(it.id) else favoriteIds.remove(it.id)
            persistFavorites(); results.adapter?.notifyDataSetChanged()
        })
        results.adapter = adapter
        root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        if (uiProfile.useOnScreenKeypad) root.addView(buildSearchKeypad(input, adapter))
        input.addTextChangedListener(SimpleTextWatcher { text ->
            val query = text.toString().trim(); val requestId = ++searchRequestId
            searchHandler.removeCallbacksAndMessages(null)
            if (query.isBlank()) adapter.submitList(emptyList())
            else if (query.length < 2) updateSearchResults(query, adapter)
            else searchHandler.postDelayed({
                catalogRepository.loadStations(query = query, limit = 50) { result ->
                    if (requestId != searchRequestId) return@loadStations
                    result.onSuccess { stations ->
                        catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } })
                        applyPersistedState(); adapter.submitList(stations)
                    }
                }
            }, 250L)
        })
        binding.contentContainer.setScreenContent(root)
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
        playerArtistView?.text = artist
        updateMarquee(playerArtistView)
        updateCurrentMediaMetadata(updated)
    }


    private fun updateCurrentMediaMetadata(station: Station) {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return
        val current = player.getMediaItemAt(index)
        val metadata = current.mediaMetadata.buildUpon()
            .setTitle(station.songTitle?.takeIf { it.isNotBlank() } ?: station.name)
            .setDisplayTitle(station.songTitle?.takeIf { it.isNotBlank() } ?: station.name)
            .setArtist(station.artist?.takeIf { it.isNotBlank() } ?: station.name)
            .setAlbumTitle(station.name)
            .setStation(station.name)
            .setGenre(station.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        player.replaceMediaItem(
            index,
            current.buildUpon().setMediaMetadata(metadata).build()
        )
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
        playerTrackView?.text = updated.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast"
        playerArtistView?.text = updated.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata"
        updateMarquee(playerTrackView)
        updateMarquee(playerArtistView)
        updateCurrentMediaMetadata(updated)
    }


    private fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause()
        else if (player.currentMediaItem == null) playCurrentStream()
        else player.play()
        updatePlayerButton()
    }

    private fun updatePlayerButton() {
        if (!::binding.isInitialized) return
        val playing = controller?.isPlaying == true
        playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"
        playerStatusView?.apply {
            text = when {
                controller?.playbackState == Player.STATE_BUFFERING -> "●  CONNECTING"
                playing -> "●  PLAYING"
                else -> "○  READY"
            }
            setTextColor(getColor(if (playing) R.color.auto_success else R.color.auto_text_muted))
        }
    }


    private fun showPlayerState(title: String, message: String) {
        Toast.makeText(this, "$title • $message", Toast.LENGTH_SHORT).show()
    }

    private fun showStationDetails(station:Station) {
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),0)}
        content.addView(verticalText(station.name,listOf(flagFor(station.countryCode),station.country,station.city,station.genre).filter{it.isNotBlank()}.joinToString("  •  "),24f,13f))
        content.addView(TextView(this).apply{text="LIVE STREAMS  •  "+station.streams.size;textSize=12f;setTextColor(getColor(R.color.auto_accent));setPadding(0,dp(20),0,dp(8))})
        content.addView(TextView(this).apply{text="Automatic stream fallback and reconnect are enabled.";textSize=14f;setTextColor(getColor(R.color.auto_text_muted))})
        AlertDialog.Builder(this).setTitle("Station details").setView(content).setPositiveButton("PLAY"){_,_->playStation(station)}.setNegativeButton("CLOSE",null).show()
    }

    private fun playerControlButton(text: String, iconRes: Int, weight: Float, heightDp: Int, accent: Boolean = false, click: () -> Unit): View =
        controlTile(iconRes, text, accent, click)

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
        removeAllViews()
        addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun actionButton(text:String,iconRes:Int?=null,click:()->Unit):Button=Button(this).apply{this.text=text;textSize=12f;setTextColor(getColor(R.color.auto_text_main));setBackgroundResource(R.drawable.bg_button);minWidth=0;minHeight=0;stateListAnimator=null;includeFontPadding=false;isAllCaps=false;gravity=Gravity.CENTER;setPadding(dp(14),0,dp(14),0);if(iconRes!=null){setCompoundDrawablesWithIntrinsicBounds(iconRes,0,0,0);compoundDrawablePadding=dp(8);compoundDrawableTintList=ColorStateList.valueOf(getColor(R.color.auto_text_main))};setOnClickListener{click()}}

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

    private fun titleBlock(title:String,subtitle:String,iconRes:Int?=null):View=topBar("SECTION",title,subtitle,iconRes?:R.drawable.ic_radio)

    private fun verticalText(title: String, subtitle: String, titleSize: Float, subtitleSize: Float): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = titleSize; setTextColor(getColor(R.color.auto_text_main))
            setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, dp((titleSize + 10).toInt())))
        addView(TextView(this@MainActivity).apply {
            text = subtitle; textSize = subtitleSize; setTextColor(getColor(R.color.auto_text_muted))
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, dp((subtitleSize + 8).toInt())))
    }

    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(26), dp(20), dp(26), dp(14))
    }

    private fun topBar(
        eyebrow: String,
        title: String,
        subtitle: String,
        icon: Int,
        right: List<View> = emptyList()
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(16))
        addView(
            ImageView(this@MainActivity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
                setBackgroundResource(R.drawable.bg_icon_badge)
                scaleType = ImageView.ScaleType.CENTER
            },
            LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(14) }
        )
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = eyebrow
                    textSize = 9f
                    setTextColor(getColor(R.color.auto_accent))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.12f
                    includeFontPadding = false
                })
                addView(
                    marqueeTextView(title, 26f, R.color.auto_text_main, true),
                    LinearLayout.LayoutParams(-1, dp(34))
                )
                addView(
                    marqueeTextView(subtitle, 11f, R.color.auto_text_muted),
                    LinearLayout.LayoutParams(-1, dp(18))
                )
            },
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )
        right.forEach {
            addView(it, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginStart = dp(7) })
        }
    }

    private fun marqueeTextView(
        value: String,
        size: Float,
        colorRes: Int,
        bold: Boolean = false,
        marquee: Boolean = true
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(colorRes))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        maxLines = 1
        isSingleLine = true
        if (marquee) {
            setHorizontallyScrolling(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
    }

    private fun updateMarquee(view: TextView?) {
        view?.apply {
            setHorizontallyScrolling(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 10f; setTextColor(getColor(R.color.auto_accent))
        setTypeface(typeface, android.graphics.Typeface.BOLD); includeFontPadding = false; letterSpacing = 0.08f
    }

    private fun controlTile(iconRes: Int, text: String, accent: Boolean = false, click: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundResource(if (accent) R.drawable.bg_giant_play else R.drawable.bg_button)
            isClickable = true; isFocusable = true; contentDescription = text; setOnClickListener { click() }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(if (accent) 34 else 28), dp(if (accent) 34 else 28)))
            addView(TextView(this@MainActivity).apply {
                this.text = text; textSize = if (accent) 10f else 9f
                setTextColor(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
                setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
                includeFontPadding = false; letterSpacing = 0.04f
            }, LinearLayout.LayoutParams(-1, dp(22)))
        }

    private fun buildSearchKeypad(input: EditText, adapter: StationAdapter): View {
        val grid=GridLayout(this).apply{columnCount=10;rowCount=4;setBackgroundResource(R.drawable.bg_surface);setPadding(dp(6),dp(6),dp(6),dp(6))}
        "QWERTYUIOPASDFGHJKLZXCVBNM".forEach{letter->
            val b=keyButton(letter.toString()){input.append(letter.toString());updateSearchResults(input.text.toString(),adapter)}
            grid.addView(b,GridLayout.LayoutParams().apply{width=dp(46);height=dp(44);setMargins(dp(2),dp(2),dp(2),dp(2))})
        }
        grid.addView(keyButton("SPACE"){input.append(" ");updateSearchResults(input.text.toString(),adapter)},GridLayout.LayoutParams().apply{width=dp(184);height=dp(44);columnSpec=GridLayout.spec(0,4)})
        grid.addView(keyButton("⌫"){if(input.text.isNotEmpty())input.text.delete(input.text.length-1,input.text.length)},GridLayout.LayoutParams().apply{width=dp(92);height=dp(44);columnSpec=GridLayout.spec(4,2)})
        grid.addView(keyButton("CLEAR"){input.text.clear()},GridLayout.LayoutParams().apply{width=dp(138);height=dp(44);columnSpec=GridLayout.spec(6,3)})
        return grid
    }

    private fun loadStationLogo(station: Station, target: ImageView) {
        target.tag = station.id
        target.setImageResource(R.drawable.ic_radio)
        target.imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
        val url = station.logo?.trim().orEmpty()
        if (url.isBlank()) return
        ImageLoader.load(url) { bitmap ->
            if (target.tag == station.id) {
                target.imageTintList = null
                target.setImageBitmap(bitmap)
            }
        }
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
        cacheExecutor.shutdownNow()
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
