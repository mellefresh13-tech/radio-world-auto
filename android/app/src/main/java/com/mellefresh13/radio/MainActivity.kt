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
        val target = if (UiProfile.from(resources).isCarReference) {
            CarMainActivity::class.java
        } else {
            MobileMainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
