package com.tkleisas.multituner.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tkleisas.multituner.PlaybackUiState
import com.tkleisas.multituner.audio.OggOpusEncoder
import com.tkleisas.multituner.model.Cassette
import com.tkleisas.multituner.model.CassetteColors
import com.tkleisas.multituner.model.CassetteExportFormat
import com.tkleisas.multituner.model.CassetteLibrary
import com.tkleisas.multituner.model.CassetteRecordSource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun CassetteScreen(
    state: PlaybackUiState,
    onSelectCassette: (String) -> Unit,
    onCreateCassette: () -> Unit,
    onDeleteCassette: (String) -> Unit,
    onColor: (Long) -> Unit,
    onArmTrack: (Int) -> Unit,
    onToggleDeckMute: (Int) -> Unit,
    onToggleDeckSolo: (Int) -> Unit,
    onDeckGain: (Int, Float) -> Unit,
    onDeckPan: (Int, Float) -> Unit,
    onDeckReverbSend: (Int, Float) -> Unit,
    onDeckDelaySend: (Int, Float) -> Unit,
    onDeckBusSend: (Int, Float) -> Unit,
    onDeckSource: (Int, CassetteRecordSource) -> Unit,
    onVarispeed: (Float) -> Unit,
    onRecordLevel: (Float) -> Unit,
    onWindStart: (Int) -> Unit,
    onWindStop: () -> Unit,
    onToStart: () -> Unit,
    onToEnd: () -> Unit,
    onFlip: () -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onStopAll: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleMetronome: () -> Unit,
    onToggleDrums: () -> Unit,
    onTapeEnabled: (Boolean) -> Unit,
    onTapeDrive: (Float) -> Unit,
    onTapeTone: (Float) -> Unit,
    onTapeWow: (Float) -> Unit,
    onTapeFlutter: (Float) -> Unit,
    onTapeHiss: (Float) -> Unit,
    onTapeMix: (Float) -> Unit,
    onReverbReturn: (Float) -> Unit,
    onReverbSize: (Float) -> Unit,
    onReverbDamping: (Float) -> Unit,
    onDelayReturn: (Float) -> Unit,
    onDelayTime: (Float) -> Unit,
    onDelayFeedback: (Float) -> Unit,
    onDelayTone: (Float) -> Unit,
    onExport: (CassetteExportFormat) -> Unit,
    onExportConsumed: () -> Unit,
    onClearExportError: () -> Unit,
) {
    val context = LocalContext.current
    var showLibrary by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(CassetteExportFormat.WAV) }
    val oggAvailable = remember { OggOpusEncoder.isAvailable() }
    val hasTakes = state.cassette.cassette.audioTracks.any { it.filePath != null }

    LaunchedEffect(state.cassette.exportUri) {
        val uri = state.cassette.exportUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = state.cassette.exportMime ?: "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Cassette mix", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, "Share cassette mix"),
            )
        }
        onExportConsumed()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleRecord()
    }
    val requiresMic = state.cassette.armedTracks.any { deckIndex ->
        state.cassette.cassette.decks[deckIndex].recordSource ==
            CassetteRecordSource.MIC
    }
    val recordAction = {
        if (!requiresMic || state.cassette.recording ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onToggleRecord()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
                    .widthIn(max = 820.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Header(state, onOpenLibrary = { showLibrary = true })
                Spacer(Modifier.height(14.dp))
                CassetteShell(
                    state = state,
                    onOpenLibrary = { showLibrary = true },
                    onOpenExport = { showExport = true },
                )
                Spacer(Modifier.height(10.dp))
                ColorPalette(state, onColor)
                Spacer(Modifier.height(14.dp))
                Transport(
                    state = state,
                    onWindStart = onWindStart,
                    onWindStop = onWindStop,
                    onToStart = onToStart,
                    onToEnd = onToEnd,
                    onPlay = onTogglePlay,
                    onStop = onStop,
                    onRecord = recordAction,
                    onFlip = onFlip,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CLICK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Switch(
                        checked = state.metronomePlaying,
                        onCheckedChange = { onToggleMetronome() },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Metronome click as a recording guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "DRUMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Switch(
                        checked = state.drumsPlaying,
                        onCheckedChange = { onToggleDrums() },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Drum machine as a recording source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
                Spacer(Modifier.height(10.dp))
                StopAllButton(
                    enabled = state.metronomePlaying || state.drumsPlaying ||
                        state.cassette.playing || state.cassette.recording,
                    onClick = onStopAll,
                )
                Spacer(Modifier.height(14.dp))
                RecordLevelPanel(state, onRecordLevel)
                Spacer(Modifier.height(14.dp))
                DeckStrips(
                    state = state,
                    onArmTrack = onArmTrack,
                    onToggleMute = onToggleDeckMute,
                    onToggleSolo = onToggleDeckSolo,
                    onGain = onDeckGain,
                    onPan = onDeckPan,
                    onReverbSend = onDeckReverbSend,
                    onDelaySend = onDeckDelaySend,
                    onBusSend = onDeckBusSend,
                    onSource = onDeckSource,
                )
                Spacer(Modifier.height(14.dp))
                FxPanel(
                    state = state,
                    onReverbReturn = onReverbReturn,
                    onReverbSize = onReverbSize,
                    onReverbDamping = onReverbDamping,
                    onDelayReturn = onDelayReturn,
                    onDelayTime = onDelayTime,
                    onDelayFeedback = onDelayFeedback,
                    onDelayTone = onDelayTone,
                )
                Spacer(Modifier.height(14.dp))
                VarispeedPanel(state, onVarispeed)
                Spacer(Modifier.height(14.dp))
                TapePanel(
                    state = state,
                    onEnabled = onTapeEnabled,
                    onDrive = onTapeDrive,
                    onTone = onTapeTone,
                    onWow = onTapeWow,
                    onFlutter = onTapeFlutter,
                    onHiss = onTapeHiss,
                    onMix = onTapeMix,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showLibrary) {
        CassetteLibraryDialog(
            library = state.cassette.library,
            onSelect = {
                onSelectCassette(it)
                showLibrary = false
            },
            onCreate = onCreateCassette,
            onDelete = onDeleteCassette,
            onDismiss = { showLibrary = false },
        )
    }

    if (showExport) {
        ExportDialog(
            format = exportFormat,
            onFormat = { exportFormat = it },
            hasTakes = hasTakes,
            oggAvailable = oggAvailable,
            onExport = {
                showExport = false
                onExport(exportFormat)
            },
            onDismiss = { showExport = false },
        )
    }

    if (state.cassette.exporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting mix") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.cassette.exportProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (exportFormat == CassetteExportFormat.OGG) {
                            "Rendering the cassette and encoding OGG Opus…"
                        } else {
                            "Rendering the cassette mix…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
        )
    }

    val exportError = state.cassette.exportError
    if (exportError != null) {
        AlertDialog(
            onDismissRequest = onClearExportError,
            title = { Text("Export failed") },
            text = { Text(exportError) },
            confirmButton = {
                TextButton(onClick = onClearExportError) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun ExportDialog(
    format: CassetteExportFormat,
    onFormat: (CassetteExportFormat) -> Unit,
    hasTakes: Boolean,
    oggAvailable: Boolean,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export cassette mix") },
        text = {
            Column {
                Text(
                    text = "Forward 1× stereo mix with deck levels, pan, " +
                        "sends, master FX and tape. Click and drums are not included. " +
                        "The file ends at the last take.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ExportFormatRow(
                    format = CassetteExportFormat.WAV,
                    selected = format == CassetteExportFormat.WAV,
                    enabled = true,
                    onSelect = onFormat,
                )
                ExportFormatRow(
                    format = CassetteExportFormat.OGG,
                    selected = format == CassetteExportFormat.OGG,
                    enabled = oggAvailable,
                    onSelect = onFormat,
                )
                if (!oggAvailable) {
                    Text(
                        text = "OGG Opus requires Android 11 and a device Opus encoder.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
                if (!hasTakes) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Record at least one take before exporting.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarnYellow,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = hasTakes, onClick = onExport) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ExportFormatRow(
    format: CassetteExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (CassetteExportFormat) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect(format) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { onSelect(format) },
            enabled = enabled,
        )
        Text(
            text = format.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MutedText
            },
        )
    }
}

@Composable
private fun Header(
    state: PlaybackUiState,
    onOpenLibrary: () -> Unit,
) {
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
                text = "Cassette",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "4-track · ${Cassette.TAPE_SECONDS / 60} minute tape",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
        Surface(
            onClick = onOpenLibrary,
            shape = RoundedCornerShape(12.dp),
            color = PanelColor,
            border = BorderStroke(1.dp, PanelBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "CASSETTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Text(
                    text = state.cassette.cassette.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = InTuneGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CassetteShell(
    state: PlaybackUiState,
    onOpenLibrary: () -> Unit,
    onOpenExport: () -> Unit,
) {
    val cassette = state.cassette.cassette
    val sampleRate = if (state.sampleRate > 0) state.sampleRate.toDouble() else 48_000.0
    val sourceSeconds = (state.cassette.positionSamples / sampleRate) *
        state.cassette.varispeed
    val tapeSeconds = sourceSeconds.coerceIn(0.0, CassettePlayerTapeSeconds)
    val angle = (sourceSeconds * 70.0) % 360.0
    val shellColor = Color(cassette.color)
    val tapeFraction = (tapeSeconds / CassettePlayerTapeSeconds).coerceIn(0.0, 1.0)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = shellColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f),
                        ),
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.17f),
                    topLeft = Offset(7.dp.toPx(), 7.dp.toPx()),
                    size = Size(
                        (w - 14.dp.toPx()).coerceAtLeast(1f),
                        (h - 14.dp.toPx()).coerceAtLeast(1f),
                    ),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
                for (x in listOf(10.dp.toPx(), w - 10.dp.toPx())) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.20f),
                        radius = 3.5.dp.toPx(),
                        center = Offset(x, h * 0.62f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = 2.dp.toPx(),
                        center = Offset(x - 0.5.dp.toPx(), h * 0.62f - 0.5.dp.toPx()),
                        style = Stroke(0.7.dp.toPx()),
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cassette.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF0B0F0C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (state.cassette.flipped) "SIDE B" else "SIDE A",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0B0F0C).copy(alpha = 0.65f),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0B0F0C).copy(alpha = 0.18f),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (state.cassette.flipped) "B" else "A",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B0F0C),
                                )
                            }
                        }
                    }
                    TextButton(onClick = onOpenExport) {
                        Text(
                            text = "EXPORT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0B0F0C),
                        )
                    }
                    TextButton(onClick = onOpenLibrary) {
                        Text(
                            text = "LIBRARY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0B0F0C),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(158.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B0F0C)),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val reelRadius = min(w * 0.135f, h * 0.30f)
                        val centerY = h * 0.43f
                        val centers = listOf(w * 0.28f, w * 0.72f)
                        val hubRadius = reelRadius * 0.30f
                        val fullRadius = reelRadius
                        val leftAmount = 1.0 - tapeFraction
                        val rightAmount = tapeFraction
                        val leftTapeRadius = sqrt(
                            (hubRadius * hubRadius +
                                leftAmount.toFloat() *
                                (fullRadius * fullRadius - hubRadius * hubRadius))
                                .toDouble(),
                        ).toFloat()
                        val rightTapeRadius = sqrt(
                            (hubRadius * hubRadius +
                                rightAmount.toFloat() *
                                (fullRadius * fullRadius - hubRadius * hubRadius))
                                .toDouble(),
                        ).toFloat()

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF121A15),
                                    Color(0xFF060907),
                                ),
                            ),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.05f),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(
                                (w - 2.dp.toPx()).coerceAtLeast(1f),
                                (h - 2.dp.toPx()).coerceAtLeast(1f),
                            ),
                            cornerRadius = CornerRadius(15.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )

                        val tapePathY = centerY + reelRadius * 0.93f
                        drawLine(
                            color = Color(0xFF342117),
                            start = Offset(centers[0], tapePathY),
                            end = Offset(centers[1], tapePathY),
                            strokeWidth = 2.5.dp.toPx(),
                        )

                        centers.forEachIndexed { index, centerX ->
                            val tapeRadius =
                                if (index == 0) leftTapeRadius else rightTapeRadius
                            val center = Offset(centerX, centerY)
                            drawCircle(
                                color = Color(0xFF2A1A12),
                                radius = tapeRadius,
                                center = center,
                            )
                            drawCircle(
                                color = Color(0xFF3A2419),
                                radius = (tapeRadius * 0.82f).coerceAtLeast(1f),
                                center = center,
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.035f),
                                radius = tapeRadius * 0.95f,
                                center = center,
                                style = Stroke(1.dp.toPx()),
                            )
                            drawCircle(
                                color = Color(0xFFE6F2E9),
                                radius = maxOf(hubRadius * 1.38f, 2f),
                                center = center,
                            )
                            drawCircle(
                                color = Color(0xFFC7D7CC),
                                radius = hubRadius * 1.02f,
                                center = center,
                            )
                            val reelAngle = angle * (reelRadius / tapeRadius.coerceAtLeast(1f))
                            rotate(
                                degrees = reelAngle.toFloat(),
                                pivot = center,
                            ) {
                                repeat(6) { spoke ->
                                    val a = spoke * Math.PI / 3.0
                                    drawLine(
                                        color = Color(0xFF0B0F0C),
                                        start = center,
                                        end = Offset(
                                            centerX + cos(a).toFloat() * hubRadius * 0.92f,
                                            centerY + sin(a).toFloat() * hubRadius * 0.92f,
                                        ),
                                        strokeWidth = 1.8.dp.toPx(),
                                    )
                                }
                                drawCircle(
                                    color = Color(0xFF0B0F0C),
                                    radius = hubRadius * 0.20f,
                                    center = center,
                                )
                            }
                        }

                        val headY = h * 0.79f
                        drawRoundRect(
                            color = Color(0xFF202C25),
                            topLeft = Offset(w * 0.41f, headY),
                            size = Size(w * 0.18f, h * 0.09f),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color(0xFF3D4B43),
                            topLeft = Offset(w * 0.46f, headY + 1.dp.toPx()),
                            size = Size(w * 0.08f, h * 0.055f),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                        for (x in listOf(w * 0.36f, w * 0.64f)) {
                            drawCircle(
                                color = Color(0xFF4A5A50),
                                radius = 3.2.dp.toPx(),
                                center = Offset(x, headY + h * 0.045f),
                            )
                            drawCircle(
                                color = Color(0xFF111713),
                                radius = 1.4.dp.toPx(),
                                center = Offset(x, headY + h * 0.045f),
                            )
                        }

                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(w * 0.06f, h * 0.07f),
                            end = Offset(w * 0.94f, h * 0.07f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0B0F0C).copy(alpha = 0.28f),
                    ) {
                        Text(
                            text = formatTapeTime(tapeSeconds) + " / " +
                                formatTapeTime(CassettePlayerTapeSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE6F2E9),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            state.cassette.flipping -> "FLIPPING"
                            state.cassette.tapePreparing -> "PREPARING"
                            state.cassette.recording -> "RECORDING"
                            state.cassette.winding -> "WINDING"
                            else -> "TAPE COUNTER"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            state.cassette.flipping -> WarnYellow
                            state.cassette.tapePreparing -> WarnYellow
                            state.cassette.recording -> ErrorRed
                            state.cassette.winding -> InTuneGreen
                            else -> Color(0xFF0B0F0C).copy(alpha = 0.62f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPalette(
    state: PlaybackUiState,
    onColor: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SHELL",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
        )
        Spacer(Modifier.width(10.dp))
        CassetteColors.palette.forEach { color ->
            val selected = state.cassette.cassette.color == color
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color.White, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onColor(color) },
            )
        }
    }
}

