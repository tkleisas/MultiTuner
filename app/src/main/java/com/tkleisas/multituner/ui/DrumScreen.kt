package com.tkleisas.multituner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkleisas.multituner.PlaybackUiState
import com.tkleisas.multituner.model.DrumPattern
import com.tkleisas.multituner.model.DrumPatterns
import java.util.Locale
import kotlin.math.roundToInt

private val drumTrackLabels = listOf(
    "Kick", "Snare", "Clap", "Tom 1", "CH", "OH", "Tom 2", "Tamb",
)

@Composable
fun DrumScreen(
    state: PlaybackUiState,
    onBpm: (Int) -> Unit,
    onToggleDrums: () -> Unit,
    onCycleStep: (Int, Int) -> Unit,
    onTrackLength: (Int, Int) -> Unit,
    onIndependentLengths: (Boolean) -> Unit,
    onCommonLength: (Int) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSwing: (Float) -> Unit,
    onDrumVolume: (Float) -> Unit,
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
                    .widthIn(max = 760.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Header(state)
                Spacer(Modifier.height(14.dp))
                TempoPanel(state, onBpm)
                Spacer(Modifier.height(12.dp))
                PatternPanel(
                    state = state,
                    onSelectPreset = onSelectPreset,
                    onCycleStep = onCycleStep,
                    onTrackLength = onTrackLength,
                    onIndependentLengths = onIndependentLengths,
                    onCommonLength = onCommonLength,
                )
                Spacer(Modifier.height(12.dp))
                SettingsPanel(
                    state = state,
                    onSwing = onSwing,
                    onDrumVolume = onDrumVolume,
                    onToggleMetronome = onToggleMetronome,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggleDrums,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.drumsPlaying) ErrorRed else InTuneGreen,
                    contentColor = if (state.drumsPlaying) Color.White else Color(0xFF06210D),
                ),
            ) {
                Text(
                    text = if (state.drumsPlaying) "STOP" else "START",
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
                text = "Drums and click share one transport and can run together.",
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
                text = "Drums",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Shared transport · synthesized kit",
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
                    color = if (state.drumsPlaying || state.metronomePlaying) {
                        InTuneGreen
                    } else {
                        MutedText
                    },
                )
            }
        }
    }
}

@Composable
private fun TempoPanel(
    state: PlaybackUiState,
    onBpm: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TEMPO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                MiniRoundButton("−") { onBpm(state.bpm - 1) }
                Text(
                    text = state.bpm.toString(),
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (state.drumsPlaying || state.metronomePlaying) {
                        InTuneGreen
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                MiniRoundButton("+") { onBpm(state.bpm + 1) }
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { onBpm(it.roundToInt()) },
                valueRange = 30f..300f,
            )
        }
    }
}

