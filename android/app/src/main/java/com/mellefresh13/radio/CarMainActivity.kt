package com.mellefresh13.radio

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import com.mellefresh13.radio.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(markerClass = [UnstableApi::class])
class CarMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val cacheExecutor = Executors.newSingleThreadExecutor()
    private val paletteExecutor = Executors.newSingleThreadExecutor()
    private var controller: MediaController? = null
    private val demoCatalog = DemoCatalog.stations
    private var catalog: MutableList<Station> = demoCatalog
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var userStateStore: UserStateStore
    private lateinit var playbackStateStore: PlaybackStateStore
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
    private var playerLavaView: LavaLampView? = null
    private var lastPlayerLavaPalette: IntArray? = null
    private var playerTrackView: TextView? = null
    private var playerStationView: TextView? = null
    private var playerMetaView: TextView? = null
    private var playerMetaChips: LinearLayout? = null
    private var miniLavaView: LavaLampView? = null
    private var miniFavoriteView: ImageView? = null
    private var miniPlayPauseIcon: ImageView? = null
    private var miniStationView: TextView? = null
    private var miniTrackView: TextView? = null
    private var miniLogoView: ImageView? = null
    private var syncStatusView: TextView? = null
    private var syncActive = false
    private var activeNavId: Int = R.id.navPlayer
    private var settingsReturnNavId: Int = R.id.navPlayer
    private var settingsFooterButton: ImageButton? = null
    private lateinit var settingsBackCallback: OnBackPressedCallback
    private var navNowLogoView: ImageView? = null
    private var navNowFavoriteView: ImageView? = null
    private var navNowStationView: TextView? = null
    private var navNowTrackView: TextView? = null
    private var lastCountryCode: String? = null
    private var lastCountryTitle: String? = null
    private var lastCountryPosition: Int = 0
    private var lastGenreName: String? = null
    private var lastGenrePosition: Int = 0
    private val browseStationPages = mutableMapOf<String, List<Station>>()
    private val browseHasMore = mutableMapOf<String, Boolean>()

    private var lastFavoritesPosition = 0
    private var favoritesRecyclerView: RecyclerView? = null
    private var activeStationAdapter: StationAdapter? = null
    private var lastSearchQuery = ""
    private var pendingAutoOpenStationId: String? = null
    private var pendingAutoOpenSourceNavId: Int? = null
    private val autoOpenHandler = Handler(Looper.getMainLooper())
    private val autoOpenPlayerRunnable = object : Runnable {
        override fun run() {
            val stationId = pendingAutoOpenStationId
            val sourceNavId = pendingAutoOpenSourceNavId
            if (stationId == null || sourceNavId == null || activeNavId != sourceNavId || currentStation?.id != stationId) {
                cancelAutoOpenPlayer()
                return
            }
            if (controller?.isPlaying == true) {
                cancelAutoOpenPlayer()
                showScreen("PLAYER") { renderPlayer() }
            } else {
                autoOpenHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private var playerStatusView: TextView? = null
    private var playerOffline = false
    private var playerReconnecting = false
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { resumeOfflinePlaybackIfConnected() }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                runOnUiThread { resumeOfflinePlaybackIfConnected() }
            }
        }
    }
    private var startupPlaybackRestored = false
    private val failedStationIds = mutableSetOf<String>()
    private var catalogReady = false
    private var playPauseIcon: ImageView? = null
    private var playPauseLabel: TextView? = null
    private var playerFavoriteButton: ImageView? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                UiMotion.dismissPopupContaining("Internet restored")
                bufferingSinceMs = null
                playerOffline = false
                playerReconnecting = false
                currentStation?.let { failedStationIds.remove(it.id) }
            }
            updatePlayerButton()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (bufferingSinceMs == null) {
                    bufferingSinceMs = System.currentTimeMillis()
                    retryHandler.postDelayed({
                        val station = currentStation ?: return@postDelayed
                        val player = controller ?: return@postDelayed
                        if (player.playbackState == Player.STATE_BUFFERING && currentStation?.id == station.id) { bufferingSinceMs = null; handlePlaybackFailure("BUFFER TIMEOUT") }
                    }, 8_000L)
                }
                Player.STATE_READY -> { bufferingSinceMs = null; playerOffline = false; if (controller?.isPlaying != true) playerReconnecting = false }
                Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null
            }
            updatePlayerButton()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val stationId = mediaItem?.mediaId ?: return
            val savedStationId = playbackStateStore.loadLastStationId()
            if (
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                !savedStationId.isNullOrBlank() &&
                stationId != savedStationId
            ) return
            val station = catalog.firstOrNull { it.id == stationId } ?: return
            val stationChanged = currentStation?.id != station.id
            currentStation = station
            restoredStationId = station.id
            if (stationChanged) {
                currentStreamIndex = 0
                streamRetryCount = 0
                bufferingSinceMs = null
                addRecentStation(station)
            }
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
            handlePlaybackFailure("STREAM ERROR")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(SettingsPreferences.getThemeMode(this))
        super.onCreate(savedInstanceState)
        applyKeepScreenOnPreference()
        restoredStationId = savedInstanceState?.getString(KEY_STATION_ID)
        restoringAfterConfig = savedInstanceState != null
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureImmersiveWindow()
        CrashReporter.install(this)
        window.decorView.post { CrashReporter.showPending(this) }
        applyCarSafeArea()
        setupNavigation()
        settingsBackCallback = object : OnBackPressedCallback(activeNavId == R.id.navSettings) {
            override fun handleOnBackPressed() { closeSettings() }
        }
        onBackPressedDispatcher.addCallback(this, settingsBackCallback)
        ImageLoader.initialize(this)
        renderPlayer()
        userStateStore = UserStateStore(this)
        playbackStateStore = PlaybackStateStore(this)
        catalogCacheStore = CatalogCacheStore(this)
        catalogRepository = ApiCatalogRepository(this, onProgress = { bytes, total -> runOnUiThread { updateSyncProgress(bytes, total) } }, cacheStore = catalogCacheStore)
        cacheExecutor.execute {
            val cached = catalogCacheStore.load()?.also { it.stationsById }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shouldRefreshCatalog = CatalogRefreshPolicy.shouldRefresh(
                    stationCount = cached?.stations?.size ?: 0,
                    savedAt = cached?.savedAt ?: 0L,
                    now = System.currentTimeMillis(),
                    refreshIntervalMillis = CATALOG_REFRESH_MS
                )
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
        if (restoredStationId == null) restoredStationId = playbackStateStore.loadLastStationId() ?: recentIds.firstOrNull()
        val token = SessionToken(this, ComponentName(this, CarRadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            resolveCurrentStationFromPlayer()
            syncPlayerPlaylist()
            restoreStationFromState()
            restorePlaybackIfNeeded()
            renderPlayer()
        }, ContextCompat.getMainExecutor(this))
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
                    }
                    catalogRepository.loadGenres { genresResult ->
                        genresResult.onSuccess { genres -> remoteGenres = genres }
                    }
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
        if (currentStation?.id != wantedId && catalog.none { it.id == wantedId }) {
            currentStation = playbackStateStore.loadLastStationSnapshot()?.takeIf { it.id == wantedId }
        }
    }

    private fun restoreStationFromState() {
        val savedId = restoredStationId ?: playbackStateStore.loadLastStationId()
        val wantedId = PlaybackRestorePolicy.resolveStationId(
            restoredStationId = savedId,
            playerStationId = controller?.currentMediaItem?.mediaId,
            recentStationId = if (savedId == null && catalogReady) userStateStore.loadRecentIds().firstOrNull() else null,
            availableStationIds = catalog.asSequence().map { it.id }.toSet()
        )
        val restored = wantedId?.let { id -> catalog.firstOrNull { it.id == id } }
            ?: playbackStateStore.loadLastStationSnapshot()?.takeIf { it.id == savedId }
        if (restored != null) {
            val stationToRestore = restored.copy(favorite = favoriteIds.contains(restored.id))
            if (catalog.none { it.id == stationToRestore.id }) catalog.add(stationToRestore)
            currentStation = stationToRestore
        } else if (savedId == null && catalogReady && !restoringAfterConfig && currentStation == null) {
            currentStation = catalog.firstOrNull()
        }
        if (currentStation != null) restoredStationId = currentStation?.id
    }

    private fun restorePlaybackIfNeeded() {
        if (!catalogReady || restoringAfterConfig || startupPlaybackRestored) return
        val player = controller ?: return
        val hasSavedStation = !restoredStationId.isNullOrBlank() ||
            !playbackStateStore.loadLastStationId().isNullOrBlank()
        val station = currentStation ?: (if (!hasSavedStation) catalog.firstOrNull() else null) ?: return
        playbackStateStore.saveLastStation(station)
        if (player.isPlaying && player.currentMediaItem?.mediaId == station.id) {
            startupPlaybackRestored = true
            return
        }
        ensureStationInPlaylist(station)
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id } ?: return
        currentStreamIndex = 0; streamRetryCount = 0; failedStationIds.clear()
        showPlayerState("CONNECTING...", "Restoring last station")
        player.seekTo(index, 0L); player.prepare(); player.play(); startupPlaybackRestored = true
    }

    private fun resolveCurrentStationFromPlayer() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        val station = catalog.firstOrNull { it.id == id }
            ?: playbackStateStore.loadLastStationSnapshot()?.takeIf { it.id == id }
            ?: return
        currentStation = station
        restoredStationId = station.id
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_STATION_ID, currentStation?.id ?: controller?.currentMediaItem?.mediaId)
        super.onSaveInstanceState(outState)
    }

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
            nav.layoutParams = LinearLayout.LayoutParams(
                dp(if (profile.isCarReference) 188 else 250),
                -1
            )
            nav.setPadding(dp(12), dp(18), dp(12), dp(18))
            setupLandscapeSidebar(profile)
        } else {
            binding.root.orientation = LinearLayout.VERTICAL
            binding.root.setPadding(0, 0, 0, 0)
            binding.contentContainer.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)

            nav.orientation = LinearLayout.HORIZONTAL
            nav.gravity = Gravity.CENTER
            nav.layoutParams = LinearLayout.LayoutParams(-1, dp(96))
            nav.setPadding(dp(12), dp(8), dp(12), dp(8))
            brandIcon.visibility = View.GONE
            brandLabel.visibility = View.GONE
            binding.navNowPlayingCard.visibility = View.GONE
            navNowLogoView = null
            navNowFavoriteView = null
            navNowStationView = null
            navNowTrackView = null

            styleNavButtons(landscape = false)
        }
    }

    /** Sidebar footer: sync progress on the left and the Settings gear in place of the version on the right. */
    private fun buildSyncFooter(): View {
        val muted = getColor(R.color.auto_text_muted)
        val sync = (syncStatusView ?: findViewById<TextView>(R.id.syncStatus) ?: TextView(this)).also {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        syncStatusView = sync
        sync.apply {
            background = null
            setTextColor(muted)
            textSize = 10f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(4), 0, 0, 0)
            visibility = if (syncActive) View.VISIBLE else View.GONE
        }
        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(muted)
            setBackgroundResource(R.drawable.bg_nav_item)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = getString(R.string.settings_title)
            isClickable = true
            isFocusable = true
            UiMotion.pressFeedback(this)
            setOnClickListener { openSettings() }
        }
        settingsFooterButton = settingsButton
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sync, LinearLayout.LayoutParams(0, -2, 1f))
            addView(settingsButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(6) })
        }
    }

    private fun setupLandscapeSidebar(profile: UiProfile) {
        val nav = binding.navContainer
        val brandIcon = binding.navBrandIcon
        val brandLabel = binding.navBrandLabel
        val mini = binding.navMiniPlayerContainer
        val buttons = listOf(
            binding.navPlayer, binding.navCountries,
            binding.navFavorites, binding.navRecents, binding.navSearch
        )
        listOf<View>(brandIcon, brandLabel, binding.navNowPlayingCard, mini).forEach { (it.parent as? ViewGroup)?.removeView(it) }
        buttons.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        nav.removeAllViews()
        brandIcon.visibility = View.VISIBLE
        brandIcon.setImageResource(R.drawable.app_logo)
        brandIcon.imageTintList = null
        brandIcon.background = null
        brandIcon.setBackgroundColor(Color.TRANSPARENT)
        brandIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        brandIcon.alpha = 1f
        brandIcon.setPadding(0, 0, 0, 0)
        brandLabel.visibility = View.VISIBLE
        brandLabel.text = getString(R.string.app_name)
        brandLabel.maxLines = 2
        brandLabel.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        brandLabel.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        brandLabel.textSize = 13f
        brandLabel.setPadding(dp(10), 0, dp(4), 0)

        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        (brandIcon.parent as? ViewGroup)?.removeView(brandIcon)
        (brandLabel.parent as? ViewGroup)?.removeView(brandLabel)
        brandHeader.addView(brandIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        brandHeader.addView(brandLabel)
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        list.addView(brandHeader, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        buttons.forEach { list.addView(it) }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        nav.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        mini.visibility = View.GONE
        nav.addView(mini, LinearLayout.LayoutParams(-1, dp(86)))
        nav.addView(buildSyncFooter(), LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        styleNavButtons(landscape = true)
    }

    private fun styleNavButtons(landscape: Boolean) {
        val buttons = listOf(
            binding.navPlayer to R.drawable.ic_radio,
            binding.navCountries to R.drawable.ic_globe,
            binding.navSettings to R.drawable.ic_settings,
            binding.navFavorites to R.drawable.ic_star_outline,
            binding.navRecents to R.drawable.ic_history,
            binding.navSearch to R.drawable.ic_search
        )
        buttons.forEach { (button, icon) ->
            if (landscape) {
                button.layoutParams = LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) }
                button.textSize = 15f
                button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                button.setPadding(dp(16), 0, dp(12), 0)
                button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                button.compoundDrawablePadding = dp(14)
            } else {
                button.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), dp(0), dp(4), dp(0)) }
                button.textSize = 13f
                button.gravity = Gravity.CENTER
                button.setPadding(dp(2), dp(6), dp(2), dp(6))
                button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
                button.compoundDrawablePadding = dp(7)
                UiMotion.pressFeedback(button)
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
        // Genre data and rendering remain available; only the navigation entry is temporarily disabled.
        binding.navSettings.setOnClickListener { openSettings() }
        binding.navFavorites.setOnClickListener { showScreen("FAVORITES") { renderFavorites() } }
        binding.navRecents.setOnClickListener { showScreen("RECENT") { renderRecents() } }
        binding.navSearch.setOnClickListener { showScreen("SEARCH") { renderSearch() } }
        binding.navNowPlayingCard.setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
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
            R.id.navSettings -> renderSettings()
            R.id.navFavorites -> renderFavorites()
            R.id.navRecents -> renderRecents()
            R.id.navSearch -> renderSearch()
        }
    }

    private fun configureImmersiveWindow() {
        window.statusBarColor = getColor(R.color.auto_surface)
        window.navigationBarColor = getColor(R.color.auto_surface)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
    override fun onUserInteraction() { super.onUserInteraction(); cancelAutoOpenPlayer() }
    private fun scheduleAutoOpenPlayer(station: Station) {
        cancelAutoOpenPlayer()
        if (activeNavId == R.id.navPlayer) return
        pendingAutoOpenStationId = station.id
        pendingAutoOpenSourceNavId = activeNavId
        autoOpenHandler.postDelayed(autoOpenPlayerRunnable, 10_000L)
    }
    private fun cancelAutoOpenPlayer() {
        pendingAutoOpenStationId = null
        pendingAutoOpenSourceNavId = null
        autoOpenHandler.removeCallbacks(autoOpenPlayerRunnable)
    }

    private fun showScreen(title: String, content: () -> Unit) { setActiveNav(when (title) { "PLAYER" -> R.id.navPlayer; "COUNTRIES" -> R.id.navCountries; "GENRES" -> R.id.navGenres; "SETTINGS" -> R.id.navSettings; "FAVORITES" -> R.id.navFavorites; "RECENT" -> R.id.navRecents; else -> R.id.navSearch }); content() }
    private fun openSettings() {
        if (activeNavId != R.id.navSettings) settingsReturnNavId = activeNavId
        showScreen("SETTINGS") { renderSettings() }
        settingsBackCallback.isEnabled = true
    }
    private fun closeSettings() {
        if (activeNavId != R.id.navSettings) return
        settingsBackCallback.isEnabled = false
        setActiveNav(settingsReturnNavId)
        renderActiveScreen()
    }
    private fun applyKeepScreenOnPreference() {
        if (SettingsPreferences.isKeepScreenOn(this)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun renderSettings() {
        val root = screenRoot()
        root.addView(topBar(
            getString(R.string.settings_eyebrow),
            getString(R.string.settings_title),
            getString(R.string.settings_subtitle),
            R.drawable.ic_settings,
            listOf(iconButton(R.drawable.ic_back, getString(R.string.back)) { closeSettings() })
        ))
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        val sections = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun addSection(title: String, description: String, extraView: View? = null) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_surface)
                setPadding(dp(20), dp(16), dp(20), dp(16))
                addView(TextView(this@CarMainActivity).apply {
                    text = title
                    textSize = if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 19f else 17f
                    setTextColor(getColor(R.color.auto_text_main))
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    includeFontPadding = false
                })
                addView(TextView(this@CarMainActivity).apply {
                    text = description
                    textSize = if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 15f else 13f
                    setTextColor(getColor(R.color.auto_text_muted))
                    setPadding(0, dp(8), 0, 0)
                    includeFontPadding = false
                })
                extraView?.let { view ->
                    addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }
            }
            sections.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        val animationToggle = androidx.appcompat.widget.SwitchCompat(this).apply {
            text = getString(R.string.settings_animation_enabled)
            textSize = if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 15f else 13f
            setTextColor(getColor(R.color.auto_text_main))
            isChecked = LavaLampView.isAnimationEnabled(this@CarMainActivity)
            setOnCheckedChangeListener { _, enabled ->
                LavaLampView.setAnimationEnabled(this@CarMainActivity, enabled)
                playerLavaView?.setAnimationEnabled(enabled)
                miniLavaView?.setAnimationEnabled(enabled)
            }
        }
        val themeButton = Button(this).apply {
            text = getString(R.string.settings_choose_theme)
            isAllCaps = false
            setOnClickListener {
                val modes = intArrayOf(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    AppCompatDelegate.MODE_NIGHT_NO,
                    AppCompatDelegate.MODE_NIGHT_YES
                )
                val labels = arrayOf(
                    getString(R.string.settings_theme_system),
                    getString(R.string.settings_theme_light),
                    getString(R.string.settings_theme_dark)
                )
                val selectedIndex = modes.indexOf(SettingsPreferences.getThemeMode(this@CarMainActivity)).coerceAtLeast(0)
                AlertDialog.Builder(this@CarMainActivity)
                    .setTitle(R.string.settings_choose_theme)
                    .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                        SettingsPreferences.setThemeMode(this@CarMainActivity, modes[which])
                        AppCompatDelegate.setDefaultNightMode(modes[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        addSection(getString(R.string.settings_appearance), getString(R.string.settings_appearance_pending), themeButton)
        addSection(getString(R.string.settings_animation), getString(R.string.settings_animation_pending), animationToggle)
        val keepScreenOnToggle = androidx.appcompat.widget.SwitchCompat(this).apply {
            text = getString(R.string.settings_keep_screen_on)
            textSize = if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 15f else 13f
            setTextColor(getColor(R.color.auto_text_main))
            isChecked = SettingsPreferences.isKeepScreenOn(this@CarMainActivity)
            setOnCheckedChangeListener { _, enabled ->
                SettingsPreferences.setKeepScreenOn(this@CarMainActivity, enabled)
                applyKeepScreenOnPreference()
            }
        }
        addSection(getString(R.string.settings_display), getString(R.string.settings_keep_screen_on_description), keepScreenOnToggle)
        val refreshCatalogButton = Button(this).apply {
            text = getString(R.string.settings_catalog_refresh)
            isAllCaps = false
            isEnabled = !syncActive
            setOnClickListener {
                if (!syncActive) loadRemoteCatalog()
            }
        }
        addSection(getString(R.string.settings_catalog), getString(R.string.settings_catalog_pending), refreshCatalogButton)
        addSection(getString(R.string.settings_about), getString(R.string.settings_about_pending))
        sections.addView(TextView(this).apply {
            text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
            textSize = 13f
            setTextColor(getColor(R.color.auto_text_muted))
            includeFontPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })
        scroll.addView(sections)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }
    private fun setActiveNav(activeId: Int) {
        val previousActiveId = activeNavId
        activeNavId = activeId
        intArrayOf(R.id.navPlayer, R.id.navCountries, R.id.navFavorites, R.id.navRecents, R.id.navSearch, R.id.navSettings).forEach { id ->
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
                if (active && previousActiveId != activeId) {
                    UiMotion.pulse(this, 0.55f)
                }
            }
        }
        settingsFooterButton?.apply {
            val active = activeId == R.id.navSettings
            imageTintList = ColorStateList.valueOf(getColor(if (active) R.color.auto_accent else R.color.auto_text_muted))
            setBackgroundResource(if (active) R.drawable.bg_nav_item_active else R.drawable.bg_nav_item)
            alpha = if (active) 1f else 0.78f
        }
    }

    private fun renderPlayer() {
        activeStationAdapter = null
        playerLogoView = null
        playerBackdropView = null
        playerLavaView = null
        playerMetaChips = null
        playerTrackView = null
        playerStationView = null
        playerMetaView = null
        playerStatusView = null
        playerFavoriteButton = null
        playPauseIcon = null
        playPauseLabel = null

        val station = currentStation ?: catalog.firstOrNull()
        val root = screenRoot()

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
            clipChildren = true
            clipToOutline = true
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
            // Round the image corners to match the player card so it never pokes outside the block
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, resources.getDimension(R.dimen.radius_xl) - dp(1))
                }
            }
        }
        playerBackdropView = ambient
        loadStationBackdrop(station, ambient, trim = true)
        // Inset by the 1dp card stroke; hero itself has no padding, so the image fills the whole block
        hero.addView(ambient, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        // Bring the station-colour animation to the automotive player without changing its established controls/layout.
        val lavaLamp = LavaLampView(this).apply {
            contentDescription = null
            isClickable = false
            isFocusable = false
            lastPlayerLavaPalette?.let { setPaletteColors(it, animate = false) }
        }
        playerLavaView = lavaLamp
        if (station.logo.isNullOrBlank()) {
            lavaLamp.alpha = 0.94f
            lavaLamp.setPalette(null)
        } else {
            val previousPalette = lastPlayerLavaPalette
            if (previousPalette != null) {
                lavaLamp.alpha = 0.94f
                lavaLamp.setPaletteColors(previousPalette)
            } else {
                lavaLamp.alpha = 0f
            }
        }
        hero.addView(lavaLamp, FrameLayout.LayoutParams(-1, -1))

        val foreground = LinearLayout(this).apply {
            orientation = if (uiProfile.isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (uiProfile.isLandscape) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
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
            if (uiProfile.isLandscape) {
                LinearLayout.LayoutParams(dp(210), dp(210)).apply { marginEnd = dp(34) }
            } else {
                LinearLayout.LayoutParams(dp(170), dp(170)).apply { bottomMargin = dp(18) }
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info.addView(label("NOW PLAYING"), LinearLayout.LayoutParams(-1, dp(24)))

        val stationTitle = marqueeTextView(station.name, if (uiProfile.isLandscape) 34f else 28f, R.color.auto_text_main, true)
        playerStationView = stationTitle
        info.addView(stationTitle, LinearLayout.LayoutParams(-1, dp(if (uiProfile.isLandscape) 48 else 42)))

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        playerMetaChips = chips
        bindPlayerMetadataChips(chips, station)
        info.addView(chips, LinearLayout.LayoutParams(-1, dp(34)))

        val trackTitle = marqueeTextView(nowPlayingText(station), if (uiProfile.isLandscape) 25f else 22f, R.color.auto_text_main, true)
        playerTrackView = trackTitle
        info.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(if (uiProfile.isLandscape) 42 else 38)).apply { topMargin = dp(12) })

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
            LinearLayout.LayoutParams(dp(if (uiProfile.isLandscape) 230 else 190), dp(32)).apply {
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
        hero.addView(favorite, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(22)
            rightMargin = dp(22)
        })

        root.addView(hero, LinearLayout.LayoutParams(-1, 0, if (uiProfile.isLandscape) 1f else 0.8f))
        updateNavNowPlaying(station)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        }

        val shuffle = iconButton(R.drawable.ic_shuffle, "Shuffle") { catalog.randomOrNull()?.let { playStation(it) } }
        controls.addView(
            shuffle,
            LinearLayout.LayoutParams(dp(if (uiProfile.isLandscape) 74 else 62), dp(if (uiProfile.isLandscape) 74 else 62))
                .apply { marginEnd = dp(10) }
        )

        val prev = iconButton(R.drawable.ic_skip_previous, "Previous station") { playAdjacentStation(-1) }
        controls.addView(
            prev,
            LinearLayout.LayoutParams(dp(if (uiProfile.isLandscape) 78 else 62), dp(if (uiProfile.isLandscape) 78 else 62))
                .apply { marginEnd = dp(10) }
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
            LinearLayout.LayoutParams(if (uiProfile.isLandscape) dp(246) else dp(156), dp(90))
                .apply { marginEnd = dp(10) }
        )

        val next = iconButton(R.drawable.ic_skip_next, "Next station") { playAdjacentStation(1) }
        controls.addView(
            next,
            LinearLayout.LayoutParams(dp(if (uiProfile.isLandscape) 70 else 58), dp(if (uiProfile.isLandscape) 70 else 58))
                .apply { marginEnd = dp(10) }
        )

        val wideControls = uiProfile.isLandscape
        if (wideControls) {
            controls.removeAllViews(); controls.gravity = Gravity.CENTER; controls.setPadding(0, dp(8), 0, dp(4))
            fun addControl(view: View, weight: Float, heightDp: Int) { controls.addView(view, LinearLayout.LayoutParams(0, dp(heightDp), weight).apply { marginEnd = dp(6) }) }
            addControl(shuffle, 1f, 84); addControl(prev, 1f, 84); addControl(play, 2f, 90); addControl(next, 1f, 84)
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(if (wideControls) 98 else 104)))

        updatePlayerButton()
        binding.contentContainer.setScreenContent(root)
    }

    private fun renderCountries() {
        val all = if (remoteCountries.isNotEmpty()) remoteCountries.sortedBy { it.name } else catalog.groupBy { it.countryCode }.map { CountryItem(it.value.first().country, it.key, flagFor(it.key), it.value.size) }.sortedBy { it.name }
        val root = screenRoot()
        root.addView(topBar("BROWSE", "Countries", "${all.size} countries with available radio", R.drawable.ic_globe))
        val columns = if (uiProfile.isLandscape) 3 else 1
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@CarMainActivity, columns)
            adapter = CountryAdapter(all) { country -> loadAndRenderStations(country.name, country = country.code, onBack = { renderCountries() }, restorePosition = lastCountryPosition, browseKind = "country") }
            setPadding(0, 0, 0, dp(8))
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }
    private fun renderGenres() { val genres = if (remoteGenres.isNotEmpty()) remoteGenres.sortedBy { it.name } else catalog.groupBy { it.genre }.map { GenreItem(it.key, it.value.size) }.sortedBy { it.name }; val root = screenRoot(); root.addView(topBar("BROWSE", "Genres", "${genres.size} genres in the catalog", R.drawable.ic_music_note)); val recycler = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@CarMainActivity, if (uiProfile.isLandscape) 3 else 1); adapter = GenreAdapter(genres) { genre -> loadAndRenderStations(genre.name, genre = genre.name, onBack = { renderGenres() }, browseKind = "genre") }; setPadding(0, 0, 0, dp(8)); clipToPadding = false }; root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f)); binding.contentContainer.setScreenContent(root) }
    private fun loadAndRenderStations(title: String, country: String? = null, genre: String? = null, onBack: () -> Unit, restorePosition: Int = 0, browseKind: String? = null) {
        if (browseKind == "country" && country != null) { lastCountryCode = country; lastCountryTitle = title }
        if (browseKind == "genre" && genre != null) { lastGenreName = genre }
        val browseKey = when {
            country != null -> "country:$country"
            genre != null -> "genre:$genre"
            else -> null
        }
        browseKey?.let { key ->
            val cachedPage = browseStationPages[key]
            if (!cachedPage.isNullOrEmpty()) {
                renderStationList(title, cachedPage, onBack, country, genre, browseHasMore[key] == true, 1, restorePosition, browseKind)
                return
            }
        }
        val root = screenRoot()
        root.addView(topBar("STATIONS", title, "Loading stations…", R.drawable.ic_list))
        binding.contentContainer.setScreenContent(root)
        catalogRepository.loadStations(country = country, genre = genre, limit = 200) { result ->
            result.onSuccess { stations ->
                browseKey?.let { key -> browseStationPages[key] = stations; browseHasMore[key] = stations.size == 200 }
                catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } })
                applyPersistedState()
                syncPlayerPlaylist()
                renderStationList(title, stations, onBack, country, genre, stations.size == 200, 1, restorePosition, browseKind)
            }.onFailure {
                renderStationList(title, emptyList(), onBack, country, genre, false, 1, restorePosition, browseKind)
                showPlayerState("CATALOG ERROR", "Unable to load stations")
            }
        }
    }

    private fun renderFavorites() {
        val ids = favoriteIds.toList()
        val loaded = ids.mapNotNull { id -> catalog.find { it.id == id } ?: catalogCacheStore.findStation(id) }
        val missing = ids.filterNot { id -> loaded.any { it.id == id } }
        if (missing.isEmpty()) { renderFavoritesScreen(loaded); return }
        val root = screenRoot()
        root.addView(topBar("LIBRARY", "Favorites", "Loading saved stations...", R.drawable.ic_star_filled))
        binding.contentContainer.setScreenContent(root)
        fun loadMissing(index: Int) {
            if (index >= missing.size) { applyPersistedState(); renderFavoritesScreen(ids.mapNotNull { id -> catalog.find { it.id == id } }); return }
            catalogCacheStore.findStation(missing[index])?.let { cached -> if (catalog.none { it.id == cached.id }) catalog.add(cached); ensureStationInPlaylist(cached); loadMissing(index + 1); return }
            catalogRepository.loadStation(missing[index]) { result ->
                result.onSuccess { station -> if (catalog.none { it.id == station.id }) catalog.add(station); ensureStationInPlaylist(station) }
                loadMissing(index + 1)
            }
        }
        loadMissing(0)
    }

    private fun renderFavoritesScreen(stations: List<Station>) {
        favoritesRecyclerView = null
        val root = screenRoot()
        root.addView(topBar("LIBRARY", "Favorites", if (stations.isEmpty()) "Your saved stations" else "${stations.size} saved stations", R.drawable.ic_star_filled))
        if (stations.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(22), dp(18), dp(22), dp(18)) }
            empty.addView(ImageView(this).apply { setImageResource(R.drawable.ic_star_filled); imageTintList = ColorStateList.valueOf(getColor(R.color.auto_favorite)) }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(marqueeTextView("No favorites yet", 20f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(-1, dp(32))); addView(marqueeTextView("Tap the star on any station to keep it here.", 13f, R.color.auto_text_muted, false, false), LinearLayout.LayoutParams(-1, dp(28))) }
            empty.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(empty, LinearLayout.LayoutParams(-1, dp(96)).apply { bottomMargin = dp(4) })
        } else {
            lateinit var recycler: RecyclerView
            val adapter = StationAdapter(
                stations,
                onPlay = { lastFavoritesPosition = (recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0; playStation(it) },
                onFavorite = { toggleFavorite(it) },
                isCurrent = { it.id == currentStation?.id },
                isPlaying = { it.id == currentStation?.id && controller?.isPlaying == true },
                onTogglePlay = { toggleStationPlayback(it) }
            )
            recycler = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@CarMainActivity, if (uiProfile.isLandscape) 3 else 1)
                this.adapter = adapter; setPadding(0,0,0,dp(8)); clipToPadding = false
                
                addOnScrollListener(object : RecyclerView.OnScrollListener() { override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { lastFavoritesPosition = (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0 } })
            }
            favoritesRecyclerView = recycler
            activeStationAdapter = adapter
            root.addView(recycler, LinearLayout.LayoutParams(-1,0,1f))
            recycler.post { if (lastFavoritesPosition > 0 && adapter.itemCount > lastFavoritesPosition) (recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(lastFavoritesPosition,0) }
        }
        binding.contentContainer.setScreenContent(root)
    }
    private fun renderRecents() { renderSavedStations(ids = recentIds.toList(), title = "Recently played", columns = if (uiProfile.isLandscape) 3 else 1, highlightCurrent = false) }
    private fun renderSavedStations(ids: List<String>, title: String, columns: Int = 1, highlightCurrent: Boolean = true) { val loaded = ids.mapNotNull { id -> catalog.find { it.id == id } ?: catalogCacheStore.findStation(id) }.toMutableList(); val missing = ids.filterNot { id -> loaded.any { it.id == id } }; if (missing.isEmpty()) { renderStationList(title, loaded, { renderPlayer() }, columns = columns, highlightCurrent = highlightCurrent); return }; setActiveNav(if (title.startsWith("FAVORITE")) R.id.navFavorites else R.id.navRecents); binding.contentContainer.removeAllViews(); val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(titleBlock(title, "Loading saved stations...", if (title.startsWith("FAVORITE")) R.drawable.ic_star_filled else R.drawable.ic_history)); binding.contentContainer.setScreenContent(root); fun loadMissing(index: Int) { if (index >= missing.size) { applyPersistedState(); renderStationList(title, ids.mapNotNull { id -> catalog.find { it.id == id } }, { renderPlayer() }, columns = columns, highlightCurrent = highlightCurrent); return }; catalogCacheStore.findStation(missing[index])?.let { cached -> if (catalog.none { it.id == cached.id }) catalog.add(cached); ensureStationInPlaylist(cached); loadMissing(index + 1); return }; catalogRepository.loadStation(missing[index]) { result -> result.onSuccess { station -> if (catalog.none { it.id == station.id }) catalog.add(station); ensureStationInPlaylist(station) }; loadMissing(index + 1) } }; loadMissing(0) }
    private fun renderStationList(title: String, stations: List<Station>, onBack: () -> Unit, country: String? = null, genre: String? = null, canLoadMore: Boolean = false, columns: Int = 1, restorePosition: Int = 0, browseKind: String? = null, highlightCurrent: Boolean = true) {
    val root = screenRoot()
    val header = topBar("STATIONS", title, if (stations.isEmpty()) "No stations found" else "${stations.size} stations available", R.drawable.ic_list, listOf(iconButton(R.drawable.ic_back, "Back") { onBack() }))
    root.addView(header)
    val countLabel = ((header as? LinearLayout)?.getChildAt(0) as? LinearLayout)?.getChildAt(2) as? TextView
    lateinit var recycler: RecyclerView
    val stationAdapter = StationAdapter(
        stations,
        onPlay = {
            val position = (recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
            if (browseKind == "country") lastCountryPosition = position
            if (browseKind == "genre") lastGenrePosition = position
            playStation(it)
        },
        onFavorite = { toggleFavorite(it) },
        isCurrent = { highlightCurrent && it.id == currentStation?.id },
        isPlaying = { it.id == currentStation?.id && controller?.isPlaying == true },
        onTogglePlay = { toggleStationPlayback(it) }
    )
    // Infinite scroll: the next page is fetched when the list nears its end (no "Load more" button).
    val browseKey = when {
        country != null -> "country:$country"
        genre != null -> "genre:$genre"
        else -> null
    }
    var moreAvailable = canLoadMore && (country != null || genre != null)
    var loadingMore = false
    var nextPageOffset = stations.size
    var retryAfterMs = 0L
    var loadedStations: List<Station> = stations
    countLabel?.text = if (loadedStations.isEmpty()) "No stations found" else "${loadedStations.size} stations available"
    fun loadNextPage() {
        if (!moreAvailable || loadingMore || System.currentTimeMillis() < retryAfterMs) return
        loadingMore = true
        catalogRepository.loadStations(country = country, genre = genre, limit = 200, offset = nextPageOffset) { result ->
            loadingMore = false
            result.onSuccess { nextPage ->
                retryAfterMs = 0L
                nextPageOffset += nextPage.size
                moreAvailable = nextPage.size == 200
                val previousStationCount = loadedStations.size
                loadedStations = (loadedStations + nextPage).distinctBy { it.id }
                browseKey?.let { key -> browseStationPages[key] = loadedStations; browseHasMore[key] = moreAvailable }
                countLabel?.text = if (loadedStations.isEmpty()) "No stations found" else "${loadedStations.size} stations available"
                catalog.addAll(nextPage.filter { station -> catalog.none { it.id == station.id } })
                applyPersistedState()
                syncPlayerPlaylist()
                stationAdapter.submitList(loadedStations)
                if (nextPage.isNotEmpty() && loadedStations.size == previousStationCount && moreAvailable) {
                    // A duplicate-only page still advances the source offset; continue so the user can reach later unique stations.
                    recycler.post { loadNextPage() }
                }
            }.onFailure {
                // Pause retries while the network is unavailable instead of requesting on every scroll callback.
                retryAfterMs = System.currentTimeMillis() + 5_000L
            }
        }
    }
    recycler = RecyclerView(this).apply {
        layoutManager = GridLayoutManager(this@CarMainActivity, if (uiProfile.isLandscape) 3 else 1)
        adapter = stationAdapter
        setPadding(0, 0, 0, dp(8))
        clipToPadding = false
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                    ?.findLastVisibleItemPosition() ?: -1
                if (dy >= 0 && lastVisible >= stationAdapter.itemCount - 8) loadNextPage()
                val position = (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                    ?.findFirstVisibleItemPosition() ?: return
                if (browseKind == "country") lastCountryPosition = position
                if (browseKind == "genre") lastGenrePosition = position
            }
        })
        post { if (restorePosition > 0 && stationAdapter.itemCount > restorePosition) (layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(restorePosition, 0) }
    }
    activeStationAdapter = stationAdapter
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
    binding.contentContainer.setScreenContent(root)
}
    private fun renderSearch() { val root = screenRoot(); if (!(uiProfile.isLandscape && !uiProfile.useOnScreenKeypad)) { root.addView(topBar("FIND", "Search", "Station, city, country or genre", R.drawable.ic_search)) }; val input = EditText(this).apply { hint = "Search station, city, country or genre"; setTextColor(getColor(R.color.auto_text_main)); setHintTextColor(getColor(R.color.auto_text_muted)); textSize = 17f; setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN; setOnEditorActionListener { v, _, _ -> (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0); true }; setShowSoftInputOnFocus(uiProfile.useOnScreenKeypad.not()); setBackgroundResource(R.drawable.bg_input); setPadding(dp(16), 0, dp(16), 0); setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0); compoundDrawablePadding = dp(10); compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.auto_text_muted)) }; root.addView(input, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(14) }); val results = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@CarMainActivity, if (uiProfile.isLandscape) 3 else 1) }; val adapter = StationAdapter(
            emptyList(),
            onPlay = { playStation(it) },
            onFavorite = { toggleFavorite(it); results.adapter?.notifyDataSetChanged() },
            isCurrent = { it.id == currentStation?.id },
            isPlaying = { it.id == currentStation?.id && controller?.isPlaying == true },
            onTogglePlay = { toggleStationPlayback(it) }
        ); results.adapter = adapter; activeStationAdapter = adapter; root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f)); if (uiProfile.useOnScreenKeypad) root.addView(buildSearchKeypad(input, adapter)); input.addTextChangedListener(SimpleTextWatcher { text -> lastSearchQuery = text.toString(); val query = text.toString().trim(); val requestId = ++searchRequestId; searchHandler.removeCallbacksAndMessages(null); if (query.isBlank()) adapter.submitList(emptyList()) else if (query.length < 2) updateSearchResults(query, adapter) else searchHandler.postDelayed({ catalogRepository.loadStations(query = query, limit = 50) { result -> if (requestId != searchRequestId) return@loadStations; result.onSuccess { stations -> catalog.addAll(stations.filter { station -> catalog.none { it.id == station.id } }); applyPersistedState(); adapter.submitList(stations) } } }, 250L) });
    if (lastSearchQuery.isNotBlank()) input.setText(lastSearchQuery)
    // Open search ready for immediate typing; keep the in-app keypad on the car HMI.
    input.postDelayed({
        input.requestFocus()
        if (!uiProfile.useOnScreenKeypad) {
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(input, 0)
        }
    }, 200L)
    binding.contentContainer.setScreenContent(root) }
    private fun updateSearchResults(query: String, adapter: StationAdapter) { val q = query.trim(); if (q.isEmpty()) { adapter.submitList(emptyList()); return }; adapter.submitList(catalog.filter { it.name.contains(q, true) || it.country.contains(q, true) || it.genre.contains(q, true) || it.city.contains(q, true) }) }
    private fun toggleStationPlayback(station: Station) {
        val player = controller
        if (player != null &&
            currentStation?.id == station.id &&
            player.currentMediaItem?.mediaId == station.id
        ) {
            if (player.isPlaying) player.pause() else player.play()
            updatePlayerButton()
        } else {
            playStation(station)
        }
    }

    private fun playStation(station: Station, renderPlayerScreen: Boolean = true) {
        currentStation = station;
        playbackStateStore.saveLastStation(station); restoredStationId = station.id; currentStreamIndex = 0; streamRetryCount = 0; bufferingSinceMs = null
        failedStationIds.remove(station.id)
        retryHandler.removeCallbacksAndMessages(null); ensureStationInPlaylist(station)
        controller?.let { player -> val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id }; if (index != null) { player.replaceMediaItem(index, stationToMediaItem(station, 0)); player.seekTo(index, 0L); player.play() } else playCurrentStream() }
        addRecentStation(station)
        updateCurrentStationUi(station)
        updatePlayerButton()
        if (activeNavId != R.id.navPlayer && renderPlayerScreen) scheduleAutoOpenPlayer(station)
    }
    private fun playCurrentStream() { val player = controller ?: return; val station = currentStation ?: return; if (station.streams.isEmpty()) { showPlayerState("STREAM UNAVAILABLE", "No working stream"); return }; ensureStationInPlaylist(station); val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id } ?: return; showPlayerState("CONNECTING...", "Opening stream " + (currentStreamIndex + 1)); player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex)); player.seekTo(index, 0L); player.prepare(); player.play() }
    private fun addRecentStation(station: Station) { val updated = RecentStationsPolicy.add(recentIds, station.id); recentIds.clear(); recentIds.addAll(updated); persistRecents() }
    private fun syncPlayerPlaylist() { val player = controller ?: return; if (player.mediaItemCount > 0) return; val items = catalog.filter { it.streams.isNotEmpty() }.distinctBy { it.id }.take(200).map { stationToMediaItem(it, 0) }; if (items.isNotEmpty()) player.setMediaItems(items, false) }
    private fun ensureStationInPlaylist(station: Station) { val player = controller ?: return; val exists = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == station.id }; if (!exists) player.addMediaItem(stationToMediaItem(station, 0)) }
    private fun updateNowPlayingArtist(artist: String) {
        val station = currentStation ?: return
        val updated = station.copy(artist = artist.trim())
        currentStation = updated
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()
        playbackStateStore.saveLastStation(updated)
        val text = nowPlayingText(updated)
        playerTrackView?.text = text
        miniTrackView?.text = text
        navNowTrackView?.text = text
        updateMarquee(playerTrackView)
        updateMarquee(miniTrackView)
        updateMarquee(navNowTrackView)
    }
    private fun stationToMediaItem(station: Station, streamIndex: Int): MediaItem {
        val stream = station.streams.getOrNull(streamIndex) ?: station.streams.first()
        return StationMediaItemFactory.create(station, stream)
    }
        private fun handlePlaybackFailure(reason: String) {
        val station = currentStation ?: return
        if (!hasValidatedInternet()) {
            retryHandler.removeCallbacksAndMessages(null)
            bufferingSinceMs = null
            playerOffline = true
            playerReconnecting = false
            controller?.pause()
            updatePlayerButton()
            showPlayerState("WAITING FOR INTERNET", "Will resume " + station.name + " when connection returns")
            return
        }
        when (val action = PlaybackRecoveryPolicy.onFailure(
            currentStreamIndex = currentStreamIndex,
            streamCount = station.streams.size,
            retryCount = streamRetryCount
        )) {
            is PlaybackRecoveryAction.NextStream -> switchToNextStream(reason)
            is PlaybackRecoveryAction.Retry -> {
                streamRetryCount = action.attempt
                playerReconnecting = true
                playerOffline = false
                showPlayerState("RECONNECTING...", "Retry " + action.attempt + "/" + PlaybackRecoveryPolicy.MAX_RETRIES)
                retryHandler.removeCallbacksAndMessages(null)
                retryHandler.postDelayed(
                    { if (currentStation?.id == station.id) playCurrentStream() },
                    if (action.attempt == 1) 1_500L else 3_500L
                )
            }
            PlaybackRecoveryAction.SwitchStation -> {
                playerReconnecting = true
                playerOffline = false
                showPlayerState("RECONNECTING...", "Switching station")
                switchToNextStation(reason)
            }
        }
    }