@Composable
private fun Transport(
    state: PlaybackUiState,
    onWindStart: (Int) -> Unit,
    onWindStop: () -> Unit,
    onToStart: () -> Unit,
    onToEnd: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onFlip: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransportButton(
                label = "|<",
                active = false,
                color = MutedText,
                onClick = onToStart,
                enabled = !state.cassette.recording &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                height = 44.dp,
                modifier = Modifier.weight(1f),
            )
            HoldButton(
                label = "<<",
                onStart = { onWindStart(-1) },
                onStop = onWindStop,
                enabled = !state.cassette.recording &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
            HoldButton(
                label = ">>",
                onStart = { onWindStart(1) },
                onStop = onWindStop,
                enabled = !state.cassette.recording &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                label = ">|",
                active = false,
                color = MutedText,
                onClick = onToEnd,
                enabled = !state.cassette.recording &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                height = 44.dp,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransportButton(
                label = "REC",
                active = state.cassette.recording,
                color = ErrorRed,
                onClick = onRecord,
                enabled = (!state.cassette.flipped || state.cassette.recording) &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                label = "PLAY",
                active = state.cassette.playing,
                color = InTuneGreen,
                onClick = onPlay,
                enabled = !state.cassette.playing &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                label = "STOP",
                active = state.cassette.playing || state.cassette.recording,
                color = ErrorRed,
                onClick = onStop,
                enabled = (state.cassette.playing || state.cassette.recording) &&
                    !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                label = "FLIP",
                active = state.cassette.flipped,
                color = WarnYellow,
                onClick = onFlip,
                enabled = !state.cassette.recording &&
                    !state.cassette.tapePreparing && !state.cassette.flipping,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                state.cassette.flipping -> "Flipping the tape…"
                state.cassette.tapePreparing ->
                    "Preparing four zeroed " +
                    formatTapeTime(CassettePlayerTapeSeconds) + " tracks…"
                state.cassette.flipped ->
                    "Side B is playback-only. Flip back to record."
                state.cassette.recording ->
                    "REC punches out; STOP ends tape playback."
                state.cassette.winding -> "Winding tape…"
                else ->
                    "Hold << / >> to wind; |< / >| jump to tape start/end."
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                state.cassette.flipping -> WarnYellow
                state.cassette.tapePreparing -> WarnYellow
                state.cassette.flipped -> WarnYellow.copy(alpha = 0.8f)
                state.cassette.recording -> ErrorRed.copy(alpha = 0.85f)
                state.cassette.winding -> InTuneGreen
                else -> MutedText
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HoldButton(
    label: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)

    DisposableEffect(Unit) {
        onDispose { currentStop() }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (pressed) {
            InTuneGreen.copy(alpha = 0.20f)
        } else {
            PanelColor
        },
        border = BorderStroke(1.dp, if (pressed) InTuneGreen else PanelBorder),
        modifier = modifier
            .focusProperties { canFocus = false }
            .height(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        currentStart()
                        tryAwaitRelease()
                        pressed = false
                        currentStop()
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (enabled) {
                    if (pressed) InTuneGreen else MaterialTheme.colorScheme.onSurface
                } else {
                    MutedText.copy(alpha = 0.5f)
                },
            )
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (active) color.copy(alpha = 0.20f) else PanelColor,
        border = BorderStroke(1.dp, if (active) color else PanelBorder),
        modifier = modifier
            .focusProperties { canFocus = false }
            .height(height),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    if (active) color else MaterialTheme.colorScheme.onSurface
                } else {
                    MutedText.copy(alpha = 0.5f)
                },
            )
        }
    }
}

