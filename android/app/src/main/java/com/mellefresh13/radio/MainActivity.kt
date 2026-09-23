package com.mellefresh13.radio

import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mellefresh13.radio.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionToken = SessionToken(
            this,
            ComponentName(this, RadioPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
            },
            MoreExecutors.directExecutor()
        )

        binding.playButton.isEnabled = false

        binding.countriesButton.setOnClickListener {
            binding.stationMeta.text = "Countries screen — next phase"
        }

        binding.genresButton.setOnClickListener {
            binding.stationMeta.text = "Genres screen — next phase"
        }

        binding.favoritesButton.setOnClickListener {
            binding.stationMeta.text = "Favorites screen — next phase"
        }

        binding.searchButton.setOnClickListener {
            binding.stationMeta.text = "Search screen — next phase"
        }
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        super.onDestroy()
    }

    private fun playStation(name: String, url: String) {
        val player = controller ?: return

        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(name)
                .setUri(url)
                .build()
        )
        player.prepare()
        player.play()

        binding.stationName.text = name
        binding.stationMeta.text = "Playing"
        binding.playButton.text = "PAUSE"
    }
}
