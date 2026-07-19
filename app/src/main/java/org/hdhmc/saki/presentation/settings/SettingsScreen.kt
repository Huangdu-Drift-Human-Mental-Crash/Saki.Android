package org.hdhmc.saki.presentation.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.BLUETOOTH_LYRICS_OFFSET_STEP_MS
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.CUSTOM_BUFFER_STEP_SECONDS
import org.hdhmc.saki.domain.model.DEFAULT_THEME_SEED_KEY
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.IMAGE_CACHE_SIZE_STEP_MB
import org.hdhmc.saki.domain.model.MAX_BLUETOOTH_LYRICS_OFFSET_MS
import org.hdhmc.saki.domain.model.MAX_CUSTOM_BUFFER_SECONDS
import org.hdhmc.saki.domain.model.MAX_IMAGE_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MAX_SONGS_PAGE_SIZE
import org.hdhmc.saki.domain.model.MAX_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MIN_BLUETOOTH_LYRICS_OFFSET_MS
import org.hdhmc.saki.domain.model.MIN_CUSTOM_BUFFER_SECONDS
import org.hdhmc.saki.domain.model.MIN_IMAGE_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MIN_SONGS_PAGE_SIZE
import org.hdhmc.saki.domain.model.MIN_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.SakiPaletteStyle
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SONGS_PAGE_SIZE_STEP
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.STREAM_CACHE_SIZE_STEP_MB
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.presentation.SakiSettingsUiState
import org.hdhmc.saki.presentation.labelRes
import org.hdhmc.saki.presentation.library.ArtworkCard
import org.hdhmc.saki.presentation.library.THUMBNAIL_COVER_ART_SIZE_PX
import org.hdhmc.saki.presentation.library.resolveArtworkModel
import org.hdhmc.saki.presentation.bottomContentPadding
import org.hdhmc.saki.presentation.rememberBrowseBackgroundBrush
import org.hdhmc.saki.ui.theme.SakiChromeIconButton
import org.hdhmc.saki.ui.theme.SakiThemePresets
import org.hdhmc.saki.ui.theme.SYSTEM_DYNAMIC_THEME_SEED_KEY
import org.hdhmc.saki.ui.theme.isSystemDynamicSeed
import org.hdhmc.saki.ui.theme.systemDynamicSeedColor
import org.hdhmc.saki.ui.theme.sakiExpressiveColorScheme
import org.hdhmc.saki.ui.theme.sakiCardContainerColor
import org.hdhmc.saki.ui.theme.sakiSelectedContainerColor
import org.hdhmc.saki.ui.theme.sakiTonalContainerColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SakiSettingsUiState,
    contentPadding: PaddingValues,
    bottomOverlayPadding: Dp = 0.dp,
    onClose: () -> Unit,
    onManageServers: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onUpdateStreamQuality: (StreamQuality) -> Unit,
    onUpdateAdaptiveQuality: (Boolean) -> Unit,
    onUpdateWifiStreamQuality: (StreamQuality) -> Unit,
    onUpdateMobileStreamQuality: (StreamQuality) -> Unit,
    onUpdateSoundBalancing: (SoundBalancingMode) -> Unit,
    onUpdateStreamCacheSizeMb: (Int) -> Unit,
    onClearStreamCache: () -> Unit,
    onUpdateImageCacheSizeMb: (Int) -> Unit,
    onClearImageCache: () -> Unit,
    onUpdateSongMetadata: () -> Unit,
    onUpdateHideMergedArtists: (Boolean) -> Unit,
    onUpdateTextScale: (TextScale) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateThemeSeed: (String) -> Unit,
    onUpdatePaletteStyle: (SakiPaletteStyle) -> Unit,
    onUpdateDefaultBrowseTab: (DefaultBrowseTab) -> Unit,
    onUpdateDefaultAlbumFeed: (AlbumListType) -> Unit,
    onUpdateSongsPageSize: (Int) -> Unit,
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBluetoothLyricsOffsetMs: (Int) -> Unit,
    onUpdateBufferStrategy: (BufferStrategy) -> Unit,
    onUpdateCustomBufferSeconds: (Int) -> Unit,
    onExportConfig: (android.net.Uri) -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
    onPlayCachedSong: (CachedSong) -> Unit,
    onPlayCachedQueue: (List<CachedSong>, Int) -> Unit,
    onDeleteCachedSong: (String) -> Unit,
    onClearCachedSongs: () -> Unit,
    onUpdateDownloadQuality: (StreamQuality) -> Unit,
) {
    val background = rememberBrowseBackgroundBrush()
    val selectedServer = uiState.servers.firstOrNull { it.id == uiState.selectedServerId }
    val visibleCachedSongs = remember(uiState.cachedSongs, uiState.selectedServerId) {
        uiState.cachedSongs.filter { song ->
            uiState.selectedServerId == null || song.serverId == uiState.selectedServerId
        }
    }
    val storageSummary = uiState.cacheStorageSummary
    val configuredStreamCacheSizeMb = uiState.playbackPreferences.streamCacheSizeMb
    var streamCacheSliderValue by remember(configuredStreamCacheSizeMb) {
        mutableFloatStateOf(configuredStreamCacheSizeMb.toFloat())
    }
    val configuredImageCacheSizeMb = uiState.playbackPreferences.imageCacheSizeMb
    var imageCacheSliderValue by remember(configuredImageCacheSizeMb) {
        mutableFloatStateOf(configuredImageCacheSizeMb.toFloat())
    }
    val configuredSongsPageSize = uiState.appPreferences.songsPageSize
    var songsPageSizeSliderValue by remember(configuredSongsPageSize) {
        mutableFloatStateOf(configuredSongsPageSize.toFloat())
    }
    val configuredBluetoothLyricsOffsetMs = uiState.playbackPreferences.bluetoothLyricsOffsetMs
    var bluetoothLyricsOffsetSliderValue by remember(configuredBluetoothLyricsOffsetMs) {
        mutableFloatStateOf(configuredBluetoothLyricsOffsetMs.toFloat())
    }

    // Precompute the theme-color swatch palette once, off the scroll path. Otherwise the theme
    // LazyColumn item generates 8 full color schemes the instant it composes mid-scroll (a frame
    // drop) and recomputes them every time it is recycled and scrolled back into view.
    val swatchPreviewDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val swatchPaletteStyle = uiState.appPreferences.paletteStyle
    val swatchContext = LocalContext.current
    val supportsSystemColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val themeSwatchColors: Map<String, ThemeSeedSwatchColors> =
        remember(swatchPreviewDark, swatchPaletteStyle, supportsSystemColor) {
            buildMap {
                SakiThemePresets.forEach { preset ->
                    put(
                        preset.key,
                        themeSeedSwatchColors(
                            sakiExpressiveColorScheme(preset.seed, swatchPreviewDark, swatchPaletteStyle),
                        ),
                    )
                }
                if (supportsSystemColor) {
                    put(
                        SYSTEM_DYNAMIC_THEME_SEED_KEY,
                        themeSeedSwatchColors(
                            sakiExpressiveColorScheme(
                                systemDynamicSeedColor(swatchContext),
                                swatchPreviewDark,
                                swatchPaletteStyle,
                            ),
                        ),
                    )
                }
            }
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(contentPadding)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = sakiCardContainerColor(),
                ),
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        SakiChromeIconButton(
                            onClick = onClose,
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_close),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_server_profiles_title),
                body = stringResource(R.string.settings_server_profiles_body),
                action = null,
            ) {
                if (uiState.servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_servers),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.servers.forEach { server ->
                        ServerRow(
                            server = server,
                            selected = server.id == uiState.selectedServerId,
                            onClick = { onSelectServer(server.id) },
                        )
                    }
                }
                FilledTonalButton(onClick = onManageServers, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.WifiTethering, contentDescription = null)
                    Text(stringResource(R.string.settings_open_server_manager), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val prefs = uiState.playbackPreferences
            SettingsSectionCard(
                title = stringResource(R.string.settings_stream_quality_title),
                body = stringResource(R.string.settings_stream_quality_body),
                action = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_adaptive_quality), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = prefs.adaptiveQualityEnabled,
                        onCheckedChange = onUpdateAdaptiveQuality,
                    )
                }
                if (prefs.adaptiveQualityEnabled) {
                    Text(stringResource(R.string.settings_wifi), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.wifiStreamQuality == quality,
                                onClick = { onUpdateWifiStreamQuality(quality) },
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_mobile), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.mobileStreamQuality == quality,
                                onClick = { onUpdateMobileStreamQuality(quality) },
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.streamQuality == quality,
                                onClick = { onUpdateStreamQuality(quality) },
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_sound_balancing_title),
                body = stringResource(R.string.settings_sound_balancing_body),
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoundBalancingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.playbackPreferences.soundBalancingMode == mode,
                            onClick = { onUpdateSoundBalancing(mode) },
                            label = { Text(mode.localizedLabel()) },
                        )
                    }
                }
            }
        }

        item {
            val prefs = uiState.playbackPreferences
            val configuredSeconds = prefs.customBufferSeconds
            var bufferSliderValue by remember(configuredSeconds) {
                mutableFloatStateOf(configuredSeconds.toFloat())
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_buffer_strategy_title),
                body = stringResource(R.string.settings_buffer_strategy_body),
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BufferStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = prefs.bufferStrategy == strategy,
                            onClick = { onUpdateBufferStrategy(strategy) },
                            label = { Text(strategy.localizedLabel()) },
                        )
                    }
                }
                when (prefs.bufferStrategy) {
                    BufferStrategy.NORMAL -> Text(
                        text = stringResource(R.string.settings_buffer_strategy_normal_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BufferStrategy.CUSTOM -> {
                        Text(
                            text = stringResource(R.string.settings_buffer_strategy_custom_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.settings_buffer_ahead_duration, formatBufferDuration(bufferSliderValue.toBufferSeconds())),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Slider(
                            value = bufferSliderValue,
                            onValueChange = { bufferSliderValue = it },
                            valueRange = MIN_CUSTOM_BUFFER_SECONDS.toFloat()..MAX_CUSTOM_BUFFER_SECONDS.toFloat(),
                            steps = ((MAX_CUSTOM_BUFFER_SECONDS - MIN_CUSTOM_BUFFER_SECONDS) / CUSTOM_BUFFER_STEP_SECONDS) - 1,
                            onValueChangeFinished = {
                                val newSeconds = bufferSliderValue.toBufferSeconds()
                                bufferSliderValue = newSeconds.toFloat()
                                if (newSeconds != configuredSeconds) {
                                    onUpdateCustomBufferSeconds(newSeconds)
                                }
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatBufferDuration(MIN_CUSTOM_BUFFER_SECONDS),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatBufferDuration(MAX_CUSTOM_BUFFER_SECONDS),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_restart_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_text_size_title),
                body = stringResource(R.string.settings_text_size_body),
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextScale.entries.forEach { textScale ->
                        FilterChip(
                            selected = uiState.textScale == textScale,
                            onClick = { onUpdateTextScale(textScale) },
                            label = { Text(textScale.localizedLabel()) },
                            leadingIcon = {
                                Icon(Icons.Rounded.TextFields, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_language_title),
                body = stringResource(R.string.settings_language_body),
                action = null,
            ) {
                val currentLanguage = uiState.appPreferences.language
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LanguageChip(
                        label = stringResource(R.string.settings_language_system),
                        selected = currentLanguage == AppLanguage.SYSTEM,
                        coverage = null,
                        onClick = { onUpdateLanguage(AppLanguage.SYSTEM) },
                    )
                    LanguageChip(
                        label = stringResource(R.string.settings_language_english),
                        selected = currentLanguage == AppLanguage.ENGLISH,
                        coverage = null,
                        onClick = { onUpdateLanguage(AppLanguage.ENGLISH) },
                    )
                    LanguageChip(
                        label = stringResource(R.string.settings_language_chinese),
                        selected = currentLanguage == AppLanguage.CHINESE,
                        coverage = translationCoverage("zh"),
                        onClick = { onUpdateLanguage(AppLanguage.CHINESE) },
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_theme_title),
                body = stringResource(R.string.settings_theme_body),
                action = null,
            ) {
                val currentTheme = uiState.appPreferences.themeMode
                Text(
                    text = stringResource(R.string.settings_theme_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { onUpdateThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_theme_system)) },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onUpdateThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light)) },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onUpdateThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark)) },
                    )
                }
                val currentPaletteStyle = uiState.appPreferences.paletteStyle
                Text(
                    text = stringResource(R.string.settings_palette_style_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = currentPaletteStyle == SakiPaletteStyle.TONAL_SPOT,
                        onClick = { onUpdatePaletteStyle(SakiPaletteStyle.TONAL_SPOT) },
                        label = { Text(stringResource(R.string.settings_palette_style_tonal_spot)) },
                    )
                    FilterChip(
                        selected = currentPaletteStyle == SakiPaletteStyle.VIBRANT,
                        onClick = { onUpdatePaletteStyle(SakiPaletteStyle.VIBRANT) },
                        label = { Text(stringResource(R.string.settings_palette_style_vibrant)) },
                    )
                    FilterChip(
                        selected = currentPaletteStyle == SakiPaletteStyle.EXPRESSIVE,
                        onClick = { onUpdatePaletteStyle(SakiPaletteStyle.EXPRESSIVE) },
                        label = { Text(stringResource(R.string.settings_palette_style_expressive)) },
                    )
                }
                val currentSeedKey = uiState.appPreferences.themeSeedKey
                // Below Android 12 a stored "system" seed falls back to the brand seed, so show
                // that preset as selected rather than leaving nothing highlighted.
                val effectiveSeedKey = if (!supportsSystemColor && isSystemDynamicSeed(currentSeedKey)) {
                    DEFAULT_THEME_SEED_KEY
                } else {
                    currentSeedKey
                }
                Text(
                    text = stringResource(R.string.settings_theme_seed_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    themeSwatchColors[SYSTEM_DYNAMIC_THEME_SEED_KEY]?.let { systemColors ->
                        ThemeSeedSwatch(
                            colors = systemColors,
                            isSelected = isSystemDynamicSeed(currentSeedKey),
                            description = stringResource(R.string.settings_theme_seed_system),
                            onClick = { onUpdateThemeSeed(SYSTEM_DYNAMIC_THEME_SEED_KEY) },
                            centerIcon = Icons.Rounded.Smartphone,
                        )
                    }
                    SakiThemePresets.forEach { preset ->
                        ThemeSeedSwatch(
                            colors = themeSwatchColors.getValue(preset.key),
                            isSelected = preset.key == effectiveSeedKey,
                            description = stringResource(preset.nameRes),
                            onClick = { onUpdateThemeSeed(preset.key) },
                        )
                    }
                }

            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_default_browse_title),
                body = stringResource(R.string.settings_default_browse_body),
                action = null,
            ) {
                val defaultBrowseTab = uiState.appPreferences.defaultBrowseTab
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DefaultBrowseTab.entries.forEach { tab ->
                        FilterChip(
                            selected = defaultBrowseTab == tab,
                            onClick = { onUpdateDefaultBrowseTab(tab) },
                            label = { Text(stringResource(tab.labelRes())) },
                        )
                    }
                }
                if (defaultBrowseTab == DefaultBrowseTab.ALBUMS) {
                    Text(
                        text = stringResource(R.string.settings_default_album_feed),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AlbumListType.defaultBrowseFeeds.forEach { feed ->
                            FilterChip(
                                selected = uiState.appPreferences.defaultAlbumFeed == feed,
                                onClick = { onUpdateDefaultAlbumFeed(feed) },
                                label = { Text(stringResource(feed.labelRes())) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_songs_page_size_title),
                body = stringResource(R.string.settings_songs_page_size_body),
                action = null,
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_songs_page_size_value,
                        songsPageSizeSliderValue.toSongsPageSize(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = songsPageSizeSliderValue,
                    onValueChange = { songsPageSizeSliderValue = it },
                    valueRange = MIN_SONGS_PAGE_SIZE.toFloat()..MAX_SONGS_PAGE_SIZE.toFloat(),
                    steps = SONGS_PAGE_SIZE_SLIDER_STEPS,
                    onValueChangeFinished = {
                        val newPageSize = songsPageSizeSliderValue.toSongsPageSize()
                        songsPageSizeSliderValue = newPageSize.toFloat()
                        if (newPageSize != configuredSongsPageSize) {
                            onUpdateSongsPageSize(newPageSize)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_songs_page_size_value, MIN_SONGS_PAGE_SIZE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_songs_page_size_value, MAX_SONGS_PAGE_SIZE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            val streamCacheCount = pluralStringResource(
                R.plurals.settings_stream_cached_track_count,
                storageSummary.streamCachedSongCount,
                storageSummary.streamCachedSongCount,
            )
            val previewStreamCacheSizeMb = streamCacheSliderValue.toStreamCacheSizeMb()
            val streamCacheBody = if (selectedServer != null) {
                stringResource(R.string.settings_cache_count_on_server, streamCacheCount, selectedServer.name)
            } else {
                streamCacheCount
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_streaming_cache_title),
                body = streamCacheBody,
                action = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(storageSummary.streamCacheBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_cache_limit,
                            formatStorageSize(previewStreamCacheSizeMb.toLong() * 1024L * 1024L),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        val limit = previewStreamCacheSizeMb.toLong() * 1024L * 1024L
                        if (limit <= 0L) 0f
                        else (storageSummary.streamCacheBytes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = streamCacheSliderValue,
                    onValueChange = { streamCacheSliderValue = it },
                    valueRange = MIN_STREAM_CACHE_SIZE_MB.toFloat()..MAX_STREAM_CACHE_SIZE_MB.toFloat(),
                    steps = STREAM_CACHE_SLIDER_STEPS,
                    onValueChangeFinished = {
                        val newSizeMb = streamCacheSliderValue.toStreamCacheSizeMb()
                        streamCacheSliderValue = newSizeMb.toFloat()
                        if (newSizeMb != configuredStreamCacheSizeMb) {
                            onUpdateStreamCacheSizeMb(newSizeMb)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(MIN_STREAM_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatStorageSize(MAX_STREAM_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearStreamCache,
                    enabled = storageSummary.streamCacheBytes > 0L,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text(stringResource(R.string.settings_clear_stream_cache), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val metadataBody = selectedServer?.let { server ->
                stringResource(R.string.settings_song_metadata_body_server, server.name)
            } ?: stringResource(R.string.settings_song_metadata_body)
            SettingsSectionCard(
                title = stringResource(R.string.settings_song_metadata_title),
                body = metadataBody,
                action = null,
            ) {
                if (uiState.isSongMetadataSyncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.settings_song_metadata_syncing, uiState.songMetadataSyncCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onUpdateSongMetadata,
                    enabled = selectedServer != null && !uiState.isSongMetadataSyncing,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null)
                    Text(stringResource(R.string.settings_update_song_metadata), modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.settings_hide_merged_artists),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.settings_hide_merged_artists_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.appPreferences.hideMergedArtists,
                        onCheckedChange = onUpdateHideMergedArtists,
                    )
                }
            }
        }

        item {
            val previewImageCacheSizeMb = imageCacheSliderValue.toImageCacheSizeMb()
            SettingsSectionCard(
                title = stringResource(R.string.settings_cover_art_cache_title),
                body = stringResource(R.string.settings_cover_art_cache_body),
                action = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(storageSummary.imageCacheBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_cache_limit,
                            formatStorageSize(previewImageCacheSizeMb.toLong() * 1024L * 1024L),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        val limit = previewImageCacheSizeMb.toLong() * 1024L * 1024L
                        if (limit <= 0L) 0f
                        else (storageSummary.imageCacheBytes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = imageCacheSliderValue,
                    onValueChange = { imageCacheSliderValue = it },
                    valueRange = MIN_IMAGE_CACHE_SIZE_MB.toFloat()..MAX_IMAGE_CACHE_SIZE_MB.toFloat(),
                    steps = ((MAX_IMAGE_CACHE_SIZE_MB - MIN_IMAGE_CACHE_SIZE_MB) / IMAGE_CACHE_SIZE_STEP_MB) - 1,
                    onValueChangeFinished = {
                        val newSizeMb = imageCacheSliderValue.toImageCacheSizeMb()
                        imageCacheSliderValue = newSizeMb.toFloat()
                        if (newSizeMb != configuredImageCacheSizeMb) {
                            onUpdateImageCacheSizeMb(newSizeMb)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(MIN_IMAGE_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatStorageSize(MAX_IMAGE_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearImageCache,
                    enabled = storageSummary.imageCacheBytes > 0L,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text(stringResource(R.string.settings_clear_cover_art_cache), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val downloadCount = pluralStringResource(
                R.plurals.settings_download_count,
                storageSummary.downloadedSongCount,
                storageSummary.downloadedSongCount,
            )
            val downloadsBody = stringResource(
                R.string.settings_downloads_body,
                downloadCount,
                formatStorageSize(storageSummary.downloadedBytes),
            )
            val downloadsBodyWithServer = if (selectedServer != null) {
                stringResource(R.string.settings_cache_count_on_server, downloadsBody, selectedServer.name)
            } else {
                downloadsBody
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_downloads_title),
                body = downloadsBodyWithServer,
                action = null,
            ) {
                Text(
                    text = stringResource(R.string.settings_download_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val currentDownloadQuality = uiState.playbackPreferences.downloadQuality
                    StreamQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = currentDownloadQuality == quality,
                            onClick = { onUpdateDownloadQuality(quality) },
                            label = { Text(quality.localizedLabel()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleCachedSongs.isNotEmpty()) {
                        OutlinedButton(onClick = { onPlayCachedQueue(visibleCachedSongs, 0) }, shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.settings_play_all), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = onClearCachedSongs,
                        enabled = visibleCachedSongs.isNotEmpty(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text(stringResource(R.string.settings_clear_all), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (visibleCachedSongs.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Text(
                            text = stringResource(R.string.settings_no_downloaded_tracks),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    visibleCachedSongs.forEachIndexed { index, song ->
                        CachedSongRow(
                            song = song,
                            server = selectedServer ?: uiState.servers.firstOrNull { it.id == song.serverId },
                            onPlay = { onPlayCachedSong(song) },
                            onDelete = { onDeleteCachedSong(song.cacheId) },
                            onPlayFromHere = { onPlayCachedQueue(visibleCachedSongs, index) },
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_experimental_title),
                body = stringResource(R.string.settings_experimental_body),
                action = null,
            ) {
                val checked = uiState.playbackPreferences.bluetoothLyricsEnabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = onUpdateBluetoothLyrics,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_bluetooth_lyrics_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_bluetooth_lyrics_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                    )
                }
                if (checked) {
                    val previewOffsetMs = bluetoothLyricsOffsetSliderValue.toBluetoothLyricsOffsetMs()
                    Text(
                        text = stringResource(R.string.settings_bluetooth_lyrics_offset_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_bluetooth_lyrics_offset_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatBluetoothLyricsOffset(previewOffsetMs),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Slider(
                        value = bluetoothLyricsOffsetSliderValue,
                        onValueChange = { bluetoothLyricsOffsetSliderValue = it },
                        valueRange = MIN_BLUETOOTH_LYRICS_OFFSET_MS.toFloat()..
                            MAX_BLUETOOTH_LYRICS_OFFSET_MS.toFloat(),
                        steps = BLUETOOTH_LYRICS_OFFSET_SLIDER_STEPS,
                        onValueChangeFinished = {
                            val newOffsetMs = bluetoothLyricsOffsetSliderValue
                                .toBluetoothLyricsOffsetMs()
                            bluetoothLyricsOffsetSliderValue = newOffsetMs.toFloat()
                            if (newOffsetMs != configuredBluetoothLyricsOffsetMs) {
                                onUpdateBluetoothLyricsOffsetMs(newOffsetMs)
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatBluetoothLyricsOffset(MIN_BLUETOOTH_LYRICS_OFFSET_MS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatBluetoothLyricsOffset(MAX_BLUETOOTH_LYRICS_OFFSET_MS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> if (uri != null) onExportConfig(uri) }
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) onImportConfig(uri) }
            SettingsSectionCard(
                title = stringResource(R.string.settings_backup_restore_title),
                body = stringResource(R.string.settings_backup_restore_body),
                action = null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("saki-backup.json") }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Text(stringResource(R.string.settings_export), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text(stringResource(R.string.settings_import), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    body: String,
    action: (@Composable (() -> Unit))?,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = sakiCardContainerColor(),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerConfig,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                sakiSelectedContainerColor()
            } else {
                sakiTonalContainerColor()
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = server.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${server.username} • ${
                    pluralStringResource(
                        R.plurals.settings_endpoint_count,
                        server.endpoints.size,
                        server.endpoints.size,
                    )
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CachedSongRow(
    song: CachedSong,
    server: ServerConfig?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPlayFromHere: () -> Unit,
) {
    val qualityLabel = song.quality.localizedLabel()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onPlay),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkCard(
            model = resolveArtworkModel(server, song.coverArtId, song),
            contentDescription = song.title,
            modifier = Modifier.size(60.dp),
            cornerRadiusDp = 18,
            requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • ").ifBlank { qualityLabel },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlayFromHere) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.settings_play_from_here))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.settings_remove_download))
        }
    }
}

@Composable
private fun StreamQuality.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun SoundBalancingMode.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun BufferStrategy.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun TextScale.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    coverage: Int?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (coverage != null) {
                Text("$label (${stringResource(R.string.settings_language_coverage, coverage)})")
            } else {
                Text(label)
            }
        },
    )
}

@Composable
private fun translationCoverage(locale: String): Int {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(locale) {
        val res = context.resources
        val enConfig = android.content.res.Configuration(res.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }
        val enRes = context.createConfigurationContext(enConfig).resources
        val localeConfig = android.content.res.Configuration(res.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(locale))
        }
        val localizedRes = context.createConfigurationContext(localeConfig).resources

        val fields = R.string::class.java.fields
        var total = 0
        var translated = 0
        for (field in fields) {
            val id = field.getInt(null)
            if (enRes.getString(id) != localizedRes.getString(id)) translated++
            total++
        }
        if (total > 0) (translated * 100) / total else 0
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return if (value >= 100 || unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

private data class ThemeSeedSwatchColors(
    val top: Color,
    val bottom: Color,
    val center: Color,
    val onCenter: Color,
)

/** Maps a generated scheme to the swatch's roles, using the roles the app actually renders. */
private fun themeSeedSwatchColors(scheme: ColorScheme) = ThemeSeedSwatchColors(
    top = scheme.primaryContainer,
    bottom = scheme.secondaryContainer,
    center = scheme.primary,
    onCenter = scheme.onPrimary,
)

/**
 * "Pokéball" theme swatch (KernelSU-style, scaled down for Settings): a rounded surfaceContainer
 * tile holding a two-tone ball (top = primaryContainer, bottom = secondaryContainer) with a small
 * primary center button; selection grows the button into a ringed, checked badge.
 */
@Composable
private fun ThemeSeedSwatch(
    colors: ThemeSeedSwatchColors,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centerIcon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(34.dp)) {
                drawArc(color = colors.top, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = colors.bottom, startAngle = 0f, sweepAngle = 180f, useCenter = true)
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(2.dp, colors.center, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.center),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.onCenter,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(colors.center),
                    contentAlignment = Alignment.Center,
                ) {
                    // System/Material You marker inside the center button when unselected.
                    if (centerIcon != null) {
                        Icon(
                            imageVector = centerIcon,
                            contentDescription = null,
                            tint = colors.onCenter,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val STREAM_CACHE_SLIDER_STEPS =
    ((MAX_STREAM_CACHE_SIZE_MB - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB) - 1

private const val SONGS_PAGE_SIZE_SLIDER_STEPS =
    ((MAX_SONGS_PAGE_SIZE - MIN_SONGS_PAGE_SIZE) / SONGS_PAGE_SIZE_STEP) - 1

private const val BLUETOOTH_LYRICS_OFFSET_SLIDER_STEPS =
    ((MAX_BLUETOOTH_LYRICS_OFFSET_MS - MIN_BLUETOOTH_LYRICS_OFFSET_MS) /
        BLUETOOTH_LYRICS_OFFSET_STEP_MS) - 1

private fun Float.toStreamCacheSizeMb(): Int {
    val stepsFromMin = ((this - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB).roundToInt()
    return (MIN_STREAM_CACHE_SIZE_MB + (stepsFromMin * STREAM_CACHE_SIZE_STEP_MB))
        .coerceIn(MIN_STREAM_CACHE_SIZE_MB, MAX_STREAM_CACHE_SIZE_MB)
}

private fun Float.toSongsPageSize(): Int {
    val stepsFromMin = ((this - MIN_SONGS_PAGE_SIZE) / SONGS_PAGE_SIZE_STEP).roundToInt()
    return (MIN_SONGS_PAGE_SIZE + (stepsFromMin * SONGS_PAGE_SIZE_STEP))
        .coerceIn(MIN_SONGS_PAGE_SIZE, MAX_SONGS_PAGE_SIZE)
}

private fun Float.toBufferSeconds(): Int {
    val stepsFromMin = ((this - MIN_CUSTOM_BUFFER_SECONDS) / CUSTOM_BUFFER_STEP_SECONDS).roundToInt()
    return (MIN_CUSTOM_BUFFER_SECONDS + (stepsFromMin * CUSTOM_BUFFER_STEP_SECONDS))
        .coerceIn(MIN_CUSTOM_BUFFER_SECONDS, MAX_CUSTOM_BUFFER_SECONDS)
}

private fun Float.toBluetoothLyricsOffsetMs(): Int {
    val stepsFromMin =
        ((this - MIN_BLUETOOTH_LYRICS_OFFSET_MS) / BLUETOOTH_LYRICS_OFFSET_STEP_MS).roundToInt()
    return (MIN_BLUETOOTH_LYRICS_OFFSET_MS +
        (stepsFromMin * BLUETOOTH_LYRICS_OFFSET_STEP_MS))
        .coerceIn(MIN_BLUETOOTH_LYRICS_OFFSET_MS, MAX_BLUETOOTH_LYRICS_OFFSET_MS)
}

@Composable
private fun formatBluetoothLyricsOffset(offsetMs: Int): String {
    return if (offsetMs == 0) {
        stringResource(R.string.settings_bluetooth_lyrics_offset_none)
    } else {
        stringResource(
            R.string.settings_bluetooth_lyrics_offset_value,
            offsetMs / 1_000f,
        )
    }
}

@Composable
private fun formatBufferDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.settings_seconds_short, seconds)
        seconds == 0 -> stringResource(R.string.settings_minutes_short, minutes)
        else -> stringResource(R.string.settings_minutes_seconds_short, minutes, seconds)
    }
}

private fun Float.toImageCacheSizeMb(): Int {
    val stepsFromMin = ((this - MIN_IMAGE_CACHE_SIZE_MB) / IMAGE_CACHE_SIZE_STEP_MB).roundToInt()
    return (MIN_IMAGE_CACHE_SIZE_MB + (stepsFromMin * IMAGE_CACHE_SIZE_STEP_MB))
        .coerceIn(MIN_IMAGE_CACHE_SIZE_MB, MAX_IMAGE_CACHE_SIZE_MB)
}
