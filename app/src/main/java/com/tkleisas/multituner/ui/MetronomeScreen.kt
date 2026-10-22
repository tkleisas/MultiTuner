package com.tkleisas.multituner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkleisas.multituner.PlaybackUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(
    state: PlaybackUiState,
    onBpm: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onBeatsPerBar: (Int) -> Unit,
    onSubdivision: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onToggleAccent: () -> Unit,
    onToggleMetronome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Header(state)

                Spacer(Modifier.height(16.dp))
                TempoPanel(
                    state = state,
                    onBpm = onBpm,
                    onTapTempo = onTapTempo,
                )

                Spacer(Modifier.height(14.dp))
                BeatPanel(state)

                Spacer(Modifier.height(14.dp))
                SettingsPanel(
                    state = state,
                    onBeatsPerBar = onBeatsPerBar,
                    onSubdivision = onSubdivision,
                    onVolume = onVolume,
                    onToggleAccent = onToggleAccent,
                )

                Spacer(Modifier.height(18.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggleMetronome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.metronomePlaying) ErrorRed else InTuneGreen,
                    contentColor = if (state.metronomePlaying) Color.White else Color(0xFF06210D),
                ),
            ) {
                Text(
                    text = if (state.metronomePlaying) "STOP" else "START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Metronome audio runs in the foreground. It stops when the app is backgrounded.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun Header(state: PlaybackUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MULTITUNER",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Text(
                text = "Metronome",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Shared transport · on-device audio",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PanelColor,
            border = BorderStroke(1.dp, PanelBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "OUTPUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Text(
                    text = if (state.sampleRate > 0) {
                        String.format(Locale.US, "%.0f kHz", state.sampleRate / 1000.0)
                    } else {
                        "idle"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.metronomePlaying || state.drumsPlaying) InTuneGreen else MutedText,
                )
            }
        }
    }
}

@Composable
private fun TempoPanel(
    state: PlaybackUiState,
    onBpm: (Int) -> Unit,
    onTapTempo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TEMPO",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TempoButton(label = "−", onClick = { onBpm(state.bpm - 1) })
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.bpm.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 92.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (state.metronomePlaying || state.drumsPlaying) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
                Spacer(Modifier.weight(1f))
                TempoButton(label = "+", onClick = { onBpm(state.bpm + 1) })
            }

            Spacer(Modifier.height(4.dp))
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { onBpm(it.roundToInt()) },
                valueRange = 30f..300f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "30",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onTapTempo,
                    border = BorderStroke(1.dp, InTuneGreen.copy(alpha = 0.55f)),
                ) {
                    Text(
                        text = "TAP TEMPO",
                        style = MaterialTheme.typography.labelMedium,
                        color = InTuneGreen,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "300",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
        }
    }
}

@Composable
private fun BeatPanel(state: PlaybackUiState) {
    val pulse = remember { Animatable(1.0f) }

    LaunchedEffect(state.currentBeat, state.metronomePlaying) {
        if (state.metronomePlaying) {
            pulse.snapTo(1.28f)
            pulse.animateTo(1.0f, animationSpec = tween(durationMillis = 160))
        } else {
            pulse.snapTo(1.0f)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.metronomePlaying) {
                    "BEAT ${state.currentBeat + 1} / ${state.beatsPerBar}"
                } else {
                    "READY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.metronomePlaying) InTuneGreen else MutedText,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(state.beatsPerBar) { index ->
                    val isCurrent = state.metronomePlaying && index == state.currentBeat
                    val dotSize = if (isCurrent) (17f * pulse.value).dp else 13.dp
                    val dotColor = when {
                        isCurrent -> InTuneGreen
                        index == 0 -> InTuneGreen.copy(alpha = 0.32f)
                        else -> PanelBorder
                    }
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    state: PlaybackUiState,
    onBeatsPerBar: (Int) -> Unit,
    onSubdivision: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onToggleAccent: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "BEATS / BAR",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 6).forEach { beats ->
                    FilterChip(
                        selected = state.beatsPerBar == beats,
                        onClick = { onBeatsPerBar(beats) },
                        label = { Text(beats.toString()) },
                    )
                }
            }

            Text(
                text = "SUBDIVISION",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 4).forEach { subdivision ->
                    FilterChip(
                        selected = state.subdivision == subdivision,
                        onClick = { onSubdivision(subdivision) },
                        label = { Text("${subdivision}×") },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACCENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Text(
                        text = "Louder first beat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(
                    checked = state.accent,
                    onCheckedChange = { onToggleAccent() },
                )
            }

            Text(
                text = "VOLUME · ${(state.volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Slider(
                value = state.volume,
                onValueChange = onVolume,
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun TempoButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
