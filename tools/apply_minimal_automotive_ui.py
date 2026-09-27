from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt"


def replace_method(text: str, name: str, replacement: str) -> str:
    marker = f"private fun {name}"
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"method not found: {name}")
    brace = text.find("{", start)
    if brace < 0:
        raise SystemExit(f"method body not found: {name}")
    depth = 0
    in_string = False
    escaped = False
    i = brace
    while i < len(text):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement + text[i + 1:]
        i += 1
    raise SystemExit(f"unterminated method: {name}")


text = MAIN.read_text(encoding="utf-8")

render_player = '''private fun renderPlayer() {
        playerLogoView = null
        playerTrackView = null
        playerArtistView = null
        playerStatusView = null
        playPauseIcon = null
        playPauseLabel = null

        val station = currentStation ?: catalog.firstOrNull()
        val root = screenRoot()

        if (station == null) {
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(marqueeTextView("RADIO WORLD", 20f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(0, dp(42), 1f))
                addView(label("READY"), LinearLayout.LayoutParams(-2, dp(30)))
            }
            root.addView(header)
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            empty.addView(marqueeTextView("Choose a station", 30f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(-1, dp(44)))
            empty.addView(TextView(this).apply {
                text = "Browse the catalog or search for a station"
                textSize = 15f
                setTextColor(getColor(R.color.auto_text_muted))
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(-1, dp(32)))
            root.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
            binding.contentContainer.setScreenContent(root)
            return
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(marqueeTextView("RADIO WORLD", 18f, R.color.auto_text_main, true, false), LinearLayout.LayoutParams(-1, dp(28)))
            addView(label("NOW PLAYING"), LinearLayout.LayoutParams(-1, dp(22)))
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(52), 1f))
        val favorite = iconButton(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline, "Favorite") {
            station.favorite = !station.favorite
            if (station.favorite) favoriteIds.add(station.id) else favoriteIds.remove(station.id)
            persistFavorites()
            renderPlayer()
        }
        header.addView(favorite, LinearLayout.LayoutParams(dp(58), dp(58)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(66)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        content.addView(logo, LinearLayout.LayoutParams(dp(220), dp(220)).apply { marginEnd = dp(42) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info.addView(marqueeTextView(station.name, 34f, R.color.auto_text_main, true), LinearLayout.LayoutParams(-1, dp(48)))
        val meta = listOf(station.country, station.genre).filter { it.isNotBlank() }.joinToString("  •  ")
        info.addView(marqueeTextView(meta, 14f, R.color.auto_text_muted), LinearLayout.LayoutParams(-1, dp(30)))
        info.addView(TextView(this).apply {
            text = ""
            setBackgroundColor(getColor(R.color.auto_border))
        }, LinearLayout.LayoutParams(dp(64), dp(2)).apply { topMargin = dp(18); bottomMargin = dp(20) })
        val trackTitle = marqueeTextView(nowPlayingText(station), 29f, R.color.auto_text_main, true)
        playerTrackView = trackTitle
        playerArtistView = null
        info.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(46)))
        val status = TextView(this).apply {
            textSize = 11f
            includeFontPadding = false
            setPadding(0, dp(16), 0, 0)
        }
        playerStatusView = status
        info.addView(status, LinearLayout.LayoutParams(-1, dp(34)))
        content.addView(info, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        val play = controlTile(if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play, if (controller?.isPlaying == true) "PAUSE" else "PLAY", true) { togglePlayPause() }
        playPauseIcon = (play as LinearLayout).getChildAt(0) as ImageView
        playPauseLabel = (play as LinearLayout).getChildAt(1) as TextView
        controls.addView(play, LinearLayout.LayoutParams(dp(210), dp(82)).apply { marginEnd = dp(12) })
        controls.addView(controlTile(R.drawable.ic_shuffle, "SHUFFLE") { catalog.randomOrNull()?.let { playStation(it) } }, LinearLayout.LayoutParams(dp(150), dp(82)))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(92)))

        updatePlayerButton()
        binding.contentContainer.setScreenContent(root)
    }'''

screen_root = '''private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(30), dp(20), dp(30), dp(12))
        setBackgroundColor(getColor(R.color.auto_bg))
    }'''

top_bar = '''private fun topBar(eyebrow: String, title: String, subtitle: String, icon: Int, right: List<View> = emptyList()): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(18))
        val text = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(eyebrow), LinearLayout.LayoutParams(-1, dp(22)))
            addView(marqueeTextView(title, 30f, R.color.auto_text_main, true), LinearLayout.LayoutParams(-1, dp(40)))
            addView(marqueeTextView(subtitle, 13f, R.color.auto_text_muted), LinearLayout.LayoutParams(-1, dp(24)))
        }
        addView(text, LinearLayout.LayoutParams(0, dp(86), 1f))
        right.forEach { addView(it, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8) }) }
    }'''

set_active_nav = '''private fun setActiveNav(activeId: Int) {
        intArrayOf(R.id.navPlayer, R.id.navCountries, R.id.navGenres, R.id.navFavorites, R.id.navRecents, R.id.navSearch).forEach { id ->
            findViewById<Button>(id).apply {
                val active = id == activeId
                setTextColor(getColor(if (active) R.color.auto_accent else R.color.auto_text_muted))
                setBackgroundResource(if (active) R.drawable.bg_nav_item_active else R.drawable.bg_nav_item)
                compoundDrawableTintList = ColorStateList.valueOf(getColor(if (active) R.color.auto_accent else R.color.auto_text_muted))
                alpha = if (active) 1f else 0.78f
            }
        }
    }'''

control_tile = '''private fun controlTile(iconRes: Int, text: String, accent: Boolean = false, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        setBackgroundResource(if (accent) R.drawable.bg_giant_play else R.drawable.bg_button_static)
        isClickable = true
        isFocusable = true
        contentDescription = text
        setOnClickListener { click() }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        addView(TextView(this@MainActivity).apply {
            this.text = text
            textSize = 11f
            setTextColor(getColor(if (accent) R.color.auto_bg else R.color.auto_text_main))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-2, -1))
    }'''

action_button = '''private fun actionButton(text: String, iconRes: Int? = null, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 14f
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
    }'''

icon_button = '''private fun iconButton(iconRes: Int, description: String, click: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(getColor(R.color.auto_text_main))
        setBackgroundResource(R.drawable.bg_icon_button)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        setOnClickListener { click() }
    }'''

for name, replacement in [
    ("renderPlayer", render_player),
    ("screenRoot", screen_root),
    ("topBar", top_bar),
    ("setActiveNav", set_active_nav),
    ("controlTile", control_tile),
    ("actionButton", action_button),
    ("iconButton", icon_button),
]:
    text = replace_method(text, name, replacement)

MAIN.write_text(text, encoding="utf-8")
print("minimal automotive UI applied")
