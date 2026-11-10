package com.mellefresh13.radio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point that keeps the automotive HMI completely isolated from
 * the responsive phone/tablet implementation.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        val profile = UiProfile.from(resources)
        AppAnalytics.appOpened(this, profile)
        val target = if (profile.isCarReference) {
            CarMainActivity::class.java
        } else {
            MobileMainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
