package com.tkleisas.multituner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tkleisas.multituner.BuildConfig
import com.tkleisas.multituner.TunerUiState
import com.tkleisas.multituner.audio.ReferenceTonePlayer
import com.tkleisas.multituner.TunerViewModel
import com.tkleisas.multituner.audio.AudioStatus
import com.tkleisas.multituner.model.Course
import com.tkleisas.multituner.model.InstrumentFamily
import com.tkleisas.multituner.model.Tuning
import com.tkleisas.multituner.model.TuningLibrary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TunerRoute(
    state: TunerUiState,
    viewModel: TunerViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.onPermissionGranted()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    hasPermission = granted
                    if (granted) {
                        viewModel.onPermissionGranted()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                    viewModel.onAppForeground()
                }

                Lifecycle.Event.ON_STOP -> viewModel.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (granted) {
                viewModel.onPermissionGranted()
            } else {
                viewModel.onPermissionDenied()
            }
            viewModel.onAppForeground()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppBackground()
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (!hasPermission) {
        PermissionScreen(
            denied = permissionDenied,
            onRequest = {
                if (permissionDenied) {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    } else {
        TunerScreen(
            state = state,
            onSelectTuning = viewModel::selectTuning,
            onToggleCourse = viewModel::toggleCourseLock,
            onClearLock = viewModel::clearCourseLock,
            onSetReference = viewModel::setReferencePitch,
            onTogglePause = viewModel::togglePause,
        )
    }
}

@Composable
private fun PermissionScreen(
    denied: Boolean,
    onRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
            .systemBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(PanelColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♪",
                    fontSize = 48.sp,
                    color = InTuneGreen,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "MultiTuner",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (denied) {
                    "Microphone access was denied. Enable it in Android settings to start tuning."
                } else {
                    "MultiTuner listens to your instrument and shows the pitch, cents and target string in real time."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Audio is processed on-device. Nothing is recorded or uploaded.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Spacer(Modifier.height(26.dp))
            Button(onClick = onRequest) {
                Text(if (denied) "Open app settings" else "Enable microphone")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunerScreen(
    state: TunerUiState,
    onSelectTuning: (Tuning) -> Unit,
    onToggleCourse: (Int) -> Unit,
    onClearLock: () -> Unit,
    onSetReference: (Double) -> Unit,
    onTogglePause: () -> Unit,
) {
    var showTuningPicker by remember { mutableStateOf(false) }
    var showReferencePicker by remember { mutableStateOf(false) }
    val tonePlayer = remember { ReferenceTonePlayer() }

    DisposableEffect(Unit) {
        onDispose { tonePlayer.stop() }
    }

    val toneCourse = state.lockedCourseIndex?.let { state.tuning.courses.getOrNull(it) }
        ?: state.activeCourse
        ?: state.tuning.courses.firstOrNull()
    val toneTarget = toneCourse?.strings?.firstOrNull()
    val toneFrequency = toneTarget?.targetFrequency(state.referenceA4) ?: 0.0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
            .systemBarsPadding(),
    ) {
        val isWide = maxWidth >= 720.dp
        val centered = isWide && maxHeight >= 560.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (centered) Modifier else Modifier.verticalScroll(scrollState))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
        ) {
            Header(
                state = state,
                isWide = isWide,
                onOpenTuning = { showTuningPicker = true },
                onOpenReference = { showReferencePicker = true },
                onTogglePause = onTogglePause,
            )

            Spacer(Modifier.height(if (isWide) 18.dp else 10.dp))

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1.18f)) {
                        NoteDisplay(state = state, isWide = true)
                        Spacer(Modifier.height(8.dp))
                        NeedleGauge(cents = state.cents, hasPitch = state.hasPitch)
                        Spacer(Modifier.height(10.dp))
                        StrobeBand(
                            cents = state.cents,
                            hasPitch = state.hasPitch,
                            inTune = state.inTune,
                        )
                        Spacer(Modifier.height(12.dp))
                        LevelMeter(level = state.level)
                        Spacer(Modifier.height(16.dp))
                        StatusLine(
                            state = state,
                            onTestTone = if (BuildConfig.DEBUG) {
                                { tonePlayer.play(toneFrequency) }
                            } else {
                                null
                            },
                        )
                    }

                    Column(modifier = Modifier.weight(0.82f)) {
                        CoursePanel(
                            state = state,
                            onToggleCourse = onToggleCourse,
                            onClearLock = onClearLock,
                        )
                        Spacer(Modifier.height(18.dp))
                        TuningInfo(state = state)
                    }
                }
            } else {
                NoteDisplay(state = state, isWide = false)
                Spacer(Modifier.height(4.dp))
                NeedleGauge(cents = state.cents, hasPitch = state.hasPitch)
                Spacer(Modifier.height(8.dp))
                StrobeBand(
                    cents = state.cents,
                    hasPitch = state.hasPitch,
                    inTune = state.inTune,
                )
                Spacer(Modifier.height(10.dp))
                LevelMeter(level = state.level)
                Spacer(Modifier.height(18.dp))
                CoursePanel(
                    state = state,
                    onToggleCourse = onToggleCourse,
                    onClearLock = onClearLock,
                )
                Spacer(Modifier.height(14.dp))
                StatusLine(
                    state = state,
                    onTestTone = if (BuildConfig.DEBUG) {
                        { tonePlayer.play(toneFrequency) }
                    } else {
                        null
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTuningPicker) {
        TuningPickerDialog(
            current = state.tuning,
            onSelect = {
                onSelectTuning(it)
                showTuningPicker = false
            },
            onDismiss = { showTuningPicker = false },
        )
    }

    if (showReferencePicker) {
        ReferencePitchDialog(
            value = state.referenceA4,
            onSet = onSetReference,
            onDismiss = { showReferencePicker = false },
        )
    }
}

@Composable
private fun Header(
    state: TunerUiState,
    isWide: Boolean,
    onOpenTuning: () -> Unit,
    onOpenReference: () -> Unit,
    onTogglePause: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MULTITUNER",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                onClick = onOpenTuning,
                color = Color.Transparent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.tuning.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = if (isWide) 24.sp else 20.sp,
                            lineHeight = if (isWide) 28.sp else 23.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = if (isWide) 24.sp else 20.sp,
                        ),
                        color = InTuneGreen,
                    )
                }
            }
            Text(
                text = state.tuning.family.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }

        SmallPanel(
            label = "A4",
            value = formatReference(state.referenceA4),
            onClick = onOpenReference,
        )
        Spacer(Modifier.width(8.dp))
        SmallPanel(
            label = "AUDIO",
            value = if (state.isPaused) "Resume" else "Pause",
            onClick = onTogglePause,
            accent = state.isPaused,
        )
    }
}

