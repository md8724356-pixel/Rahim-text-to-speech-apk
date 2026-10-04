package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.PlaybackUiState
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

private val SPEED_OPTIONS = listOf(0.8f, 1.0f, 1.2f)

@Composable
fun StatefulWaveformPlayerSection(
    playbackFlow: StateFlow<PlaybackUiState>,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onShareAudio: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackFlow.collectAsStateWithLifecycle()
    if (playbackState.activeFilePath != null) {
        WaveformPlayerCard(
            playbackState = playbackState,
            onTogglePlayPause = onTogglePlayPause,
            onSeekFraction = onSeekFraction,
            onSpeedSelected = onSpeedSelected,
            onShareAudio = onShareAudio,
            modifier = modifier
        )
    }
}

@Composable
fun WaveformPlayerCard(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onShareAudio: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePath = playbackState.activeFilePath ?: return
    val progressFraction = if (playbackState.durationMs > 0L) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val playheadColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("waveform_player_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Audio Active",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playbackState.activeTitle.ifBlank { "Synthesized Audio" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = playbackState.activeSubtitle.ifBlank { "24 kHz Studio WAV" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { onShareAudio(activePath, playbackState.activeTitle) },
                    modifier = Modifier.testTag("share_active_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share WAV Audio",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware-accelerated Waveform Canvas (no infinite animation loop)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekFraction(fraction)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("waveform_scrubber_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    val bars = playbackState.waveformAmplitudes
                    if (bars.isEmpty()) return@Canvas
                    val totalBars = bars.size
                    val spacing = 4.dp.toPx()
                    val availableWidth = size.width - (spacing * (totalBars - 1))
                    val barWidth = (availableWidth / totalBars).coerceAtLeast(3f)

                    for (index in 0 until totalBars) {
                        val rawAmp = bars[index]
                        val barFraction = index.toFloat() / totalBars.toFloat()
                        val isPassed = barFraction <= progressFraction
                        val barHeight = (size.height * rawAmp.coerceIn(0.15f, 1f)).coerceAtLeast(6.dp.toPx())
                        val topY = (size.height - barHeight) / 2f
                        val startX = index * (barWidth + spacing)

                        drawRoundRect(
                            color = if (isPassed) primaryColor else inactiveBarColor,
                            topLeft = Offset(startX, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }

                    val playheadX = (size.width * progressFraction).coerceIn(0f, size.width)
                    drawLine(
                        color = playheadColor,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatDurationMs(playbackState.currentPositionMs)} / ${formatDurationMs(playbackState.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SPEED_OPTIONS.forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(playbackState.playbackSpeed - speed) < 0.05f,
                            onClick = { onSpeedSelected(speed) },
                            label = {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSeekFraction(0f) },
                        modifier = Modifier.testTag("replay_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Replay from start"
                        )
                    }
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_play_pause_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause Audio" else "Play Audio"
                        )
                    }
                }
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
