package com.tkleisas.multituner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.tkleisas.multituner.ui.MainScreen
import com.tkleisas.multituner.ui.MultiTunerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val tunerViewModel = ViewModelProvider(this)[TunerViewModel::class.java]
        val metronomeViewModel = ViewModelProvider(this)[MetronomeViewModel::class.java]

        setContent {
            MultiTunerTheme {
                MainScreen(
                    tunerViewModel = tunerViewModel,
                    metronomeViewModel = metronomeViewModel,
                )
            }
        }
    }
}