private fun switchToNextStream(reason: String) {
        val station = currentStation ?: return
        val player = controller ?: return
        if (currentStreamIndex + 1 >= station.streams.size) { handlePlaybackFailure(reason); return }
        currentStreamIndex++
        bufferingSinceMs = null
        showPlayerState(reason, "Opening stream " + (currentStreamIndex + 1))
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex))
        player.prepare(); player.play()
    }
    private fun updateNowPlayingTitle(rawTitle: String) {
        val station = currentStation ?: return
        val value = rawTitle.trim()
        if (value.isBlank()) return
        val parsed = TrackMetadataParser.parse(value)
        val updated = if (parsed.artist != null && parsed.title != null) {
            station.copy(songTitle = parsed.title, artist = parsed.artist)
        } else {
            station.copy(songTitle = parsed.title)
        }
        currentStation = updated
        restoredStationId = updated.id
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()
        playbackStateStore.saveLastStation(updated)
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
    val newValue = FavoriteStatePolicy.toggle(favoriteIds, station.id)
    if (newValue) favoriteIds.add(station.id) else favoriteIds.remove(station.id)
    catalog = catalog.map { if (it.id == station.id) it.copy(favorite = newValue) else it }.toMutableList()
    if (currentStation?.id == station.id) currentStation = currentStation?.copy(favorite = newValue)
    station.favorite = newValue
    persistFavorites()
    if (currentStation?.id == station.id) {
        playerFavoriteButton?.apply {
            setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
            UiMotion.pulse(this, 0.3f)
        }
    }
    if (activeNavId == R.id.navFavorites) {
        // The mini-player toggles a different Station instance from the visible row.
        // Update the row's model directly so its star reflects the new state immediately.
        (favoritesRecyclerView?.adapter as? StationAdapter)?.updateFavoriteState(station.id, newValue)
    }
    if (currentStation?.id == station.id) {
        miniFavoriteView?.apply {
            setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
            UiMotion.pulse(this, 0.3f)
        }
    }
    if (currentStation?.id == station.id) {
        navNowFavoriteView?.apply {
            setImageResource(if (newValue) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (newValue) R.color.auto_favorite else R.color.auto_text_main))
            UiMotion.pulse(this, 0.3f)
        }
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
    private fun hasValidatedInternet(): Boolean {
        val manager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun resumeOfflinePlaybackIfConnected() {
        if (!playerOffline || !hasValidatedInternet()) return
        val station = currentStation ?: return
        retryHandler.removeCallbacksAndMessages(null)
        currentStreamIndex = 0
        streamRetryCount = 0
        playerOffline = false
        playerReconnecting = true
        showPlayerState("RECONNECTING...", "Internet restored — " + station.name)
        playCurrentStream()
    }

private fun switchToNextStation(reason: String) {
        val player = controller ?: return
        currentStation?.id?.let { failedStationIds.add(it) }

        val nextStation = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = currentStation?.id,
            failedStationIds = failedStationIds
        )

        if (nextStation == null) {
            playerOffline = true
            playerReconnecting = false
            player.pause()
            updatePlayerButton()
            showPlayerState("STREAM UNAVAILABLE", "No working station")
            return
        }

        playerReconnecting = true
        playerOffline = false
        showPlayerState("RECONNECTING...", "Switching station")
        playStation(nextStation, renderPlayerScreen = false)
    }

    private fun togglePlayPause() { val player = controller ?: return; if (player.isPlaying) player.pause() else if (playerOffline) { streamRetryCount = 0; playerOffline = false; playCurrentStream() } else if (player.currentMediaItem == null) playCurrentStream() else player.play(); updatePlayerButton() }

    private fun playAdjacentStation(delta: Int) {
        PlaybackAdjacentStationPolicy.resolve(catalog, currentStation?.id, delta)?.let(::playStation)
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
        val showSidebarNowPlaying = uiProfile.isLandscape && uiProfile.widthDp >= 700 && !uiProfile.isPhoneLandscape
        if (!showSidebarNowPlaying || station == null) {
            binding.navNowPlayingCard.visibility = View.GONE
            navNowLogoView = null; navNowFavoriteView = null; navNowStationView = null; navNowTrackView = null
            return
        }
        binding.navNowPlayingCard.visibility = View.GONE
        navNowLogoView = binding.navNowLogo; navNowFavoriteView = binding.navNowFavorite; navNowStationView = binding.navNowStation; navNowTrackView = binding.navNowTrack
        binding.navNowLogo.tag = station.id; binding.navNowStation.text = station.name; binding.navNowTrack.text = nowPlayingText(station)
        loadStationLogo(station, binding.navNowLogo)
        binding.navNowFavorite.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        binding.navNowFavorite.imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
        updateMarquee(binding.navNowStation); updateMarquee(binding.navNowTrack)
    }

    private fun bindPlayerMetadataChips(container: LinearLayout, station: Station) {
        container.removeAllViews()
        val values = listOf(station.country, station.genre).filter { it.isNotBlank() }
        values.forEach { value ->
            val chip = TextView(this).apply {
                text = value
                textSize = if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 14f else 12f
                typeface = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.NORMAL)
                setTextColor(getColor(R.color.auto_text_main))
                setBackground(android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(9).toFloat()
                    val base = getColor(R.color.auto_accent_soft)
                    setColor(Color.argb(76, Color.red(base), Color.green(base), Color.blue(base)))
                })
                setPadding(dp(if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 12 else 9), 0, dp(if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 12 else 9), 0)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                maxWidth = dp(if (uiProfile.isLandscape) 260 else 120)
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = value
            }
            container.addView(chip, LinearLayout.LayoutParams(-2, dp(if (uiProfile.isCarReference || uiProfile.isTabletLandscape) 30 else 26)).apply {
                marginEnd = dp(8)
            })
        }
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
        playerMetaChips?.let { bindPlayerMetadataChips(it, station) }
        playerTrackView?.apply {
            text = nowPlayingText(station)
            updateMarquee(this)
        }
        playerLogoView?.let {
            it.contentDescription = station.name + " logo"
            loadStationLogo(station, it)
        }
        playerBackdropView?.let { loadStationBackdrop(station, it, trim = true) }
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
        miniLogoView?.let { loadStationBackdrop(station, it) }
        miniFavoriteView?.apply {
            setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
        }
        updateNavNowPlaying(station)
        updateMiniPlayPauseState()
    }

    private fun updateMiniPlayPauseState() {
        val playing = controller?.isPlaying == true
        miniPlayPauseIcon?.apply {
            setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            contentDescription = if (playing) "Pause" else "Play"
        }
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
        activeStationAdapter?.notifyDataSetChanged()
    }
    private fun showPlayerState(title: String, message: String) {
        UiMotion.showPopup(binding.contentContainer, "$title • $message")
    }
    private fun playerControlButton(text: String, iconRes: Int, weight: Float, heightDp: Int, accent: Boolean = false, click: () -> Unit): View = controlTile(iconRes, text, accent, click)
    private fun iconButton(iconRes: Int, description: String, click: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
        setBackgroundResource(R.drawable.bg_icon_button)
        // Icons ~1.4x bigger (24dp -> ~34dp), capped to 70% of the button; button size is unchanged
        scaleType = ImageView.ScaleType.FIT_CENTER
        addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val side = minOf(r - l, b - t)
            val icon = minOf(dp(34), (side * 0.7f).toInt())
            val pad = ((side - icon) / 2).coerceAtLeast(0)
            if (v.paddingLeft != pad || v.paddingTop != pad) v.setPadding(pad, pad, pad, pad)
        }
        contentDescription = description
        UiMotion.pressFeedback(this)
        setOnClickListener { click() }
    }
    private fun FrameLayout.setScreenContent(view: View) {
        miniPlayPauseIcon = null
        miniStationView = null
        miniTrackView = null
        miniLogoView = null
        miniLavaView = null
        miniFavoriteView = null
        removeAllViews()

        if (activeNavId == R.id.navPlayer) {
            updateSidebarMiniPlayer(null)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            UiMotion.enter(view, 4)
            return
        }

        val station = currentStation
            ?: controller?.currentMediaItem?.mediaId?.let { id -> catalog.firstOrNull { it.id == id } }
            ?: catalog.firstOrNull()

        if (station == null) {
            updateSidebarMiniPlayer(null)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            UiMotion.enter(view, 4)
            return
        }

        if (currentStation == null) currentStation = station

        if (uiProfile.isLandscape) {
            updateSidebarMiniPlayer(station)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            UiMotion.enter(view, 4)
        } else {
            val wrapper = LinearLayout(this@CarMainActivity).apply { orientation = LinearLayout.VERTICAL }
            wrapper.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
            wrapper.addView(
                buildMiniPlayer(station),
                LinearLayout.LayoutParams(-1, dp(94)).apply {
                    marginStart = dp(uiProfile.contentPaddingDp)
                    marginEnd = dp(uiProfile.contentPaddingDp)
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
            )
            addView(wrapper, FrameLayout.LayoutParams(-1, -1))
            UiMotion.enter(wrapper, 4)
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
        val card = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_card)
            clipChildren = true
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { showScreen("PLAYER") { renderPlayer() } }
        }

        val ambient = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.20f
            setImageResource(R.drawable.app_logo)
            contentDescription = null
        }
        miniLogoView = ambient
        card.addView(ambient, FrameLayout.LayoutParams(-1, -1))

        val miniLava = LavaLampView(this).apply {
            contentDescription = null
            isClickable = false
            isFocusable = false
            alpha = 0.55f
        }
        miniLavaView = miniLava
        if (!station.logo.isNullOrBlank()) {
            val palette = lastPlayerLavaPalette ?: LavaLampView.defaultPalette().also { lastPlayerLavaPalette = it }
            miniLava.setPaletteColors(palette, animate = false)
        }
        card.addView(miniLava, FrameLayout.LayoutParams(-1, -1))
        loadStationBackdrop(station, ambient)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(50), dp(9))
        }

        val miniStation = marqueeTextView(station.name, 14f, R.color.auto_text_main, true).apply {
            setPadding(dp(6), 0, dp(6), 0)
        }
        val miniTrack = marqueeTextView(nowPlayingText(station), 11f, R.color.auto_text_main).apply {
            setPadding(dp(6), 0, dp(6), 0)
        }
        miniStationView = miniStation
        miniTrackView = miniTrack
        content.addView(miniStation, LinearLayout.LayoutParams(-1, dp(28)))
        content.addView(miniTrack, LinearLayout.LayoutParams(-1, dp(28)))
        card.addView(content, FrameLayout.LayoutParams(-1, -1))

        miniFavoriteView = ImageView(this).apply {
            setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_icon_button)
            background?.mutate()?.alpha = 112
            setPadding(dp(7), dp(7), dp(7), dp(7))
            contentDescription = "Favorite"
            setOnClickListener { currentStation?.let(::toggleFavorite) }
        }
        card.addView(
            miniFavoriteView,
            FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(6)
                rightMargin = dp(6)
            }
        )

        miniPlayPauseIcon = ImageView(this).apply {
            setImageResource(if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
            imageTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
            setBackgroundResource(R.drawable.bg_icon_button)
            contentDescription = if (controller?.isPlaying == true) "Pause" else "Play"
            setOnClickListener { togglePlayPause() }
        }
        card.addView(
            miniPlayPauseIcon,
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = dp(6)
                rightMargin = dp(6)
            }
        )
        updateMiniPlayPauseState()
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
    private fun verticalText(title: String, subtitle: String, titleSize: Float, subtitleSize: Float): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; addView(TextView(this@CarMainActivity).apply { text = title; textSize = titleSize; setTextColor(getColor(R.color.auto_text_main)); setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; setHorizontallyScrolling(true); post { isSelected = true }; includeFontPadding = false }, LinearLayout.LayoutParams(-1, dp((titleSize + 10).toInt()))); addView(TextView(this@CarMainActivity).apply { text = subtitle; textSize = subtitleSize; setTextColor(getColor(R.color.auto_text_muted)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; setHorizontallyScrolling(true); post { isSelected = true }; includeFontPadding = false }, LinearLayout.LayoutParams(-1, dp((subtitleSize + 8).toInt()))) }
    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = uiProfile.contentPaddingDp
        setPadding(dp(pad), dp(16), dp(pad), dp(12))
        setBackgroundColor(getColor(R.color.auto_bg))
    }
    private fun topBar(eyebrow: String, title: String, subtitle: String, icon: Int, right: List<View> = emptyList()): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(14))
        val text = LinearLayout(this@CarMainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(eyebrow), LinearLayout.LayoutParams(-1, dp(22)))
            addView(marqueeTextView(title, 29f, R.color.auto_text_main, true), LinearLayout.LayoutParams(-1, dp(40)))
            addView(marqueeTextView(subtitle, 14f, R.color.auto_text_muted), LinearLayout.LayoutParams(-1, dp(24)))
        }
        addView(text, LinearLayout.LayoutParams(0, dp(94), 1f))
        right.forEach { addView(it, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8) }) }
    }
    private fun marqueeTextView(value: String, size: Float, colorRes: Int, bold: Boolean = false, marquee: Boolean = true): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(getColor(colorRes)); typeface = android.graphics.Typeface.create("sans-serif-medium", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL); gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; maxLines = 1; isSingleLine = true; if (marquee) { setHorizontallyScrolling(true); ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(dp(12)); marqueeRepeatLimit = -1; post { isSelected = true } } }
    private fun updateMarquee(view: TextView?) { view?.apply { setHorizontallyScrolling(true); ellipsize = android.text.TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; post { isSelected = true; requestLayout() } } }
    private fun label(text: String): TextView = TextView(this).apply { this.text = text; textSize = 11f; setTextColor(getColor(R.color.auto_accent)); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD); includeFontPadding = false; letterSpacing = 0.08f }
    private fun controlTile(iconRes: Int, text: String, accent: Boolean = false, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(if (accent) 12 else 20), 0, dp(if (accent) 12 else 20), 0)
        setBackgroundResource(if (accent) R.drawable.bg_giant_play else R.drawable.bg_button_static)
        isClickable = true
        isFocusable = true
        contentDescription = text
        UiMotion.pressFeedback(this)
        setOnClickListener { click() }
        addView(ImageView(this@CarMainActivity).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(if (accent) dp(46) else dp(38), if (accent) dp(46) else dp(38)).apply { marginEnd = dp(if (accent) 12 else 10) })
        addView(TextView(this@CarMainActivity).apply {
            this.text = text
            textSize = if (accent) 20f else 17f
            setTextColor(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-2, -1))
    }
    private fun buildSearchKeypad(input: EditText, adapter: StationAdapter): View { val grid = GridLayout(this).apply { columnCount = 10; rowCount = 4; setBackgroundResource(R.drawable.bg_surface); setPadding(dp(6), dp(6), dp(6), dp(6)) }; "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { letter -> val b = keyButton(letter.toString()) { input.append(letter.toString()); updateSearchResults(input.text.toString(), adapter) }; grid.addView(b, GridLayout.LayoutParams().apply { width = dp(46); height = dp(44); setMargins(dp(2), dp(2), dp(2), dp(2)) }) }; grid.addView(keyButton("SPACE") { input.append(" "); updateSearchResults(input.text.toString(), adapter) }, GridLayout.LayoutParams().apply { width = dp(184); height = dp(44); columnSpec = GridLayout.spec(0, 4) }); grid.addView(keyButton("⌫") { if (input.text.isNotEmpty()) input.text.delete(input.text.length - 1, input.text.length) }, GridLayout.LayoutParams().apply { width = dp(92); height = dp(44); columnSpec = GridLayout.spec(4, 2) }); grid.addView(keyButton("CLEAR") { input.text.clear() }, GridLayout.LayoutParams().apply { width = dp(138); height = dp(44); columnSpec = GridLayout.spec(6, 3) }); return grid }
    private fun loadStationLogo(station: Station, target: ImageView) {
        target.tag = station.id
        target.animate().cancel()
        target.alpha = 1f
        target.imageTintList = null
        target.setImageResource(R.drawable.app_logo)
        val url = station.logo?.trim().orEmpty()
        if (url.isBlank()) {
            return
        }
        ImageLoader.load(url) { bitmap ->
            if (target.tag == station.id) {
                target.imageTintList = null
                target.setImageBitmap(bitmap)
                target.alpha = 1f
            }
        }
    }

    private fun loadStationBackdrop(station: Station, target: ImageView, trim: Boolean = false) {
        target.tag = station.id
        target.animate().cancel()
        target.alpha = if (target === playerBackdropView) 0.025f else 0.20f
        target.imageTintList = null
        target.setImageResource(R.drawable.app_logo)
        val url = station.logo?.trim().orEmpty()
        if (url.isBlank()) {
            lastPlayerLavaPalette = LavaLampView.defaultPalette()
            if (target === playerBackdropView) {
                // Resolve no-artwork stations to the brand palette without clearing the
                // previous colors first; setPaletteColors animates from the current palette.
                playerLavaView?.apply {
                    setPalette(null)
                    fadeTo(0.94f)
                }
            }
            if (target === miniLogoView) miniLavaView?.apply { fadeTo(0.55f); setPalette(null) }
            return
        }
        // Keep the colour wash visible while the logo loads. On a cold start use the
        // brand palette, then animate once to the palette extracted from the logo.
        if (target === playerBackdropView) {
            val previousPalette = lastPlayerLavaPalette
            val palette = previousPalette ?: LavaLampView.defaultPalette().also { lastPlayerLavaPalette = it }
            playerLavaView?.apply {
                setPaletteColors(palette, animate = previousPalette != null)
                fadeTo(0.94f)
            }
        }
        ImageLoader.load(url, onFailure = {
            // The logo could not be loaded: same result as "no logo", use the default logo palette.
            if (target.tag == station.id && !isFinishing && !isDestroyed) {
                lastPlayerLavaPalette = LavaLampView.defaultPalette()
                if (target === playerBackdropView) {
                    playerLavaView?.apply { setPalette(null); fadeTo(0.94f) }
                }
                if (target === miniLogoView) miniLavaView?.apply { fadeTo(0.55f); setPalette(null) }
            }
        }) { bitmap ->
            if (target.tag == station.id) {
                target.setImageBitmap(if (trim) BitmapTrim.trimBorders(bitmap) else bitmap)
                target.alpha = if (target === playerBackdropView) 0.025f else 0.20f
                // Palette extraction samples the bitmap off the UI thread. Keep the old
                // palette visible until the new colors are ready, then animate once to them.
                try {
                    paletteExecutor.execute {
                        val palette = LavaLampView.paletteFromBitmap(bitmap)
                        runOnUiThread {
                            if (target.tag != station.id || (currentStation != null && currentStation?.id != station.id) || (target !== playerBackdropView && target !== miniLogoView) || isFinishing || isDestroyed) return@runOnUiThread
                            lastPlayerLavaPalette = palette
                            if (target === miniLogoView) {
                                miniLavaView?.apply {
                                    setPaletteColors(palette)
                                    fadeTo(0.55f)
                                }
                            }
                            if (target === playerBackdropView) {
                                playerLavaView?.apply {
                                    setPaletteColors(palette)
                                    fadeTo(0.94f)
                                }
                            }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    // The Activity is being destroyed; ignore a late image callback.
                }
            }
        }
    }
    private fun flagFor(code: String): String { if (code.length != 2) return "🌐"; val upper = code.uppercase(java.util.Locale.ROOT); val first = Character.codePointAt(upper, 0); val second = Character.codePointAt(upper, 1); return String(Character.toChars(0x1F1E6 + first - 'A'.code)) + String(Character.toChars(0x1F1E6 + second - 'A'.code)) }
    private val uiProfile: UiProfile get() = UiProfile.from(resources)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onStart() {
        super.onStart()
        val manager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager != null && !networkCallbackRegistered) {
            try {
                manager.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (_: RuntimeException) {
            }
        }
        retryHandler.post { resumeOfflinePlaybackIfConnected() }
    }

    override fun onStop() {
        val manager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager != null && networkCallbackRegistered) {
            try {
                manager.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
            }
            networkCallbackRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() { cancelAutoOpenPlayer(); autoOpenHandler.removeCallbacksAndMessages(null); retryHandler.removeCallbacksAndMessages(null); searchHandler.removeCallbacksAndMessages(null); catalogRepository.close(); cacheExecutor.shutdownNow(); paletteExecutor.shutdownNow(); controller?.removeListener(playerListener); controllerFuture?.let(MediaController::releaseFuture); controller = null; super.onDestroy() }
    private class SimpleTextWatcher(private val onChanged: (CharSequence) -> Unit) : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged(s ?: "") }; override fun afterTextChanged(s: android.text.Editable?) = Unit }
    companion object {
        private const val KEY_STATION_ID = "current_station_id"
        private const val CATALOG_REFRESH_MS = 6L * 60 * 60 * 1000
    }
}
