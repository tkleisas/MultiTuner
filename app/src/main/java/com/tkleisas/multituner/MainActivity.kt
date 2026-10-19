package com.tkleisas.multituner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkleisas.multituner.ui.MultiTunerTheme
import com.tkleisas.multituner.ui.TunerRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val viewModel = ViewModelProvider(this)[TunerViewModel::class.java]

        setContent {
            MultiTunerTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle()
                TunerRoute(
                    state = state.value,
                    viewModel = viewModel,
                )
            }
        }
    }
}