@Composable
private fun DeckStrips(
    state: PlaybackUiState,
    onArmTrack: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onGain: (Int, Float) -> Unit,
    onPan: (Int, Float) -> Unit,
    onReverbSend: (Int, Float) -> Unit,
    onDelaySend: (Int, Float) -> Unit,
    onBusSend: (Int, Float) -> Unit,
    onSource: (Int, CassetteRecordSource) -> Unit,
) {
    val cassette = state.cassette.cassette
    val order = if (state.cassette.flipped) (3 downTo 0).toList() else (0..3).toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { deckIndex ->
            val deck = cassette.decks[deckIndex]
            val track = cassette.audioTracks[deckIndex]
            val armed = deckIndex in state.cassette.armedTracks
            val recording = deckIndex in state.cassette.recordingTracks

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PanelColor,
                border = BorderStroke(
                    if (armed || recording) 2.dp else 1.dp,
                    when {
                        recording -> ErrorRed
                        armed -> InTuneGreen
                        else -> PanelBorder
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${deckIndex + 1}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 24.sp,
                            ),
                            color = if (recording) ErrorRed else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (track.filePath != null) "TAKE" else "EMPTY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (track.filePath != null) InTuneGreen else MutedText,
                            )
                        }
                        if (!state.cassette.flipped) {
                            Chip(
                                text = if (armed) "ARMED" else "ARM",
                                active = armed,
                                onClick = { onArmTrack(deckIndex) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Chip(
                            text = "M",
                            active = deck.muted,
                            onClick = { onToggleMute(deckIndex) },
                            activeColor = WarnYellow,
                        )
                        Spacer(Modifier.width(4.dp))
                        Chip(
                            text = "S",
                            active = deck.soloed,
                            onClick = { onToggleSolo(deckIndex) },
                            activeColor = InTuneGreen,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VOL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                        )
                        Slider(
                            value = deck.gain,
                            onValueChange = { onGain(deckIndex, it) },
                            valueRange = 0f..1.5f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(deck.gain * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                        )
                        Slider(
                            value = deck.pan,
                            onValueChange = { onPan(deckIndex, it) },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = panLabel(deck.pan),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                    SendRow(
                        label = "REV",
                        value = deck.reverbSend,
                        onValue = { onReverbSend(deckIndex, it) },
                    )
                    SendRow(
                        label = "DLY",
                        value = deck.delaySend,
                        onValue = { onDelaySend(deckIndex, it) },
                    )
                    SendRow(
                        label = "BUS",
                        value = deck.busSend,
                        onValue = { onBusSend(deckIndex, it) },
                    )
                    if (!state.cassette.flipped && (armed || recording)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "SRC",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                            )
                            CassetteRecordSource.entries.forEach { source ->
                                Chip(
                                    text = source.label,
                                    active = deck.recordSource == source,
                                    onClick = { onSource(deckIndex, source) },
                                    enabled = !recording,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendRow(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun Chip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: Color = InTuneGreen,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (active) activeColor else PanelBorder),
        modifier = Modifier.focusProperties { canFocus = false },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StopAllButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) ErrorRed.copy(alpha = 0.12f) else PanelColor,
        border = BorderStroke(1.dp, if (enabled) ErrorRed else PanelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STOP ALL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) ErrorRed else MutedText.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Tape · click · drums",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun RecordLevelPanel(
    state: PlaybackUiState,
    onRecordLevel: (Float) -> Unit,
) {
    val level = state.cassette.recordLevel
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "REC LEVEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(level * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = InTuneGreen,
                )
            }
            Slider(
                value = level,
                onValueChange = onRecordLevel,
                valueRange = 0f..2f,
            )
            Spacer(Modifier.height(2.dp))
            LevelMeter(
                level = state.cassette.inputLevel.toDouble(),
                label = "REC IN",
                activeColor = if (state.cassette.inputClipping) ErrorRed else InTuneGreen,
                clip = state.cassette.inputClipping,
            )
            Spacer(Modifier.height(4.dp))
            LevelMeter(
                level = state.cassette.outputLevel.toDouble(),
                label = "TAPE OUT",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "MIC gain · armed tracks record together from their SRC",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun FxPanel(
    state: PlaybackUiState,
    onReverbReturn: (Float) -> Unit,
    onReverbSize: (Float) -> Unit,
    onReverbDamping: (Float) -> Unit,
    onDelayReturn: (Float) -> Unit,
    onDelayTime: (Float) -> Unit,
    onDelayFeedback: (Float) -> Unit,
    onDelayTone: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MASTER FX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "send / return",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
            FxSlider(
                label = "REVERB",
                value = state.cassette.reverbReturn,
                onValue = onReverbReturn,
                display = "${(state.cassette.reverbReturn * 100).roundToInt()}%",
            )
            if (state.cassette.reverbReturn > 0f) {
                FxSlider(
                    label = "SIZE",
                    value = state.cassette.reverbSize,
                    onValue = onReverbSize,
                    display = "${(state.cassette.reverbSize * 100).roundToInt()}%",
                )
                FxSlider(
                    label = "DAMP",
                    value = state.cassette.reverbDamping,
                    onValue = onReverbDamping,
                    display = "${(state.cassette.reverbDamping * 100).roundToInt()}%",
                )
            }
            Spacer(Modifier.height(4.dp))
            FxSlider(
                label = "DELAY",
                value = state.cassette.delayReturn,
                onValue = onDelayReturn,
                display = "${(state.cassette.delayReturn * 100).roundToInt()}%",
            )
            if (state.cassette.delayReturn > 0f) {
                FxSlider(
                    label = "TIME",
                    value = state.cassette.delayTimeMs,
                    onValue = onDelayTime,
                    valueRange = 40f..1200f,
                    display = "${state.cassette.delayTimeMs.roundToInt()} ms",
                )
                FxSlider(
                    label = "FDBK",
                    value = state.cassette.delayFeedback,
                    onValue = onDelayFeedback,
                    valueRange = 0f..0.9f,
                    display = "${(state.cassette.delayFeedback * 100).roundToInt()}%",
                )
                FxSlider(
                    label = "TONE",
                    value = state.cassette.delayTone,
                    onValue = onDelayTone,
                    display = "${(state.cassette.delayTone * 100).roundToInt()}%",
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Per-deck REV/DLY sends feed these master returns.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun FxSlider(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    display: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = display,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun VarispeedPanel(
    state: PlaybackUiState,
    onVarispeed: (Float) -> Unit,
) {
    val semitones = 12.0 * log2(state.cassette.varispeed.toDouble())
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VARISPEED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2f×  (%+.1f st)",
                        state.cassette.varispeed,
                        semitones,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = InTuneGreen,
                )
            }
            Slider(
                value = state.cassette.varispeed,
                onValueChange = onVarispeed,
                valueRange = 0.5f..2f,
                enabled = !state.cassette.recording,
            )
            Text(
                text = "Playback only. Recording runs at 1×.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun TapePanel(
    state: PlaybackUiState,
    onEnabled: (Boolean) -> Unit,
    onDrive: (Float) -> Unit,
    onTone: (Float) -> Unit,
    onWow: (Float) -> Unit,
    onFlutter: (Float) -> Unit,
    onHiss: (Float) -> Unit,
    onMix: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TAPE EMULATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Text(
                        text = "Saturation · wow/flutter · hiss · tone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(
                    checked = state.cassette.tapeEnabled,
                    onCheckedChange = onEnabled,
                )
            }
            if (state.cassette.tapeEnabled) {
                TapeSlider("Drive", state.cassette.tapeDrive, onDrive)
                TapeSlider("Tone", state.cassette.tapeTone, onTone)
                TapeSlider("Wow", state.cassette.tapeWow, onWow)
                TapeSlider("Flutter", state.cassette.tapeFlutter, onFlutter)
                TapeSlider("Hiss", state.cassette.tapeHiss, onHiss)
                TapeSlider("Mix", state.cassette.tapeMix, onMix)
            }
        }
    }
}

@Composable
private fun TapeSlider(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun CassetteLibraryDialog(
    library: CassetteLibrary,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cassettes") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(library.cassettes, key = { it.id }) { cassette ->
                        val selected = cassette.id == library.selectedId
                        Surface(
                            onClick = { onSelect(cassette.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) InTuneGreen.copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(cassette.color)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cassette.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${cassette.audioTracks.count { it.filePath != null }} takes · ${Cassette.TAPE_SECONDS / 60} min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedText,
                                    )
                                }
                                if (library.cassettes.size > 1) {
                                    TextButton(onClick = { onDelete(cassette.id) }) {
                                        Text(
                                            text = "Delete",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErrorRed,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreate) {
                    Text(
                        text = "New cassette",
                        style = MaterialTheme.typography.labelMedium,
                        color = InTuneGreen,
                    )
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

private val CassettePlayerTapeSeconds = Cassette.TAPE_SECONDS.toDouble()

private fun formatTapeTime(seconds: Double): String {
    val clamped = seconds.coerceIn(0.0, CassettePlayerTapeSeconds)
    val whole = clamped.toInt()
    return String.format(Locale.US, "%02d:%02d", whole / 60, whole % 60)
}

private fun panLabel(pan: Float): String = when {
    abs(pan) < 0.02f -> "C"
    pan < 0f -> "L${(-pan * 100).roundToInt()}"
    else -> "R${(pan * 100).roundToInt()}"
}
