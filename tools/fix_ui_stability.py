from pathlib import Path
import re

p = Path('android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt')
s = p.read_text()

# Playback events must not rebuild the whole player screen.
s = re.sub(
    r'(        override fun onMediaItemTransition\(mediaItem: MediaItem\?, reason: Int\) \{.*?        addRecentStation\(station\)\n)            renderPlayer\(\)\n        \}',
    r'\1        }', s, count=1, flags=re.S)

# Disable the cross-fade: state changes should not visually blink/jump.
s = re.sub(
    r'    private fun FrameLayout\.setScreenContent\(view: View\) \{.*?    \}\n    private fun actionButton',
    '''    private fun FrameLayout.setScreenContent(view: View) {
        removeAllViews()
        addView(view, FrameLayout.LayoutParams(-1, -1))
    }
    private fun actionButton''', s, count=1, flags=re.S)

# Favorite toggles only its icon; do not rebuild Now Playing.
if 'private var playerFavoriteButton: ImageView? = null' not in s:
    s = s.replace('private var playPauseLabel: TextView? = null', 'private var playPauseLabel: TextView? = null\n    private var playerFavoriteButton: ImageView? = null')

s = s.replace(
    'playerLogoView = null; playerTrackView = null; playerArtistView = null; playerStatusView = null; playPauseIcon = null; playPauseLabel = null',
    'playerLogoView = null; playerTrackView = null; playerArtistView = null; playerStatusView = null; playPauseIcon = null; playPauseLabel = null; playerFavoriteButton = null')

s = s.replace(
    'val root = screenRoot(); val favorite = iconButton(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline, "Favorite") { station.favorite = !station.favorite; if (station.favorite) favoriteIds.add(station.id) else favoriteIds.remove(station.id); persistFavorites(); renderPlayer() }; val details',
    'val root = screenRoot(); val favorite = iconButton(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline, "Favorite") { station.favorite = !station.favorite; if (station.favorite) favoriteIds.add(station.id) else favoriteIds.remove(station.id); persistFavorites(); playerFavoriteButton?.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline) }; playerFavoriteButton = favorite; val details')

# Countries is a category browser; station search remains on Search.
countries = re.compile(r'    private fun renderCountries\(filter: String = ""\) \{.*?    private fun renderGenres\(\)', re.S)
countries_replacement = '''    private fun renderCountries() {
        val all = if (remoteCountries.isNotEmpty()) remoteCountries.sortedBy { it.name } else catalog.groupBy { it.countryCode }.map { CountryItem(it.value.first().country, it.key, flagFor(it.key), it.value.size) }.sortedBy { it.name }
        val root = screenRoot()
        root.addView(topBar("BROWSE", "Countries", "${all.size} countries with available radio", R.drawable.ic_globe))
        val columns = if (uiProfile.isLandscape) 3 else 2
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, columns)
            adapter = CountryAdapter(all) { country -> loadAndRenderStations(country.name, country = country.code, onBack = { renderCountries() }) }
            setPadding(0, 0, 0, dp(8))
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        binding.contentContainer.setScreenContent(root)
    }
    private fun renderGenres()'''
s, n = countries.subn(countries_replacement, s, count=1)
if n != 1:
    raise SystemExit('Countries renderer patch failed')

p.write_text(s)
