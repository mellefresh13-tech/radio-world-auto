package com.mellefresh13.radio

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
import kotlin.math.abs

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
    private var restoredStationId: String? = null
    private var restoringAfterConfig = false
    private var searchRequestId = 0
    private var streamRetryCount = 0
    private var bufferingSinceMs: Long? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private val searchHandler = Handler(Looper.getMainLooper())
    private val recentIds = ArrayDeque<String>()
    private var playerLogoView: ImageView? = null
    private var playerBackdropView: ImageView? = null
    private var playerTrackView: TextView? = null
    private var playerStationView: TextView? = null
    private var playerMetaView: TextView? = null
    private var playerArtistView: TextView? = null
    private var miniFavoriteView: ImageView? = null
    private var miniPlayPauseIcon: ImageView? = null
    private var miniStationView: TextView? = null
    private var miniTrackView: TextView? = null
    private var miniLogoView: ImageView? = null
    private var syncStatusView: TextView? = null
    private var syncActive = false
    private var activeNavId: Int = R.id.navPlayer
    private var navNowLogoView: ImageView? = null
    private var navNowFavoriteView: ImageView? = null
    private var navNowStationView: TextView? = null
    private var navNowTrackView: TextView? = null
    private var lastCountryCode: String? = null
    private var lastCountryTitle: String? = null
    private var lastCountryPosition: Int = 0
    private var lastGenreName: String? = null
    private var lastGenrePosition: Int = 0
    private var playerStatusView: TextView? = null
    private var playerOffline = false
    private var playerReconnecting = false
    private var startupPlaybackRestored = false
    private val failedStationIds = mutableSetOf<String>()
    private var catalogReady = false
    private var playPauseIcon: ImageView? = null
    private var playPauseLabel: TextView? = null
    private var playerFavoriteButton: ImageView? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) { bufferingSinceMs = null; playerOffline = false; playerReconnecting = false; currentStation?.let { failedStationIds.remove(it.id) } }
            updatePlayerButton()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (bufferingSinceMs == null) {
                    bufferingSinceMs = System.currentTimeMillis()
                    retryHandler.postDelayed({
                        val station = currentStation ?: return@postDelayed
                        val player = controller ?: return@postDelayed
                        if (player.playbackState == Player.STATE_BUFFERING && currentStation?.id == station.id) { if (currentStreamIndex + 1 < station.streams.size) switchToNextStream("BUFFER TIMEOUT") else switchToNextStation("BUFFER TIMEOUT") }
                    }, 8_000L)
                }
                Player.STATE_READY -> { bufferingSinceMs = null; playerOffline = false; if (controller?.isPlaying != true) playerReconnecting = false }
                Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null
            }
            updatePlayerButton()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val stationId = mediaItem?.mediaId ?: return
            val station = catalog.firstOrNull { it.id == stationId } ?: return
            currentStation = station
            restoredStationId = station.id
            currentStreamIndex = 0
            streamRetryCount = 0
            bufferingSinceMs = null
            addRecentStation(station)
            updateCurrentStationUi(station)
        }
        override fun onMetadata(metadata: Metadata) {
            for (index in 0 until metadata.length()) {
                when (val entry = metadata[index]) {
                    is IcyInfo -> entry.title?.trim()?.takeIf { it.isNotEmpty() }?.let(::updateNowPlayingTitle)
                    is TextInformationFrame -> {
                        val value = entry.values.firstOrNull()?.trim().orEmpty()
                        when (entry.id) {
                            "TIT2" -> if (value.isNotEmpty()) updateNowPlayingTitle(value)
                            "TPE1" -> if (value.isNotEmpty()) updateNowPlayingArtist(value)
                        }
                    }
                }
            }
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val station = currentStation ?: return
            val title = mediaMetadata.title?.toString()?.trim().orEmpty()
            val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
            if (title.isNotBlank() && title != station.name) updateNowPlayingTitle(if (artist.isNotBlank() && artist != station.name) "$artist - $title" else title)
            else if (artist.isNotBlank() && artist != station.name) updateNowPlayingArtist(artist)
        }
        override fun onPlayerError(error: PlaybackException) {
            val station = currentStation ?: return
            if (currentStreamIndex + 1 < station.streams.size) switchToNextStream("STREAM ERROR")
            else if (streamRetryCount < 2) {
                streamRetryCount++
                playerReconnecting = true
                playerOffline = false
                showPlayerState("RECONNECTING...", "Retry " + streamRetryCount + "/2")
                retryHandler.postDelayed({ if (currentStation?.id == station.id) playCurrentStream() }, if (streamRetryCount == 1) 1_500L else 3_500L)
            } else { playerOffline = true; playerReconnecting = false; switchToNextStation("STREAM UNAVAILABLE") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredStationId = savedInstanceState?.getString(KEY_STATION_ID)
        restoringAfterConfig = savedInstanceState != null
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureImmersiveWindow()
        applyCarSafeArea()
        setupNavigation()
        ImageLoader.initialize(this)
        renderPlayer()
        userStateStore = UserStateStore(this)
        catalogCacheStore = CatalogCacheStore(this)
        catalogRepository = ApiCatalogRepository(this, onProgress = { bytes, total -> runOnUiThread { updateSyncProgress(bytes, total) } })
        cacheExecutor.execute {
            val cached = catalogCacheStore.load()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shouldRefreshCatalog = cached == null ||
                    cached.savedAt <= 0L ||
                    System.currentTimeMillis() - cached.savedAt >= CATALOG_REFRESH_MS
                cached?.let {
                    if (it.stations.isNotEmpty()) {
                        catalog = it.stations.toMutableList()
                        catalogReady = true
                        invalidateUnavailableRestoredStation()
                        applyPersistedState()
                        restoreStationFromState()
                        syncPlayerPlaylist()
                        restorePlaybackIfNeeded()
                    }
                    remoteCountries = it.countries
                    remoteGenres = it.genres
                    renderActiveScreen()
                }
                if (shouldRefreshCatalog) {
                    loadRemoteCatalog()
                }
            }
        }
        favoriteIds.clear(); favoriteIds.addAll(userStateStore.loadFavoriteIds())
        recentIds.addAll(userStateStore.loadRecentIds().take(10))
        if (restoredStationId == null) restoredStationId = recentIds.firstOrNull()
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            resolveCurrentStationFromPlayer()
            syncPlayerPlaylist()
            restoreStationFromState()
            restorePlaybackIfNeeded()
            renderPlayer()
        }, MoreExecutors.directExecutor())
    }


    private fun loadRemoteCatalog() {
        syncActive = true
        updateSyncProgress(0L, 0L)
        catalogRepository.loadStations(limit = 50_000) { result ->
            result.onSuccess { stations ->
                if (stations.isNotEmpty()) {
                    catalog = stations.toMutableList()
                    catalogReady = true
                    invalidateUnavailableRestoredStation()
                    applyPersistedState()
                    restoreStationFromState()
                    syncPlayerPlaylist()
                    restorePlaybackIfNeeded()
                    catalogRepository.loadCountries { countriesResult ->
                        countriesResult.onSuccess { countries -> remoteCountries = countries }
                        saveCatalogCacheAsync()
                    }
                    catalogRepository.loadGenres { genresResult ->
                        genresResult.onSuccess { genres -> remoteGenres = genres }
                        saveCatalogCacheAsync()
                    }
                    saveCatalogCacheAsync()
                    finishSyncProgress()
                    renderActiveScreen()
                } else {
                    finishSyncProgress()
                }
            }.onFailure {
                finishSyncProgress()
            }
        }
    }

    private fun invalidateUnavailableRestoredStation() {
        val wantedId = restoredStationId ?: currentStation?.id ?: return
        if (catalog.none { it.id == wantedId }) {
            restoredStationId = null
            currentStation = null
        }
    }

    private fun restoreStationFromState() {
        val wantedId = restoredStationId
            ?: controller?.currentMediaItem?.mediaId
            ?: if (catalogReady) userStateStore.loadRecentIds().firstOrNull() else null
        val restored = wantedId?.let { id -> catalog.firstOrNull { it.id == id } }
        if (restored != null) currentStation = restored
        else if (catalogReady && !restoringAfterConfig && currentStation == null) currentStation = catalog.firstOrNull()
        if (currentStation != null) restoredStationId = currentStation?.id
    }

    private fun restorePlaybackIfNeeded() {
        if (!catalogReady || restoringAfterConfig || startupPlaybackRestored) return
        val player = controller ?: return
        val station = currentStation ?: catalog.firstOrNull() ?: return
        if (player.isPlaying || player.playbackState == Player.STATE_READY) { startupPlaybackRestored = true; return }
        ensureStationInPlaylist(station)
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id } ?: return
        currentStreamIndex = 0; streamRetryCount = 0; failedStationIds.clear()
        showPlayerState("CONNECTING...", "Restoring last station")
        player.seekTo(index, 0L); player.prepare(); player.play(); startupPlaybackRestored = true
    }

    private fun resolveCurrentStationFromPlayer() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        val station = catalog.firstOrNull { it.id == id } ?: return
        currentStation = station
        restoredStationId = station.id
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_STATION_ID, currentStation?.id ?: controller?.currentMediaItem?.mediaId)
        super.onSaveInstanceState(outState)
    }

    private fun saveCatalogCacheAsync() { val stations = catalog.toList(); val countries = remoteCountries.toList(); val genres = remoteGenres.toList(); cacheExecutor.execute { catalogCacheStore.save(stations, countries, genres) } }
    private fun applyPersistedState() { catalog.forEach { it.favorite = favoriteIds.contains(it.id) } }
    private fun persistFavorites() { userStateStore.saveFavoriteIds(favoriteIds) }
    private fun persistRecents() { userStateStore.saveRecentIds(recentIds) }
    private fun applyCarSafeArea() {
        val profile = UiProfile.from(resources)
        val nav = binding.navContainer
        val brandIcon = binding.navBrandIcon
        val brandLabel = binding.navBrandLabel

        val useLandscapeSidebar = profile.isLandscape && profile.widthDp >= 700
        if (useLandscapeSidebar) {
            binding.root.orientation = LinearLayout.HORIZONTAL
            binding.root.setPadding(
                if (profile.isCarReference) profile.carSafeInsetPx else 0,
                0,
                0,
                0
            )
            binding.contentContainer.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)

            nav.orientation = LinearLayout.VERTICAL
            nav.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            nav.layoutParams = LinearLayout.LayoutParams(dp(profile.sidebarWidthDp), -1)
            nav.setPadding(
                dp(if (profile.isPhoneLandscape) 10 else 12),
                dp(if (profile.isPhoneLandscape) 14 else 18),
                dp(if (profile.isPhoneLandscape) 10 else 12),
                dp(if (profile.isPhoneLandscape) 14 else 18)
            )
            navNowLogoView = null
            navNowFavoriteView = null
            navNowStationView = null
            navNowTrackView = null
            if (profile.isPhoneLandscape) {
                setupPhoneLandscapeSidebar()
            } else {
                brandIcon.visibility = View.VISIBLE
                brandLabel.visibility = View.VISIBLE
                binding.navNowPlayingCard.visibility = View.GONE
                styleNavButtons(landscape = true)
            }
        } else {
            binding.root.orientation = LinearLayout.VERTICAL
            binding.root.setPadding(0, 0, 0, 0)
            binding.contentContainer.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)

            nav.orientation = LinearLayout.HORIZONTAL
            nav.gravity = Gravity.CENTER
            nav.layoutParams = LinearLayout.LayoutParams(-1, dp(profile.bottomNavHeightDp))
            nav.setPadding(dp(6), dp(6), dp(6), dp(6))
            brandIcon.visibility = View.GONE
            brandLabel.visibility = View.GONE
            binding.navNowPlayingCard.visibility = View.GONE
            navNowLogoView = null
            navNowFavoriteView = null
            navNowStationView = null
            navNowTrackView = null
            styleNavButtons(landscape = false)
        }
        syncStatusView = findViewById(R.id.syncStatus)
    }

    private fun setupPhoneLandscapeSidebar() {
        val nav = binding.navContainer
        val brandIcon = binding.navBrandIcon
        val brandLabel = binding.navBrandLabel
        val mini = binding.navMiniPlayerContainer
        val buttons = listOf(
            binding.navPlayer,
            binding.navCountries,
            binding.navGenres,
            binding.navFavorites,
            binding.navRecents,
            binding.navSearch
        )

        listOf<View>(brandIcon, brandLabel, mini, binding.navNowPlayingCard).forEach {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        buttons.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        nav.removeAllViews()

        brandIcon.visibility = View.VISIBLE
        brandLabel.visibility = View.VISIBLE
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(brandIcon, LinearLayout.LayoutParams(-1, dp(38)))
            addView(brandLabel, LinearLayout.LayoutParams(-1, dp(20)))
        }
        nav.addView(header, LinearLayout.LayoutParams(-1, dp(60)))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        buttons.forEach { list.addView(it) }
        scroll.addView(list, ScrollView.LayoutParams(-1, -2))
        nav.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        mini.visibility = View.GONE
        nav.addView(mini, LinearLayout.LayoutParams(-1, dp(86)))
        styleNavButtons(landscape = true)
    }

    private fun styleNavButtons(landscape: Boolean) {
        val buttons = listOf(
            binding.navPlayer to R.drawable.ic_radio,
            binding.navCountries to R.drawable.ic_globe,
            binding.navGenres to R.drawable.ic_grid,
            binding.navFavorites to R.drawable.ic_star_outline,
            binding.navRecents to R.drawable.ic_history,
            binding.navSearch to R.drawable.ic_search
        )
        buttons.forEach { (button, icon) ->
            if (landscape) {
                button.layoutParams = LinearLayout.LayoutParams(-1, dp(uiProfile.sidebarButtonHeightDp)).apply {
                    bottomMargin = dp(if (uiProfile.isCarReference) 8 else 6)
                }
                button.textSize = uiProfile.navLabelSizeSp
                button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                button.setPadding(dp(16), 0, dp(12), 0)
                button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                button.compoundDrawablePadding = dp(14)
            } else {
                button.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
                button.textSize = uiProfile.navLabelSizeSp
                button.gravity = Gravity.CENTER
                button.setPadding(dp(2), dp(6), dp(2), dp(6))
                button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
                button.compoundDrawablePadding = dp(7)
            }
        }
    }
    private fun setupNavigation() {
        binding.navPlayer.setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
        binding.navCountries.setOnClickListener {
            showScreen("COUNTRIES") {
                val code = lastCountryCode
                if (code != null) loadAndRenderStations(lastCountryTitle ?: code, country = code, onBack = { renderCountries() }, restorePosition = lastCountryPosition, browseKind = "country") else renderCountries()
            }
        }
        binding.navGenres.setOnClickListener {
            showScreen("GENRES") {
                val genre = lastGenreName
                if (!genre.isNullOrBlank()) loadAndRenderStations(genre, genre = genre, onBack = { renderGenres() }, restorePosition = lastGenrePosition, browseKind = "genre") else renderGenres()
            }
        }
        binding.navFavorites.setOnClickListener { showScreen("FAVORITES") { renderFavorites() } }
        binding.navRecents.setOnClickListener { showScreen("RECENT") { renderRecents() } }
        binding.navSearch.setOnClickListener { showScreen("SEARCH") { renderSearch() } }
        binding.navNowPlayingCard.setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
        styleNavigationButtons()
        setActiveNav(R.id.navPlayer)
    }
    private fun renderActiveScreen() {
        when (activeNavId) {
            R.id.navPlayer -> renderPlayer()
            R.id.navCountries -> {
                val code = lastCountryCode
                if (code != null) {
                    loadAndRenderStations(lastCountryTitle ?: code, country = code, onBack = { renderCountries() }, restorePosition = lastCountryPosition, browseKind = "country")
                } else {
                    renderCountries()
                }
            }
            R.id.navGenres -> {
                val genre = lastGenreName
                if (!genre.isNullOrBlank()) {
                    loadAndRenderStations(genre, genre = genre, onBack = { renderGenres() }, restorePosition = lastGenrePosition, browseKind = "genre")
                } else {
                    renderGenres()
                }
            }
            R.id.navFavorites -> renderFavorites()
            R.id.navRecents -> renderRecents()
        }
    }

    private fun styleNavigationButtons() = Unit
    private fun configureImmersiveWindow() { window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT; if (android.os.Build.VERSION.SDK_INT >= 30) { window.setDecorFitsSystemWindows(false); window.insetsController?.let { controller -> controller.hide(android.view.WindowInsets.Type.systemBars()); controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } } else { @Suppress("DEPRECATION") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN } }
    private fun showScreen(title: String, content: () -> Unit) { setActiveNav(when (title) { "PLAYER" -> R.id.navPlayer; "COUNTRIES" -> R.id.navCountries; "GENRES" -> R.id.navGenres; "FAVORITES" -> R.id.navFavorites; "RECENT" -> R.id.navRecents; else -> R.id.navSearch }); content() }
    private fun setActiveNav(activeId: Int) {
        activeNavId = activeId
        intArrayOf(R.id.navPlayer, R.id.navCountries, R.id.navGenres, R.id.navFavorites, R.id.navRecents, R.id.navSearch).forEach { id ->
            findViewById<Button>(id).apply {
                val active = id == activeId
                val favoriteNav = id == R.id.navFavorites
                val navColor = when {
                    active && favoriteNav -> R.color.auto_accent
                    active -> R.color.auto_accent
                    else -> R.color.auto_text_muted
                }
                setTextColor(getColor(navColor))
                setBackgroundResource(if (active) R.drawable.bg_nav_item_active else R.drawable.bg_nav_item)
                if (favoriteNav) {
                    if (uiProfile.isLandscape) {
                        setCompoundDrawablesWithIntrinsicBounds(
                            if (active) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                            0,
                            0,
                            0
                        )
                    } else {
                        setCompoundDrawablesWithIntrinsicBounds(
                            0,
                            if (active) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                            0,
                            0
                        )
                    }
                }
                compoundDrawableTintList = ColorStateList.valueOf(getColor(navColor))
                alpha = if (active) 1f else 0.78f
            }
        }
    }

    private fun renderPlayer() {
        playerLogoView = null
        playerBackdropView = null
        playerTrackView = null
        playerStationView = null
        playerMetaView = null
        playerStatusView = null
        playerFavoriteButton = null
        playPauseIcon = null
        playPauseLabel = null

        val station = currentStation ?: catalog.firstOrNull()
        val root = screenRoot()
        val compactPlayer = !uiProfile.isCarReference

        if (station == null) {
            updateNavNowPlaying(null)
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_player_card)
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
            emptyCard.addView(ImageView(this).apply {
                setImageResource(R.drawable.app_logo)
                alpha = 0.9f
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(150), dp(150)))
            emptyCard.addView(marqueeTextView("Ready to play", 28f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(-1, dp(40)))
            emptyCard.addView(marqueeTextView("Choose a station from Countries, Genres or Search", 15f, R.color.auto_text_muted, false, false), LinearLayout.LayoutParams(-1, dp(28)))
            root.addView(emptyCard, LinearLayout.LayoutParams(-1, 0, 1f))
            binding.contentContainer.setScreenContent(root)
            return
        }

        val hero = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_player_card)
            if (uiProfile.isCarReference) {
                setPadding(dp(22), dp(22), dp(22), dp(18))
            } else {
                val pad = uiProfile.playerHeroPaddingDp
                setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
            }
            isClickable = true
            isFocusable = true
            elevation = dp(2).toFloat()
        }
        attachStationSwipe(hero)

        val ambient = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.075f
            setImageResource(R.drawable.app_logo)
            contentDescription = null
        }
        playerBackdropView = ambient
        loadStationBackdrop(station, ambient)
        hero.addView(ambient, FrameLayout.LayoutParams(-1, -1))

        val foreground = LinearLayout(this).apply {
            orientation = if (uiProfile.isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (uiProfile.isLandscape) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
        }

        val logoFrame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_logo)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = station.name + " logo"
            setImageResource(R.drawable.ic_radio)
            imageTintList = ColorStateList.valueOf(getColor(R.color.auto_accent))
            tag = station.id
        }
        playerLogoView = logo
        loadStationLogo(station, logo)
        logoFrame.addView(logo, FrameLayout.LayoutParams(-1, -1))
        foreground.addView(
            logoFrame,
            if (uiProfile.isCarReference) {
                LinearLayout.LayoutParams(dp(210), dp(210)).apply { marginEnd = dp(34) }
            } else if (uiProfile.isLandscape) {
                LinearLayout.LayoutParams(dp(uiProfile.playerLogoDp), dp(uiProfile.playerLogoDp)).apply { marginEnd = dp(18) }
            } else {
                LinearLayout.LayoutParams(dp(uiProfile.playerLogoDp), dp(uiProfile.playerLogoDp)).apply { bottomMargin = dp(14) }
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info.addView(label("NOW PLAYING"), LinearLayout.LayoutParams(-1, dp(if (uiProfile.isCarReference) 24 else 20)))

        val stationTitle = marqueeTextView(
            station.name,
            if (uiProfile.isCarReference) 34f else uiProfile.playerTitleSizeSp,
            R.color.auto_text_main,
            true
        )
        playerStationView = stationTitle
        info.addView(
            stationTitle,
            LinearLayout.LayoutParams(
                -1,
                dp(
                    when {
                        uiProfile.isCarReference -> 48
                        uiProfile.isPhonePortrait -> 38
                        else -> 42
                    }
                )
            )
        )

        val metaText = listOf(station.country, station.genre).filter { it.isNotBlank() }.joinToString("  •  ")
        val meta = marqueeTextView(metaText, 14f, R.color.auto_text_muted)
        playerMetaView = meta
        info.addView(meta, LinearLayout.LayoutParams(-1, dp(28)))

        val trackTitle = marqueeTextView(
            nowPlayingText(station),
            if (uiProfile.isCarReference) 25f else uiProfile.playerTrackSizeSp,
            R.color.auto_text_main,
            true
        )
        playerTrackView = trackTitle
        info.addView(
            trackTitle,
            LinearLayout.LayoutParams(
                -1,
                dp(if (uiProfile.isCarReference) 42 else 36)
            ).apply { topMargin = dp(if (uiProfile.isCarReference) 12 else 8) }
        )

        val status = TextView(this).apply {
            textSize = 11f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(R.drawable.bg_status_pill)
            letterSpacing = 0.04f
        }
        playerStatusView = status
        info.addView(
            status,
            LinearLayout.LayoutParams(
                dp(uiProfile.playerStatusWidthDp),
                dp(if (uiProfile.isCarReference) 32 else 30)
            ).apply {
                topMargin = dp(6)
            }
        )

        foreground.addView(info, if (uiProfile.isLandscape) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
        hero.addView(foreground, FrameLayout.LayoutParams(-1, -1))

        val favorite = iconButton(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline, "Favorite") {
            currentStation?.let(::toggleFavorite)
        }.apply {
            setBackgroundResource(R.drawable.bg_icon_button)
            imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
        }
        playerFavoriteButton = favorite
        hero.addView(
            favorite,
            FrameLayout.LayoutParams(
                dp(uiProfile.playerFavoriteDp),
                dp(uiProfile.playerFavoriteDp),
                Gravity.TOP or Gravity.END
            )
        )

        root.addView(hero, LinearLayout.LayoutParams(-1, 0, if (uiProfile.isLandscape) 1f else 0.8f))
        updateNavNowPlaying(station)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(if (uiProfile.isCarReference) 10 else 6), 0, dp(2))
        }

        val shuffle = iconButton(R.drawable.ic_shuffle, "Shuffle") { catalog.randomOrNull()?.let { playStation(it) } }
        val compactControlDp = uiProfile.playerControlIconDp
        val compactMarginDp = if (uiProfile.isCarReference) 10 else 4

        controls.addView(
            shuffle,
            LinearLayout.LayoutParams(
                dp(if (uiProfile.isCarReference) 74 else compactControlDp),
                dp(if (uiProfile.isCarReference) 74 else compactControlDp)
            ).apply { marginEnd = dp(compactMarginDp) }
        )

        val prev = iconButton(R.drawable.ic_skip_previous, "Previous station") { playAdjacentStation(-1) }
        controls.addView(
            prev,
            LinearLayout.LayoutParams(
                dp(if (uiProfile.isCarReference) 78 else compactControlDp),
                dp(if (uiProfile.isCarReference) 78 else compactControlDp)
            ).apply { marginEnd = dp(compactMarginDp) }
        )

        val play = controlTile(
            if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play,
            if (controller?.isPlaying == true) "PAUSE" else "PLAY",
            true
        ) { togglePlayPause() }
        playPauseIcon = (play as LinearLayout).getChildAt(0) as ImageView
        playPauseLabel = (play as LinearLayout).getChildAt(1) as TextView
        controls.addView(
            play,
            LinearLayout.LayoutParams(
                dp(if (uiProfile.isCarReference) 246 else uiProfile.playerPlayWidthDp),
                dp(if (uiProfile.isCarReference) 90 else uiProfile.playerPlayHeightDp)
            ).apply { marginEnd = dp(compactMarginDp) }
        )

        val next = iconButton(R.drawable.ic_skip_next, "Next station") { playAdjacentStation(1) }
        controls.addView(
            next,
            LinearLayout.LayoutParams(
                dp(if (uiProfile.isCarReference) 70 else compactControlDp),
                dp(if (uiProfile.isCarReference) 70 else compactControlDp)
            ).apply { marginEnd = dp(compactMarginDp) }
        )

        root.addView(
            controls,
            LinearLayout.LayoutParams(
                -1,
                dp(if (uiProfile.isCarReference) 104 else uiProfile.playerPlayHeightDp + 12)
            )
        )

        updatePlayerButton()
        binding.contentContainer.setScreenContent(root)
    }

    private fun renderCountries() {
        val all = if (remoteCountries.isNotEmpty()) remoteCountries.sortedBy { it.name } else catalog.groupBy { it.countryCode }.map { CountryItem(it.value.first().country, it.key, flagFor(it.key), it.value.size) }.sortedBy { it.name }
        val root = screenRoot()
        root.addView(topBar("BROWSE", "Countries", "${all.size} countries with available radio", R.drawable.ic_globe))
        val columns = uiProfile.countryColumns
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, columns)
            adapter = CountryAdapter(all) { country -> loadAndRenderStations(country.name, country = country.code, onBack = { renderCountries() }, restorePosition = lastCountryPosition, browseKind = "country") }
            setPadding(0, 0, 0, dp(8))
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }
    private fun renderGenres() { val genres = if (remoteGenres.isNotEmpty()) remoteGenres.sortedBy { it.name } else catalog.groupBy { it.genre }.map { GenreItem(it.key, it.value.size) }.sortedBy { it.name }; val root = screenRoot(); root.addView(topBar("BROWSE", "Genres", "${genres.size} genres in the catalog", R.drawable.ic_music_note)); val recycler = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@MainActivity, uiProfile.genreColumns); adapter = GenreAdapter(genres) { genre -> loadAndRenderStations(genre.name, genre = genre.name, onBack = { renderGenres() }, browseKind = "genre") }; setPadding(0, 0, 0, dp(8)); clipToPadding = false }; root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f)); binding.contentContainer.setScreenContent(root) }
    private fun loadAndRenderStations(title: String, country: String? = null, genre: String? = null, onBack: () -> Unit, restorePosition: Int = 0, browseKind: String? = null) {
    if (browseKind == "country" && country != null) { lastCountryCode = country; lastCountryTitle = title }
    if (browseKind == "genre" && genre != null) lastGenreName = genre
    val root = screenRoot()
    root.addView(topBar("STATIONS", title, "Loading stations…", R.drawable.ic_list))
    binding.contentContainer.setScreenContent(root)
    catalogRepository.loadStations(country = country, genre = genre, limit = 200) { result ->
        result.onSuccess { stations ->
            catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } })
            applyPersistedState()
            saveCatalogCacheAsync()
            syncPlayerPlaylist()
            renderStationList(title, stations, onBack, country, genre, stations.size == 200, 1, restorePosition, browseKind)
        }.onFailure {
            renderStationList(title, emptyList(), onBack, country, genre, false, 1, restorePosition, browseKind)
            showPlayerState("CATALOG ERROR", "Unable to load stations")
        }
    }
}
    private fun renderFavorites() { renderSavedStations(ids = favoriteIds.toList(), title = "Favorites", columns = uiProfile.stationColumns) }
    private fun renderRecents() { renderSavedStations(ids = recentIds.toList(), title = "Recently played", columns = uiProfile.stationColumns) }
    private fun renderSavedStations(ids: List<String>, title: String, columns: Int = 1) { val loaded = ids.mapNotNull { id -> catalog.find { it.id == id } ?: catalogCacheStore.findStation(id) }.toMutableList(); val missing = ids.filterNot { id -> loaded.any { it.id == id } }; if (missing.isEmpty()) { renderStationList(title, loaded, { renderPlayer() }, columns = columns); return }; setActiveNav(if (title.startsWith("FAVORITE")) R.id.navFavorites else R.id.navRecents); binding.contentContainer.removeAllViews(); val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(titleBlock(title, "Loading saved stations...", if (title.startsWith("FAVORITE")) R.drawable.ic_star_filled else R.drawable.ic_history)); binding.contentContainer.setScreenContent(root); fun loadMissing(index: Int) { if (index >= missing.size) { applyPersistedState(); renderStationList(title, ids.mapNotNull { id -> catalog.find { it.id == id } }, { renderPlayer() }, columns = columns); return }; catalogCacheStore.findStation(missing[index])?.let { cached -> if (catalog.none { it.id == cached.id }) catalog.add(cached); ensureStationInPlaylist(cached); loadMissing(index + 1); return }; catalogRepository.loadStation(missing[index]) { result -> result.onSuccess { station -> if (catalog.none { it.id == station.id }) catalog.add(station); saveCatalogCacheAsync(); ensureStationInPlaylist(station) }; loadMissing(index + 1) } }; loadMissing(0) }
    private fun renderStationList(title: String, stations: List<Station>, onBack: () -> Unit, country: String? = null, genre: String? = null, canLoadMore: Boolean = false, columns: Int = 1, restorePosition: Int = 0, browseKind: String? = null) {
    val root = screenRoot()
    root.addView(topBar("STATIONS", title, if (stations.isEmpty()) "No stations found" else "${stations.size} stations available", R.drawable.ic_list, listOf(iconButton(R.drawable.ic_back, "Back") { onBack() })))
    lateinit var recycler: RecyclerView
    val stationAdapter = StationAdapter(stations, onPlay = {
        val position = (recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        if (browseKind == "country") lastCountryPosition = position
        if (browseKind == "genre") lastGenrePosition = position
        playStation(it)
    }, onFavorite = { toggleFavorite(it) }, isCurrent = { it.id == currentStation?.id })
    recycler = RecyclerView(this).apply {
        layoutManager = GridLayoutManager(this@MainActivity, uiProfile.stationColumns)
        adapter = stationAdapter
        setPadding(0, 0, 0, dp(8))
        clipToPadding = false
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val position = (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                    ?.findFirstVisibleItemPosition() ?: return
                if (browseKind == "country") lastCountryPosition = position
                if (browseKind == "genre") lastGenrePosition = position
            }
        })
        post { if (restorePosition > 0 && stationAdapter.itemCount > restorePosition) (layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(restorePosition, 0) }
    }
    if (stations.isEmpty()) {
        val empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_player_card)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val icon = if (title.contains("Favorite", true)) R.drawable.ic_star_filled else R.drawable.ic_radio
        val headline = when {
            title.contains("Favorite", true) -> "No favorites yet"
            title.contains("Recent", true) -> "No recently played stations"
            else -> "No stations found"
        }
        val detail = when {
            title.contains("Favorite", true) -> "Tap the star on any station to keep it here."
            title.contains("Recent", true) -> "Stations you play will appear here."
            else -> "Try another country, genre or search."
        }
        empty.addView(ImageView(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(
                getColor(if (title.contains("Favorite", true)) R.color.auto_favorite else R.color.auto_text_muted)
            )
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        empty.addView(marqueeTextView(headline, 22f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(-1, dp(38)))
        empty.addView(marqueeTextView(detail, 14f, R.color.auto_text_muted, false, false), LinearLayout.LayoutParams(-1, dp(30)))
        root.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
    } else {
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    if (canLoadMore && (country != null || genre != null)) root.addView(
        actionButton("LOAD MORE") {
            catalogRepository.loadStations(country = country, genre = genre, limit = 200, offset = stations.size) { result ->
                result.onSuccess { nextPage ->
                    val merged = (stations + nextPage).distinctBy { it.id }
                    catalog.addAll(nextPage.filter { station -> catalog.none { it.id == station.id } })
                    applyPersistedState()
                    syncPlayerPlaylist()
                    saveCatalogCacheAsync()
                    renderStationList(
                        title,
                        merged,
                        onBack,
                        country,
                        genre,
                        nextPage.size == 200,
                        columns,
                        restorePosition,
                        browseKind
                    )
                }
            }
        },
        LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8); bottomMargin = dp(2) }
    )
    binding.contentContainer.setScreenContent(root)
}
    private fun renderSearch() { val root = screenRoot(); root.addView(topBar("FIND", "Search", "Station, city, country or genre", R.drawable.ic_search)); val input = EditText(this).apply { hint = "Search station, city, country or genre"; setTextColor(getColor(R.color.auto_text_main)); setHintTextColor(getColor(R.color.auto_text_muted)); textSize = 17f; setSingleLine(true); setShowSoftInputOnFocus(uiProfile.useOnScreenKeypad.not()); setBackgroundResource(R.drawable.bg_input); setPadding(dp(16), 0, dp(16), 0); setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0); compoundDrawablePadding = dp(10); compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted)) }; root.addView(input, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(14) }); val results = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@MainActivity, uiProfile.stationColumns) }; val adapter = StationAdapter(
            emptyList(),
            onPlay = { playStation(it) },
            onFavorite = { toggleFavorite(it); results.adapter?.notifyDataSetChanged() },
            isCurrent = { it.id == currentStation?.id }
        ); results.adapter = adapter; root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f)); if (uiProfile.useOnScreenKeypad) root.addView(buildSearchKeypad(input, adapter)); input.addTextChangedListener(SimpleTextWatcher { text -> val query = text.toString().trim(); val requestId = ++searchRequestId; searchHandler.removeCallbacksAndMessages(null); if (query.isBlank()) adapter.submitList(emptyList()) else if (query.length < 2) updateSearchResults(query, adapter) else searchHandler.postDelayed({ catalogRepository.loadStations(query = query, limit = 50) { result -> if (requestId != searchRequestId) return@loadStations; result.onSuccess { stations -> catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } }); applyPersistedState(); adapter.submitList(stations) } } }, 250L) }); binding.contentContainer.setScreenContent(root) }
    private fun updateSearchResults(query: String, adapter: StationAdapter) { val q = query.trim(); if (q.isEmpty()) { adapter.submitList(emptyList()); return }; adapter.submitList(catalog.filter { it.name.contains(q, true) || it.country.contains(q, true) || it.genre.contains(q, true) || it.city.contains(q, true) }) }
    private fun playStation(station: Station, renderPlayerScreen: Boolean = true) { currentStation = station; restoredStationId = station.id; currentStreamIndex = 0; streamRetryCount = 0; bufferingSinceMs = null; retryHandler.removeCallbacksAndMessages(null); ensureStationInPlaylist(station); controller?.let { player -> val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id }; if (index != null) { player.seekTo(index, 0L); player.play() } else playCurrentStream() }; addRecentStation(station); showScreen("PLAYER") { renderPlayer() } }
    private fun playCurrentStream() { val player = controller ?: return; val station = currentStation ?: return; if (station.streams.isEmpty()) { showPlayerState("STREAM UNAVAILABLE", "No working stream"); return }; ensureStationInPlaylist(station); val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id } ?: return; showPlayerState("CONNECTING...", "Opening stream " + (currentStreamIndex + 1)); player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex)); player.seekTo(index, 0L); player.prepare(); player.play() }
    private fun addRecentStation(station: Station) { recentIds.remove(station.id); recentIds.addFirst(station.id); while (recentIds.size > 10) recentIds.removeLast(); persistRecents() }
    private fun syncPlayerPlaylist() { val player = controller ?: return; if (player.mediaItemCount > 0) return; val items = catalog.filter { it.streams.isNotEmpty() }.distinctBy { it.id }.take(200).map { stationToMediaItem(it, 0) }; if (items.isNotEmpty()) player.setMediaItems(items, false) }
    private fun ensureStationInPlaylist(station: Station) { val player = controller ?: return; val exists = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == station.id }; if (!exists) player.addMediaItem(stationToMediaItem(station, 0)) }
    private fun updateNowPlayingArtist(artist: String) {
        val station = currentStation ?: return
        val updated = station.copy(artist = artist.trim())
        currentStation = updated
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()
        val text = nowPlayingText(updated)
        playerTrackView?.text = text
        miniTrackView?.text = text
        navNowTrackView?.text = text
        updateMarquee(playerTrackView)
        updateMarquee(miniTrackView)
        updateMarquee(navNowTrackView)
    }
    private fun stationToMediaItem(station: Station, streamIndex: Int): MediaItem { val stream = station.streams.getOrNull(streamIndex) ?: station.streams.first(); val metadata = MediaMetadata.Builder().setTitle(station.songTitle?.takeIf { it.isNotBlank() } ?: station.name).setArtist(station.artist?.takeIf { it.isNotBlank() && !it.equals(station.name, true) }).setAlbumTitle(station.name).setStation(station.name).setGenre(station.genre).setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build(); return MediaItem.Builder().setMediaId(station.id).setUri(stream).setTag(station.id).setMediaMetadata(metadata).build() }
    private fun switchToNextStream(reason: String) {
        val station = currentStation ?: return
        val player = controller ?: return
        if (currentStreamIndex + 1 >= station.streams.size) { switchToNextStation(reason); return }
        currentStreamIndex++
        showPlayerState(reason, "Opening stream " + (currentStreamIndex + 1))
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex))
        player.prepare(); player.play()
    }
    private fun updateNowPlayingTitle(rawTitle: String) {
        val station = currentStation ?: return
        val value = rawTitle.trim()
        if (value.isBlank()) return
        val parts = value.split(" - ", limit = 2)
        val updated = if (parts.size == 2) station.copy(songTitle = parts[1].trim(), artist = parts[0].trim()) else station.copy(songTitle = value)
        currentStation = updated
        restoredStationId = updated.id
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()
        val text = nowPlayingText(updated)
        playerTrackView?.text = text
        miniTrackView?.text = text
        navNowTrackView?.text = text
        updateMarquee(playerTrackView)
        updateMarquee(miniTrackView)
        updateMarquee(navNowTrackView)
    }
    private fun nowPlayingText(station: Station): String {
    val artist = station.artist?.trim().orEmpty()
    val title = station.songTitle?.trim().orEmpty()
    return when { artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"; title.isNotBlank() -> title; else -> station.name }
}
private fun toggleFavorite(station: Station) {
    val newValue = !favoriteIds.contains(station.id)
    if (newValue) favoriteIds.add(station.id) else favoriteIds.remove(station.id)
    catalog = catalog.map { if (it.id == station.id) it.copy(favorite = newValue) else it }.toMutableList()
    if (currentStation?.id == station.id) currentStation = currentStation?.copy(favorite = newValue)
    station.favorite = newValue
    persistFavorites()
    playerFavoriteButton?.apply {
        setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
    }
    miniFavoriteView?.apply {
        setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
    }
    navNowFavoriteView?.apply {
        setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
    }
}
private fun updateSyncProgress(bytes: Long, total: Long) {
    if (!syncActive) return
    syncStatusView?.visibility = View.VISIBLE
    syncStatusView?.text = if (total > 0L) "Sync " + formatBytes(bytes) + " / " + formatBytes(total) else "Sync " + formatBytes(bytes)
}
private fun finishSyncProgress() {
    syncActive = false
    syncStatusView?.visibility = View.GONE
}
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}
private fun switchToNextStation(reason: String) {
        val player = controller ?: return
        if (player.mediaItemCount <= 1) { playerOffline = true; playerReconnecting = false; player.pause(); updatePlayerButton(); showPlayerState("STREAM UNAVAILABLE", "No working station"); return }
        val currentIndex = player.currentMediaItemIndex
        currentStation?.id?.let { failedStationIds.add(it) }
        var nextIndex = -1
        for (offset in 1 until player.mediaItemCount) {
            val index = (currentIndex + offset) % player.mediaItemCount
            if (!failedStationIds.contains(player.getMediaItemAt(index).mediaId)) { nextIndex = index; break }
        }
        if (nextIndex < 0) { playerOffline = true; playerReconnecting = false; player.pause(); updatePlayerButton(); showPlayerState("STREAM UNAVAILABLE", "No working station"); return }
        playerReconnecting = true; playerOffline = false; streamRetryCount = 0; currentStreamIndex = 0
        showPlayerState("RECONNECTING...", "Switching station")
        player.seekTo(nextIndex, 0L); player.prepare(); player.play()
    }

    private fun togglePlayPause() { val player = controller ?: return; if (player.isPlaying) player.pause() else if (player.currentMediaItem == null) playCurrentStream() else player.play(); updatePlayerButton() }

    private fun playAdjacentStation(delta: Int) {
        val playable = catalog.filter { it.streams.isNotEmpty() }.distinctBy { it.id }
        if (playable.isEmpty()) return
        val currentId = currentStation?.id
        val currentIndex = playable.indexOfFirst { it.id == currentId }
        val base = if (currentIndex >= 0) currentIndex else 0
        val targetIndex = (base + delta + playable.size) % playable.size
        if (playable[targetIndex].id != currentId) playStation(playable[targetIndex])
    }

    private fun attachStationSwipe(view: View) {
        var downX = 0f
        var downY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > dp(70) && abs(dx) > abs(dy) * 1.2f) {
                        playAdjacentStation(if (dx < 0f) 1 else -1)
                        true
                    } else {
                        false
                    }
                }
                else -> true
            }
        }
    }
    private fun updateNavNowPlaying(station: Station?) {
        if (!::binding.isInitialized) return
        binding.navNowPlayingCard.visibility = View.GONE
        navNowLogoView = null
        navNowFavoriteView = null
        navNowStationView = null
        navNowTrackView = null
    }

    private fun updateCurrentStationUi(station: Station) {
        playerStationView?.apply {
            text = station.name
            updateMarquee(this)
        }
        playerMetaView?.apply {
            text = listOf(station.country, station.genre)
                .filter { it.isNotBlank() }
                .joinToString("  •  ")
            updateMarquee(this)
        }
        playerTrackView?.apply {
            text = nowPlayingText(station)
            updateMarquee(this)
        }
        playerLogoView?.let {
            it.contentDescription = station.name + " logo"
            loadStationLogo(station, it)
        }
        playerBackdropView?.let { loadStationBackdrop(station, it) }
        playerFavoriteButton?.apply {
            setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
        }
        miniStationView?.apply {
            text = station.name
            updateMarquee(this)
        }
        miniTrackView?.apply {
            text = nowPlayingText(station)
            updateMarquee(this)
        }
        miniLogoView?.let { loadStationLogo(station, it) }
        miniFavoriteView?.apply {
            setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
        }
        updateNavNowPlaying(station)
    }

    private fun updatePlayerButton() {
        if (!::binding.isInitialized) return
        val player = controller
        val playing = player?.isPlaying == true
        playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"
        miniPlayPauseIcon?.apply {
            setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            contentDescription = if (playing) "Pause" else "Play"
        }

        val status = when {
            playerOffline -> "●  OFFLINE"
            playerReconnecting -> "●  RECONNECTING"
            player?.playbackState == Player.STATE_BUFFERING -> "●  BUFFERING"
            player?.playbackState == Player.STATE_IDLE -> "●  CONNECTING"
            playing -> "●  PLAYING"
            else -> "●  PAUSED"
        }
        val color = when {
            playerOffline -> R.color.auto_danger
            playerReconnecting || player?.playbackState == Player.STATE_BUFFERING || player?.playbackState == Player.STATE_IDLE -> R.color.auto_warning
            playing -> R.color.auto_success
            else -> R.color.auto_text_muted
        }
        val background = when {
            playerOffline -> R.drawable.bg_status_pill_danger
            playerReconnecting || player?.playbackState == Player.STATE_BUFFERING || player?.playbackState == Player.STATE_IDLE -> R.drawable.bg_status_pill_warning
            playing -> R.drawable.bg_status_pill_success
            else -> R.drawable.bg_status_pill
        }
        playerStatusView?.apply {
            text = status
            setTextColor(getColor(color))
            setBackgroundResource(background)
            updateMarquee(this)
        }
    }
    private fun showPlayerState(title: String, message: String) { Toast.makeText(this, "$title • $message", Toast.LENGTH_SHORT).show() }
    private fun playerControlButton(text: String, iconRes: Int, weight: Float, heightDp: Int, accent: Boolean = false, click: () -> Unit): View = controlTile(iconRes, text, accent, click)
    private fun iconButton(iconRes: Int, description: String, click: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
        setBackgroundResource(R.drawable.bg_icon_button)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        setOnClickListener { click() }
    }
    private fun FrameLayout.setScreenContent(view: View) {
        miniPlayPauseIcon = null
        miniStationView = null
        miniTrackView = null
        miniLogoView = null
        miniFavoriteView = null
        removeAllViews()

        if (activeNavId == R.id.navPlayer) {
            updateSidebarMiniPlayer(null)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            return
        }

        val station = currentStation
            ?: controller?.currentMediaItem?.mediaId?.let { id -> catalog.firstOrNull { it.id == id } }
            ?: catalog.firstOrNull()

        if (station == null) {
            updateSidebarMiniPlayer(null)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            return
        }

        if (currentStation == null) currentStation = station

        if (uiProfile.isLandscape) {
            updateSidebarMiniPlayer(station)
            addView(view, FrameLayout.LayoutParams(-1, -1))
        } else {
            val wrapper = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            wrapper.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
            wrapper.addView(
                buildMiniPlayer(station),
                LinearLayout.LayoutParams(-1, dp(uiProfile.miniPlayerHeightDp)).apply {
                    marginStart = dp(uiProfile.contentPaddingDp)
                    marginEnd = dp(uiProfile.contentPaddingDp)
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
            )
            addView(wrapper, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun updateSidebarMiniPlayer(station: Station?) {
        if (!::binding.isInitialized) return
        val container = binding.navMiniPlayerContainer

        if (!uiProfile.isLandscape || activeNavId == R.id.navPlayer || station == null) {
            container.visibility = View.GONE
            container.removeAllViews()
            miniPlayPauseIcon = null
            miniStationView = null
            miniTrackView = null
            miniLogoView = null
            miniFavoriteView = null
            return
        }

        container.visibility = View.VISIBLE
        container.removeAllViews()
        container.addView(buildMiniPlayer(station), LinearLayout.LayoutParams(-1, -1))
    }

    private fun buildMiniPlayer(station: Station): View {
        val phone = uiProfile.isPhonePortrait || uiProfile.isPhoneLandscape
        val card = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_card)
            isClickable = true
            isFocusable = true
        }

        val ambient = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.09f
            setImageResource(R.drawable.app_logo)
            contentDescription = null
        }
        miniLogoView = ambient
        card.addView(ambient, FrameLayout.LayoutParams(-1, -1))
        loadStationBackdrop(station, ambient)

        miniStationView = marqueeTextView(
            station.name,
            if (phone) 13f else 14f,
            R.color.auto_text_main,
            true
        )
        miniStationView?.let {
            card.addView(
                it,
                FrameLayout.LayoutParams(-1, dp(if (phone) 24 else 28), Gravity.TOP).apply {
                    topMargin = dp(7)
                    leftMargin = dp(40)
                    rightMargin = dp(40)
                }
            )
        }

        miniTrackView = marqueeTextView(
            nowPlayingText(station),
            if (phone) 10f else 11f,
            R.color.auto_text_main
        )
        miniTrackView?.let {
            card.addView(
                it,
                FrameLayout.LayoutParams(-1, dp(if (phone) 20 else 24), Gravity.BOTTOM).apply {
                    bottomMargin = dp(6)
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                }
            )
        }

        miniFavoriteView = ImageView(this).apply {
            setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(
                getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main)
            )
            setBackgroundResource(R.drawable.bg_icon_button)
            contentDescription = "Favorite"
            setOnClickListener { currentStation?.let(::toggleFavorite) }
        }
        card.addView(
            miniFavoriteView,
            FrameLayout.LayoutParams(
                dp(if (phone) 30 else 32),
                dp(if (phone) 30 else 32),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(5)
                rightMargin = dp(5)
            }
        )

        val playSize = when {
            uiProfile.isPhonePortrait -> 62
            uiProfile.isPhoneLandscape -> 54
            else -> 34
        }
        miniPlayPauseIcon = ImageView(this).apply {
            setImageResource(if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
                        imageTintList = ColorStateList.valueOf(getColor(if (phone) R.color.auto_bg else R.color.auto_text_main))
            setBackgroundResource(if (phone) R.drawable.bg_giant_play else R.drawable.bg_icon_button)
            contentDescription = if (controller?.isPlaying == true) "Pause" else "Play"
            setOnClickListener { togglePlayPause() }
        }
        card.addView(
            miniPlayPauseIcon,
            FrameLayout.LayoutParams(dp(playSize), dp(playSize), if (phone) Gravity.CENTER else Gravity.BOTTOM or Gravity.END).apply {
                if (!phone) {
                    bottomMargin = dp(6)
                    rightMargin = dp(6)
                }
            }
        )
        return card
    }

    private fun stationMetaText(station: Station): String =
        listOf(station.country, station.genre)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

    private fun actionButton(text: String, iconRes: Int? = null, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(getColor(R.color.auto_text_main))
        setBackgroundResource(R.drawable.bg_button)
        minWidth = 0
        minHeight = 0
        stateListAnimator = null
        includeFontPadding = false
        isAllCaps = false
        gravity = Gravity.CENTER
        setPadding(dp(20), 0, dp(20), 0)
        if (iconRes != null) {
            setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            compoundDrawablePadding = dp(10)
            compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
        }
        setOnClickListener { click() }
    }
    private fun keyButton(text: String, click: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 13f; setTextColor(getColor(R.color.auto_text_main)); setBackgroundResource(R.drawable.bg_button); minWidth = 0; minHeight = 0; stateListAnimator = null; includeFontPadding = false; isAllCaps = false; gravity = Gravity.CENTER; setOnClickListener { click() } }
    private fun titleBlock(title: String, subtitle: String, iconRes: Int? = null): View = topBar("SECTION", title, subtitle, iconRes ?: R.drawable.ic_radio)
    private fun verticalText(title: String, subtitle: String, titleSize: Float, subtitleSize: Float): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; addView(TextView(this@MainActivity).apply { text = title; textSize = titleSize; setTextColor(getColor(R.color.auto_text_main)); setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; setHorizontallyScrolling(true); post { isSelected = true }; includeFontPadding = false }, LinearLayout.LayoutParams(-1, dp((titleSize + 10).toInt()))); addView(TextView(this@MainActivity).apply { text = subtitle; textSize = subtitleSize; setTextColor(getColor(R.color.auto_text_muted)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; setHorizontallyScrolling(true); post { isSelected = true }; includeFontPadding = false }, LinearLayout.LayoutParams(-1, dp((subtitleSize + 8).toInt()))) }
    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = uiProfile.contentPaddingDp
        setPadding(dp(pad), dp(16), dp(pad), dp(12))
        setBackgroundColor(getColor(R.color.auto_bg))
    }
    private fun topBar(eyebrow: String, title: String, subtitle: String, icon: Int, right: List<View> = emptyList()): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(if (uiProfile.isCarReference) 14 else 10))
        val text = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(eyebrow), LinearLayout.LayoutParams(-1, dp(if (uiProfile.isCarReference) 22 else 18)))
            addView(
                marqueeTextView(
                    title,
                    uiProfile.sectionTitleSizeSp,
                    R.color.auto_text_main,
                    true
                ),
                LinearLayout.LayoutParams(-1, dp(if (uiProfile.isCarReference) 40 else 34))
            )
            addView(
                marqueeTextView(
                    subtitle,
                    uiProfile.sectionSubtitleSizeSp,
                    R.color.auto_text_muted
                ),
                LinearLayout.LayoutParams(-1, dp(if (uiProfile.isCarReference) 24 else 20))
            )
        }
        addView(
            text,
            LinearLayout.LayoutParams(0, dp(uiProfile.sectionHeaderHeightDp), 1f)
        )
        right.forEach {
            addView(
                it,
                LinearLayout.LayoutParams(
                    dp(if (uiProfile.isCarReference) 58 else 52),
                    dp(if (uiProfile.isCarReference) 58 else 52)
                ).apply { marginStart = dp(if (uiProfile.isCarReference) 8 else 6) }
            )
        }
    }
    private fun marqueeTextView(value: String, size: Float, colorRes: Int, bold: Boolean = false, marquee: Boolean = true): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(getColor(colorRes)); typeface = android.graphics.Typeface.create("sans-serif-medium", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL); gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; maxLines = 1; isSingleLine = true; if (marquee) { setHorizontallyScrolling(true); ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; post { isSelected = true } } }
    private fun updateMarquee(view: TextView?) { view?.apply { setHorizontallyScrolling(true); ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; post { isSelected = true; requestLayout() } } }
    private fun label(text: String): TextView = TextView(this).apply { this.text = text; textSize = 11f; setTextColor(getColor(R.color.auto_accent)); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD); includeFontPadding = false; letterSpacing = 0.08f }
    private fun controlTile(iconRes: Int, text: String, accent: Boolean = false, click: () -> Unit): View = LinearLayout(this).apply {
        val compact = !uiProfile.isCarReference
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(
            dp(if (compact) 8 else 20),
            0,
            dp(if (compact) 8 else 20),
            0
        )
        setBackgroundResource(if (accent) R.drawable.bg_giant_play else R.drawable.bg_button_static)
        isClickable = true
        isFocusable = true
        contentDescription = text
        setOnClickListener { click() }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(
            dp(if (accent) if (compact) 28 else 46 else if (compact) 30 else 38),
            dp(if (accent) if (compact) 28 else 46 else if (compact) 30 else 38)
        ).apply {
            marginEnd = dp(if (accent) if (compact) 8 else 12 else 8)
        })
        addView(TextView(this@MainActivity).apply {
            this.text = text
            textSize = if (accent) {
                if (compact) 12f else 16f
            } else {
                if (compact) 10f else 14f
            }
            setTextColor(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 1
        }, LinearLayout.LayoutParams(-2, -1))
    }
    private fun buildSearchKeypad(input: EditText, adapter: StationAdapter): View { val grid = GridLayout(this).apply { columnCount = 10; rowCount = 4; setBackgroundResource(R.drawable.bg_surface); setPadding(dp(6), dp(6), dp(6), dp(6)) }; "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { letter -> val b = keyButton(letter.toString()) { input.append(letter.toString()); updateSearchResults(input.text.toString(), adapter) }; grid.addView(b, GridLayout.LayoutParams().apply { width = dp(46); height = dp(44); setMargins(dp(2), dp(2), dp(2), dp(2)) }) }; grid.addView(keyButton("SPACE") { input.append(" "); updateSearchResults(input.text.toString(), adapter) }, GridLayout.LayoutParams().apply { width = dp(184); height = dp(44); columnSpec = GridLayout.spec(0, 4) }); grid.addView(keyButton("⌫") { if (input.text.isNotEmpty()) input.text.delete(input.text.length - 1, input.text.length) }, GridLayout.LayoutParams().apply { width = dp(92); height = dp(44); columnSpec = GridLayout.spec(4, 2) }); grid.addView(keyButton("CLEAR") { input.text.clear() }, GridLayout.LayoutParams().apply { width = dp(138); height = dp(44); columnSpec = GridLayout.spec(6, 3) }); return grid }
    private fun loadStationLogo(station: Station, target: ImageView) {
        target.tag = station.id
        target.setImageResource(R.drawable.app_logo)
        target.imageTintList = null
        val url = station.logo?.trim().orEmpty()
        if (url.isBlank()) return
        ImageLoader.load(url) { bitmap ->
            if (target.tag == station.id) {
                target.imageTintList = null
                target.setImageBitmap(bitmap)
            }
        }
    }

    private fun loadStationBackdrop(station: Station, target: ImageView) {
        target.tag = station.id
        target.setImageResource(R.drawable.app_logo)
        target.imageTintList = null
        val url = station.logo?.trim().orEmpty()
        if (url.isBlank()) return
        ImageLoader.load(url) { bitmap ->
            if (target.tag == station.id) target.setImageBitmap(bitmap)
        }
    }
    private fun flagFor(code: String): String { if (code.length != 2) return "🌐"; val upper = code.uppercase(); val first = Character.codePointAt(upper, 0); val second = Character.codePointAt(upper, 1); return String(Character.toChars(0x1F1E6 + first - 'A'.code)) + String(Character.toChars(0x1F1E6 + second - 'A'.code)) }
    private fun renderEmoji(text: CharSequence): CharSequence = try { text } catch (e: IllegalStateException) { text }
    private val uiProfile: UiProfile get() = UiProfile.from(resources)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { retryHandler.removeCallbacksAndMessages(null); searchHandler.removeCallbacksAndMessages(null); catalogRepository.close(); cacheExecutor.shutdownNow(); controller?.removeListener(playerListener); controllerFuture?.let(MediaController::releaseFuture); controller = null; super.onDestroy() }
    private class SimpleTextWatcher(private val onChanged: (CharSequence) -> Unit) : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged(s ?: "") }; override fun afterTextChanged(s: android.text.Editable?) = Unit }
    companion object {
        private const val KEY_STATION_ID = "current_station_id"
        private const val CATALOG_REFRESH_MS = 6L * 60 * 60 * 1000
    }
}