@Composable
private fun PatternPanel(
    state: PlaybackUiState,
    onSelectPreset: (String) -> Unit,
    onCycleStep: (Int, Int) -> Unit,
    onTrackLength: (Int, Int) -> Unit,
    onIndependentLengths: (Boolean) -> Unit,
    onCommonLength: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PATTERN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                if (state.drumPresetId == "custom") {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CUSTOM",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarnYellow,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.drumsPlaying) {
                    val commonLength = state.drumPattern.effectiveLengths()
                        .distinct()
                        .singleOrNull()
                    Text(
                        text = if (commonLength != null) {
                            "STEP ${state.currentDrumStep % commonLength + 1}/$commonLength"
                        } else {
                            "POLYRHYTHM · per-lane steps"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InTuneGreen,
                    )
                } else {
                    Text(
                        text = if (state.drumPattern.independentLengths) {
                            "tap steps · set lane length 1–32"
                        } else {
                            "tap steps · common length 1–32"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DrumPatterns.all.forEach { preset ->
                    FilterChip(
                        selected = state.drumPresetId == preset.id,
                        onClick = { onSelectPreset(preset.id) },
                        label = { Text(preset.name) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INDEPENDENT LANE LENGTHS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.drumPattern.independentLengths) {
                            "Each lane loops at its own length"
                        } else {
                            "All lanes share one length"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
                Switch(
                    checked = state.drumPattern.independentLengths,
                    onCheckedChange = onIndependentLengths,
                )
            }
            if (!state.drumPattern.independentLengths) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COMMON LENGTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Spacer(Modifier.weight(1f))
                    TrackLengthControl(
                        length = state.drumPattern.commonLength,
                        onChange = onCommonLength,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            StepGrid(state, onCycleStep, onTrackLength)
        }
    }
}

@Composable
private fun StepGrid(
    state: PlaybackUiState,
    onCycleStep: (Int, Int) -> Unit,
    onTrackLength: (Int, Int) -> Unit,
) {
    val cellSize = 22.dp
    val spacing = 3.dp
    val headerHeight = 17.dp
    val independent = state.drumPattern.independentLengths
    val labelWidth = if (independent) 108.dp else 78.dp
    val horizontalScroll = rememberScrollState()

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.width(labelWidth)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight + spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TRACK",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                if (independent) {
                    Text(
                        text = "LEN",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MutedText,
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            drumTrackLabels.forEachIndexed { track, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellSize + spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MutedText,
                        maxLines = 1,
                    )
                    if (independent) {
                        TrackLengthControl(
                            length = state.drumPattern.length(track),
                            onChange = { length -> onTrackLength(track, length) },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(DrumPattern.MAX_STEPS) { step ->
                    Box(
                        modifier = Modifier
                            .width(cellSize)
                            .height(headerHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (step + 1).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MutedText,
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing))

            repeat(DrumPattern.TRACKS) { track ->
                val trackLength = state.drumPattern.length(track)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    repeat(DrumPattern.MAX_STEPS) { step ->
                        val active = step < trackLength
                        val level = state.drumPattern.level(track, step)
                        val current = state.drumsPlaying && active &&
                            trackLength > 0 &&
                            state.currentDrumStep % trackLength == step
                        val color = when {
                            !active -> PanelBorder.copy(alpha = 0.10f)
                            level == 2 -> WarnYellow.copy(alpha = 0.9f)
                            level == 1 -> InTuneGreen.copy(alpha = 0.55f)
                            else -> PanelBorder.copy(alpha = 0.35f)
                        }
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color)
                                .then(
                                    if (current) {
                                        Modifier.border(
                                            1.5.dp,
                                            InTuneGreen,
                                            RoundedCornerShape(5.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(enabled = active) {
                                    onCycleStep(track, step)
                                },
                        )
                    }
                }
                Spacer(Modifier.height(spacing))
            }
        }
    }
}

@Composable
private fun TrackLengthControl(
    length: Int,
    onChange: (Int) -> Unit,
) {
    val canDecrease = length > 1
    val canIncrease = length < DrumPattern.MAX_STEPS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "−",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = if (canDecrease) InTuneGreen else MutedText.copy(alpha = 0.35f),
            modifier = Modifier
                .clickable(enabled = canDecrease) { onChange(length - 1) }
                .padding(horizontal = 3.dp),
        )
        Text(
            text = length.toString(),
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = if (canIncrease) InTuneGreen else MutedText.copy(alpha = 0.35f),
            modifier = Modifier
                .clickable(enabled = canIncrease) { onChange(length + 1) }
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun SettingsPanel(
    state: PlaybackUiState,
    onSwing: (Float) -> Unit,
    onDrumVolume: (Float) -> Unit,
    onToggleMetronome: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SWING · ${(state.swing * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Slider(
                value = state.swing,
                onValueChange = onSwing,
                valueRange = 0f..0.6f,
            )
            Text(
                text = "DRUM VOLUME · ${(state.drumVolume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Slider(
                value = state.drumVolume,
                onValueChange = onDrumVolume,
                valueRange = 0f..1f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CLICK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Text(
                        text = "Metronome click with the drums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(
                    checked = state.metronomePlaying,
                    onCheckedChange = { onToggleMetronome() },
                )
            }
        }
    }
}

@Composable
private fun MiniRoundButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