@Composable
private fun SmallPanel(
    label: String,
    value: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, if (accent) InTuneGreen else PanelBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (accent) InTuneGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NoteDisplay(
    state: TunerUiState,
    isWide: Boolean,
) {
    val color = when {
        !state.hasPitch -> MutedText
        abs(state.cents) <= 5.0 -> InTuneGreen
        abs(state.cents) <= 15.0 -> WarnYellow
        else -> ErrorRed
    }
    val noteSize = if (isWide) 118.sp else 84.sp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.displayNote,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = noteSize),
                color = color,
                maxLines = 1,
            )
            if (state.inTune) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = InTuneGreen.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, InTuneGreen.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = "IN TUNE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = InTuneGreen,
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        if (state.hasPitch) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatFrequency(state.frequency),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatCents(state.cents),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
                Text(
                    text = "${(state.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
        } else {
            Text(
                text = when {
                    state.isPaused -> "Listening is paused"
                    state.audioStatus is AudioStatus.Error -> "Microphone unavailable"
                    else -> "Play a single string"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoursePanel(
    state: TunerUiState,
    onToggleCourse: (Int) -> Unit,
    onClearLock: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STRINGS",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.lockedCourseIndex == null) {
                    "Auto-detect"
                } else {
                    "Locked · tap to unlock"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.lockedCourseIndex == null) MutedText else InTuneGreen,
            )
        }

        Spacer(Modifier.height(9.dp))

        if (state.tuning.courseCount > 6) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AutoChip(
                        selected = state.lockedCourseIndex == null,
                        onClick = onClearLock,
                    )
                }
                itemsIndexed(state.tuning.courses) { index, course ->
                    CourseChip(
                        course = course,
                        preferFlats = state.tuning.preferFlats,
                        isActive = state.activeCourseIndex == index,
                        isLocked = state.lockedCourseIndex == index,
                        inTune = state.inTune && state.activeCourseIndex == index,
                        activeMidi = state.displayMidi.takeIf { state.activeCourseIndex == index },
                        cents = state.cents.takeIf { state.activeCourseIndex == index },
                        onClick = { onToggleCourse(index) },
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AutoChip(
                    selected = state.lockedCourseIndex == null,
                    onClick = onClearLock,
                )
                state.tuning.courses.forEachIndexed { index, course ->
                    CourseChip(
                        course = course,
                        preferFlats = state.tuning.preferFlats,
                        isActive = state.activeCourseIndex == index,
                        isLocked = state.lockedCourseIndex == index,
                        inTune = state.inTune && state.activeCourseIndex == index,
                        activeMidi = state.displayMidi.takeIf { state.activeCourseIndex == index },
                        cents = state.cents.takeIf { state.activeCourseIndex == index },
                        onClick = { onToggleCourse(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoChip(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) InTuneGreen.copy(alpha = 0.12f) else PanelColor,
        border = BorderStroke(1.dp, if (selected) InTuneGreen.copy(alpha = 0.65f) else PanelBorder),
        modifier = Modifier.height(76.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "AUTO",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) InTuneGreen else MutedText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "nearest",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) InTuneGreen else MutedText,
            )
        }
    }
}

@Composable
private fun CourseChip(
    course: Course,
    preferFlats: Boolean,
    isActive: Boolean,
    isLocked: Boolean,
    inTune: Boolean,
    activeMidi: Int?,
    cents: Double?,
    onClick: () -> Unit,
) {
    val borderColor = when {
        inTune -> InTuneGreen
        isActive || isLocked -> InTuneGreen.copy(alpha = 0.65f)
        else -> PanelBorder
    }
    val background = when {
        inTune -> InTuneGreen.copy(alpha = 0.14f)
        isLocked -> InTuneGreen.copy(alpha = 0.10f)
        isActive -> InTuneGreen.copy(alpha = 0.07f)
        else -> PanelColor
    }
    val borderWidth = if (isActive || isLocked) 2.dp else 1.dp
    val uniqueStrings = course.strings.distinctBy { it.midi }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = background,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .widthIn(min = 74.dp)
            .height(76.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    maxLines = 1,
                )
                if (isLocked) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "●",
                        fontSize = 7.sp,
                        color = InTuneGreen,
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                uniqueStrings.forEach { string ->
                    val highlighted = isActive && string.midi == activeMidi
                    Text(
                        text = string.displayLabel(preferFlats),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = if (highlighted) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            if (isActive && cents != null) {
                Text(
                    text = formatCents(cents),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        abs(cents) <= 5.0 -> InTuneGreen
                        abs(cents) <= 15.0 -> WarnYellow
                        else -> ErrorRed
                    },
                )
            } else {
                Text(
                    text = " ",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(
    state: TunerUiState,
    onTestTone: (() -> Unit)?,
) {
    val text = when (val status = state.audioStatus) {
        AudioStatus.Idle -> "Ready"
        AudioStatus.Starting -> "Starting microphone…"
        AudioStatus.Stopped -> if (state.isPaused) "Paused" else "Audio stopped"
        is AudioStatus.Running -> {
            val rate = if (status.sampleRate % 1000 == 0) {
                "${status.sampleRate / 1000} kHz"
            } else {
                String.format(Locale.US, "%.1f kHz", status.sampleRate / 1000.0)
            }
            "$rate · ${status.source}"
        }

        is AudioStatus.Error -> status.message
    }
    val color = if (state.audioStatus is AudioStatus.Error) ErrorRed else MutedText

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        state.audioStatus is AudioStatus.Error -> ErrorRed
                        state.isRunning -> InTuneGreen
                        else -> MutedText.copy(alpha = 0.5f)
                    },
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (onTestTone != null) {
            TextButton(onClick = onTestTone) {
                Text(
                    text = "TONE",
                    style = MaterialTheme.typography.labelSmall,
                    color = InTuneGreen,
                )
            }
        }
        Text(
            text = "A4 ${formatReference(state.referenceA4)}",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
        )
    }
}

@Composable
private fun TuningInfo(state: TunerUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = state.tuning.name,
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.tuning.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.tuning.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.tuning.courseCount} courses · reference A4 ${formatReference(state.referenceA4)}",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuningPickerDialog(
    current: Tuning,
    onSelect: (Tuning) -> Unit,
    onDismiss: () -> Unit,
) {
    var family by remember(current.id) { mutableStateOf(current.family) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose tuning") },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InstrumentFamily.entries.forEach { item ->
                        FilterChip(
                            selected = family == item,
                            onClick = { family = item },
                            label = { Text(item.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(TuningLibrary.forFamily(family), key = { it.id }) { tuning ->
                        val selected = tuning.id == current.id
                        Surface(
                            onClick = { onSelect(tuning) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tuning.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${tuning.courseCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedText,
                                    )
                                }
                                if (tuning.description.isNotBlank()) {
                                    Text(
                                        text = tuning.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedText,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ReferencePitchDialog(
    value: Double,
    onSet: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var slider by remember { mutableFloatStateOf(value.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reference pitch") },
        text = {
            Column {
                Text(
                    text = String.format(Locale.US, "A4 = %.1f Hz", slider),
                    style = MaterialTheme.typography.headlineMedium,
                    color = InTuneGreen,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Adjust the tuning reference. 440 Hz is standard.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Slider(
                    value = slider,
                    onValueChange = { raw ->
                        val rounded = (raw * 10f).roundToInt() / 10f
                        slider = rounded
                        onSet(rounded.toDouble())
                    },
                    valueRange = 415f..466f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(415.0, 432.0, 440.0, 442.0, 444.0).forEach { preset ->
                        TextButton(
                            onClick = {
                                slider = preset.toFloat()
                                onSet(preset)
                            },
                        ) {
                            Text("${preset.toInt()}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private fun formatFrequency(frequency: Double): String =
    String.format(Locale.US, "%.1f Hz", frequency)

private fun formatCents(cents: Double): String =
    String.format(Locale.US, "%+.1f ¢", cents)

private fun formatReference(reference: Double): String =
    String.format(Locale.US, "%.1f Hz", reference)
