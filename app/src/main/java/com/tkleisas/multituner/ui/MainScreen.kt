package com.tkleisas.multituner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkleisas.multituner.PlaybackViewModel
import com.tkleisas.multituner.TunerViewModel

private enum class MainTab(val label: String, val glyph: String) {
    TUNER("Tuner", "♪"),
    METRONOME("Metronome", "◷"),
    DRUMS("Drums", "▦"),
}

@Composable
fun MainScreen(
    tunerViewModel: TunerViewModel,
    playbackViewModel: PlaybackViewModel,
) {
    val tunerState by tunerViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tab = MainTab.entries[selectedTab]

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                playbackViewModel.onAppBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val content: @Composable () -> Unit = {
        when (tab) {
            MainTab.TUNER -> TunerRoute(
                state = tunerState,
                viewModel = tunerViewModel,
            )

            MainTab.METRONOME -> MetronomeScreen(
                state = playbackState,
                onBpm = playbackViewModel::setBpm,
                onTapTempo = playbackViewModel::tapTempo,
                onBeatsPerBar = playbackViewModel::setBeatsPerBar,
                onSubdivision = playbackViewModel::setSubdivision,
                onVolume = playbackViewModel::setVolume,
                onToggleAccent = playbackViewModel::toggleAccent,
                onToggleMetronome = playbackViewModel::toggleMetronome,
            )

            MainTab.DRUMS -> DrumScreen(
                state = playbackState,
                onBpm = playbackViewModel::setBpm,
                onToggleDrums = playbackViewModel::toggleDrums,
                onCycleStep = playbackViewModel::cycleDrumStep,
                onSelectPreset = playbackViewModel::selectDrumPreset,
                onSwing = playbackViewModel::setSwing,
                onDrumVolume = playbackViewModel::setDrumVolume,
                onToggleMetronome = playbackViewModel::toggleMetronome,
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
            .systemBarsPadding(),
    ) {
        val wide = maxWidth >= 720.dp

        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = PanelColor,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    MainTab.entries.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    text = item.glyph,
                                    fontSize = 22.sp,
                                    color = if (selectedTab == index) InTuneGreen else MutedText,
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = InTuneGreen,
                                selectedTextColor = InTuneGreen,
                                indicatorColor = InTuneGreen.copy(alpha = 0.14f),
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    content()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    content()
                }
                NavigationBar(containerColor = PanelColor) {
                    MainTab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    text = item.glyph,
                                    fontSize = 20.sp,
                                    color = if (selectedTab == index) InTuneGreen else MutedText,
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = InTuneGreen,
                                selectedTextColor = InTuneGreen,
                                indicatorColor = InTuneGreen.copy(alpha = 0.14f),
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText,
                            ),
                        )
                    }
                }
            }
        }
    }
}
