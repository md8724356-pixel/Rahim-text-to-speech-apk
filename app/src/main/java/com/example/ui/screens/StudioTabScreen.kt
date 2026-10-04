package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioPlayerController
import com.example.audio.PlaybackUiState
import com.example.data.model.PresetScript
import com.example.data.model.TtsDefaults
import com.example.data.model.TtsEngineMode
import com.example.data.model.TtsLanguageOption
import com.example.data.model.TtsModelOption
import com.example.data.model.TtsVoice
import com.example.ui.TtsStudioUiState
import com.example.ui.components.StatefulWaveformPlayerSection
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StudioTabScreen(
    uiState: TtsStudioUiState,
    transcriptFlow: StateFlow<String>,
    playbackFlow: StateFlow<PlaybackUiState>,
    onTranscriptChange: (String) -> Unit,
    onClearTranscript: () -> Unit,
    onPresetSelected: (PresetScript) -> Unit,
    onVoiceSelected: (TtsVoice) -> Unit,
    onModelSelected: (TtsModelOption) -> Unit,
    onLanguageSelected: (TtsLanguageOption) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onEngineModeSelected: (TtsEngineMode) -> Unit,
    onSynthesizeClicked: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onShareAudio: (String, String) -> Unit,
    onOpenVoicesTab: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onQuickSaveApiKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_tab_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hardware-Accelerated Studio Hero Header (zero bitmap decode lag)
        item(key = "hero_header", contentType = "hero") {
            StudioHeroHeader(
                isApiKeyConfigured = uiState.isApiKeyConfigured,
                maskedKeyLabel = uiState.maskedKeyLabel,
                selectedVoice = uiState.selectedVoice,
                selectedModel = uiState.selectedModel,
                onOpenApiKeyDialog = onOpenApiKeyDialog
            )
        }

        // 2. API Key Secrets Notice Banner
        if (!uiState.isApiKeyConfigured) {
            item(key = "secrets_notice", contentType = "notice") {
                SecretsConfigurationNoticeCard(
                    onOpenApiKeyDialog = onOpenApiKeyDialog,
                    onQuickSaveApiKey = onQuickSaveApiKey
                )
            }
        }

        // 3. Status or Error Feedback Banner
        if (uiState.errorBannerMessage != null || uiState.statusBannerMessage != null) {
            item(key = "status_banner", contentType = "status") {
                val isError = uiState.errorBannerMessage != null
                val message = uiState.errorBannerMessage ?: uiState.statusBannerMessage.orEmpty()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_feedback_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = "Status",
                            tint = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Isolated Stateful Waveform Player Section (only recomposes itself on timer ticks)
        item(key = "waveform_player", contentType = "player") {
            StatefulWaveformPlayerSection(
                playbackFlow = playbackFlow,
                onTogglePlayPause = onTogglePlayPause,
                onSeekFraction = onSeekFraction,
                onSpeedSelected = onSpeedChanged,
                onShareAudio = onShareAudio
            )
        }

        // 5. One-Tap Preset Templates (Bangla & English)
        item(key = "preset_scripts", contentType = "presets") {
            PresetScriptsSection(onPresetSelected = onPresetSelected)
        }

        // 6. Isolated Script Editor Card (typing only recomposes this card!)
        item(key = "script_editor", contentType = "editor") {
            IsolatedScriptEditorCard(
                transcriptFlow = transcriptFlow,
                selectedLanguage = uiState.selectedLanguage,
                isSynthesizing = uiState.isSynthesizing,
                onTranscriptChange = onTranscriptChange,
                onClearTranscript = onClearTranscript,
                onLanguageSelected = onLanguageSelected,
                onSynthesizeClicked = onSynthesizeClicked
            )
        }

        // 7. Acoustic Controls & Voice Configuration Card
        item(key = "acoustic_controls", contentType = "controls") {
            AcousticConfigurationCard(
                uiState = uiState,
                onVoiceSelected = onVoiceSelected,
                onModelSelected = onModelSelected,
                onSpeedChanged = onSpeedChanged,
                onEngineModeSelected = onEngineModeSelected,
                onOpenVoicesTab = onOpenVoicesTab
            )
        }
    }
}

@Composable
private fun PresetScriptsSection(
    onPresetSelected: (PresetScript) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "রেডিমেড স্ক্রিপ্ট টেমপ্লেট (Quick Scripts)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "এক ট্যাপে টেস্ট করুন",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("preset_scripts_row")
        ) {
            items(
                items = TtsDefaults.presetScripts,
                key = { it.id },
                contentType = { "preset_chip" }
            ) { preset ->
                AssistChip(
                    onClick = { onPresetSelected(preset) },
                    label = {
                        Text(
                            text = preset.titleBangla,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.testTag("preset_chip_${preset.id}")
                )
            }
        }
    }
}

@Composable
private fun IsolatedScriptEditorCard(
    transcriptFlow: StateFlow<String>,
    selectedLanguage: TtsLanguageOption,
    isSynthesizing: Boolean,
    onTranscriptChange: (String) -> Unit,
    onClearTranscript: () -> Unit,
    onLanguageSelected: (TtsLanguageOption) -> Unit,
    onSynthesizeClicked: () -> Unit
) {
    val transcript by transcriptFlow.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    val textMetrics by remember(transcript) {
        derivedStateOf {
            Pair(AudioPlayerController.countWordsFast(transcript), transcript.length)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.input_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                onTranscriptChange(clipText)
                            }
                        },
                        modifier = Modifier.testTag("paste_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.btn_paste_text),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (transcript.isNotEmpty()) {
                        IconButton(
                            onClick = onClearTranscript,
                            modifier = Modifier.testTag("clear_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.btn_clear_text),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Language Picker LazyRow
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = TtsDefaults.languages,
                    key = { it.code },
                    contentType = { "lang_chip" }
                ) { lang ->
                    FilterChip(
                        selected = selectedLanguage.code == lang.code,
                        onClick = { onLanguageSelected(lang) },
                        label = {
                            Text("${lang.flagEmoji} ${lang.labelBangla}")
                        },
                        modifier = Modifier.testTag("lang_chip_${lang.code}")
                    )
                }
            }

            OutlinedTextField(
                value = transcript,
                onValueChange = onTranscriptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("tts_transcript_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${textMetrics.first} শব্দ (words) • ${textMetrics.second} অক্ষর (chars)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "WAV • 24 kHz • Low Latency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Primary Synthesize Speech CTA Button
            Button(
                onClick = onSynthesizeClicked,
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_speech_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.btn_generating),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_generate_speech),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioHeroHeader(
    isApiKeyConfigured: Boolean,
    maskedKeyLabel: String,
    selectedVoice: TtsVoice,
    selectedModel: TtsModelOption,
    onOpenApiKeyDialog: () -> Unit
) {
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0B132B),
                Color(0xFF1A2855),
                Color(0xFF0E3B52)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(gradientBrush)
            .drawBehind {
                // Lightweight GPU decorative soundwave bars on the right side
                val barCount = 14
                val barW = 5.dp.toPx()
                val gap = 6.dp.toPx()
                val startX = size.width - (barCount * (barW + gap)) - 18.dp.toPx()
                val heights = floatArrayOf(0.25f, 0.45f, 0.7f, 0.5f, 0.85f, 0.6f, 0.95f, 0.55f, 0.75f, 0.4f, 0.65f, 0.35f, 0.5f, 0.25f)
                for (i in 0 until barCount) {
                    val h = size.height * heights[i] * 0.65f
                    val y = (size.height - h) / 2f
                    drawRoundRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.14f),
                        topLeft = Offset(startX + i * (barW + gap), y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenApiKeyDialog() }
                        .border(
                            width = 1.dp,
                            color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isApiKeyConfigured) "API Key: $maskedKeyLabel" else "API Key সেট করুন",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = selectedModel.id.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text(
                    text = stringResource(R.string.studio_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ভয়েস: ${selectedVoice.name} • ${selectedVoice.styleBadge}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD0E8FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SecretsConfigurationNoticeCard(
    onOpenApiKeyDialog: () -> Unit,
    onQuickSaveApiKey: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("secrets_notice_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "API Key Setup Info",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Cartesia API Key পার্মানেন্ট সেভ করুন",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "আপনার sk_car_… কী-টি একবার সেভ করলেই অ্যাপে স্থায়ীভাবে সংরক্ষিত থাকবে অথবা AI Studio-এর Secrets প্যানেলে CARTESIA_API_KEY হিসেবে যুক্ত করতে পারেন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenApiKeyDialog,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_api_key_dialog_button")
                ) {
                    Text("API Key বসান (Save Key)")
                }
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                        if (clip.isNotEmpty()) {
                            onQuickSaveApiKey(clip)
                        } else {
                            onOpenApiKeyDialog()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paste_save_api_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("পেস্ট ও সেভ")
                }
            }
        }
    }
}

@Composable
private fun AcousticConfigurationCard(
    uiState: TtsStudioUiState,
    onVoiceSelected: (TtsVoice) -> Unit,
    onModelSelected: (TtsModelOption) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onEngineModeSelected: (TtsEngineMode) -> Unit,
    onOpenVoicesTab: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ভয়েস ও অডিও সেটিংস (Voice Controls)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = onOpenVoicesTab,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("browse_all_voices_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সব ভয়েস (${uiState.availableVoices.size})")
                }
            }

            // Horizontal Voice Selector Cards with keys & contentType
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("studio_voice_selector_row")
            ) {
                items(
                    items = uiState.availableVoices,
                    key = { it.id },
                    contentType = { "voice_card" }
                ) { voice ->
                    val isSelected = voice.id == uiState.selectedVoice.id
                    Surface(
                        modifier = Modifier
                            .width(196.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onVoiceSelected(voice) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("voice_card_${voice.name.lowercase().replace(" ", "_")}"),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = voice.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                            Text(
                                text = voice.banglaTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = voice.styleBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Sonic AI Model Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Cartesia Sonic মডেল (AI Model):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = TtsDefaults.models,
                        key = { it.id },
                        contentType = { "model_chip" }
                    ) { model ->
                        FilterChip(
                            selected = uiState.selectedModel.id == model.id,
                            onClick = { onModelSelected(model) },
                            label = {
                                Text("${model.title} (${model.badge})")
                            },
                            modifier = Modifier.testTag("model_chip_${model.id}")
                        )
                    }
                }
            }

            // Speech Speed Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "কথার গতি (Speech Speed)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1fx", uiState.speechSpeed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = uiState.speechSpeed,
                    onValueChange = onSpeedChanged,
                    valueRange = 0.6f..1.5f,
                    steps = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("speech_speed_slider")
                )
            }

            // Engine Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "সিন্থেসিস ইঞ্জিন (Synthesis Engine):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = TtsEngineMode.entries,
                        key = { it.name },
                        contentType = { "engine_chip" }
                    ) { mode ->
                        FilterChip(
                            selected = uiState.engineMode == mode,
                            onClick = { onEngineModeSelected(mode) },
                            label = { Text(mode.titleBangla) },
                            modifier = Modifier.testTag("engine_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}
