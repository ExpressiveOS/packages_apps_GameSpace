/*
 * Copyright (C) 2025-2026 AxionOS
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package io.chaldeaprjkt.gamespace.gamebar

import android.app.ActivityManager
import android.app.GameManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.IThermalService
import android.os.ServiceManager
import android.os.SystemClock
import android.os.Temperature
import android.os.UserHandle
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.compose.expressive.ExpressiveBlurSurface
import com.android.axion.compose.expressive.ExpressiveMorphButton
import com.android.axion.compose.expressive.ExpressiveMorphContainer
import com.android.axion.compose.expressive.ExpressiveShapePreset
import com.android.axion.compose.expressive.rememberExpressiveHaptics
import com.android.axion.compose.expressive.rememberExpressiveReducedMotion
import com.android.axion.compose.preferences.ExpressiveSwitch
import com.android.axion.compose.preferences.PreferencePosition
import com.android.axion.compose.preferences.PreferenceTokens
import com.android.axion.compose.preferences.preferenceShape
import com.android.axion.compose.scaffold.ExpressiveBackButton
import com.android.axion.compose.theme.EmphasizedTabularStyle
import com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX
import com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN
import com.android.settingslib.display.BrightnessUtils.convertLinearToGammaFloat
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.tiles.DragTileData
import io.chaldeaprjkt.gamespace.gamebar.tiles.DragType
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileAction
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileDragDropState
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.gamebar.tiles.dragAndDropActiveGrid
import io.chaldeaprjkt.gamespace.gamebar.tiles.dragAndDropAvailableTileSource
import io.chaldeaprjkt.gamespace.gamebar.tiles.dragAndDropAvailableZone
import io.chaldeaprjkt.gamespace.gamebar.tiles.dragAndDropTileSource
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GamePanelCard"
private val PanelOuterShape = RoundedCornerShape(28.dp)
private val PanelSectionShape = RoundedCornerShape(22.dp)

@Composable
fun GamePanelCard(
    interactor: BrightnessInteractor,
    fpsInteractor: FpsInteractor,
    apps: List<AppInfo>,
    gameModeUtils: GameModeUtils,
    systemSettings: SystemSettings,
    tileRepository: TileRepository,
    maxHeight: Dp = Dp.Unspecified,
    sessionStartElapsedMs: Long = SystemClock.elapsedRealtime(),
    liveFpsText: String = "",
    onDismissPanel: () -> Unit = {},
) {
    val panelWidth = 328.dp

    val initialMode = remember {
        fromSystemGameMode(gameModeUtils.activeGame?.mode ?: GameManager.GAME_MODE_STANDARD)
    }
    var selectedMode by remember { mutableStateOf(initialMode) }
    var headerExpanded by remember { mutableStateOf(false) }

    val time = rememberCurrentTime()
    val sessionClock = rememberSessionElapsedClock(sessionStartElapsedMs)

    ExpressiveBlurSurface(
        modifier = Modifier
            .width(panelWidth)
            .wrapContentHeight()
            .shadow(
                elevation = 12.dp,
                shape = PanelOuterShape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.42f),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = PanelOuterShape,
        tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimAlpha = 0.88f,
    ) {
        GamePanelContent(
            apps = apps,
            headerExpanded = headerExpanded,
            onToggleExpand = { headerExpanded = !headerExpanded },
            selectedMode = selectedMode,
            onModeChange = { mode ->
                selectedMode = mode
                gameModeUtils.setActiveGameMode(systemSettings, mode.toSystemGameMode())
            },
            interactor = interactor,
            fpsInteractor = fpsInteractor,
            time = time,
            sessionClock = sessionClock,
            liveFpsText = liveFpsText,
            tileRepository = tileRepository,
            maxHeight = maxHeight,
            onDismissPanel = onDismissPanel,
        )
    }
}

@Composable
fun GamePanelContent(
    apps: List<AppInfo>,
    headerExpanded: Boolean,
    onToggleExpand: () -> Unit,
    selectedMode: GameMode,
    onModeChange: (GameMode) -> Unit,
    interactor: BrightnessInteractor,
    fpsInteractor: FpsInteractor,
    time: String,
    sessionClock: String,
    liveFpsText: String,
    tileRepository: TileRepository,
    maxHeight: Dp = Dp.Unspecified,
    onDismissPanel: () -> Unit = {},
) {
    var isEditing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val resolvedMaxHeight = if (maxHeight != Dp.Unspecified) {
        maxHeight
    } else {
        (LocalConfiguration.current.screenHeightDp - 64).dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = resolvedMaxHeight)
            .then(if (!isEditing) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isEditing) {
            HeaderInfoBar(
                modifier = Modifier.fillMaxWidth(),
                headerExpanded = headerExpanded,
                time = time,
                sessionClock = sessionClock,
                liveFpsText = liveFpsText,
                currentMode = selectedMode,
                onModeChange = onModeChange,
                fpsInteractor = fpsInteractor,
                onToggleExpand = onToggleExpand,
                onEditClick = { isEditing = true },
                tileRepository = tileRepository,
            )

            if (apps.isNotEmpty()) {
                QuickStartAppSidebar(
                    apps = apps,
                    onAppLaunched = onDismissPanel,
                )
            }

            PanelContent(
                interactor = interactor,
                tileRepository = tileRepository,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TileEditPanel(
                tileRepository = tileRepository,
                onClose = { isEditing = false },
            )
        }
    }
}

@Composable
fun PanelContent(
    interactor: BrightnessInteractor,
    tileRepository: TileRepository,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberExpressiveReducedMotion()
    val tiles = tileRepository.tiles
    val tilesPerPage = 8
    val pages by remember {
        derivedStateOf { tiles.chunked(tilesPerPage) }
    }
    val pagerState = rememberPagerState { pages.size.coerceAtLeast(1) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (tileRepository.isBrightnessVisible.value) {
            BrightnessSlider(interactor = interactor)
        }

        if (pages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelSectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        key = { pageIndex -> "page_$pageIndex" },
                    ) { pageIndex ->
                        val pageTiles = pages.getOrNull(pageIndex).orEmpty()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 164.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
                        ) {
                            pageTiles.chunked(4).forEach { rowTiles ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowTiles.forEach { tile ->
                                        key(tile.id) {
                                            TileButton(
                                                tile = tile,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                    repeat(4 - rowTiles.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (pages.size > 1) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(pages.size) { index ->
                                val isSelected = index == pagerState.currentPage
                                val indicatorWidth by animateDpAsState(
                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                    animationSpec = if (reducedMotion) {
                                        snap()
                                    } else {
                                        MaterialTheme.motionScheme.fastSpatialSpec()
                                    },
                                    label = "pagerIndicatorWidth",
                                )
                                Box(
                                    modifier = Modifier
                                        .width(indicatorWidth)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                                            },
                                        ),
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
fun HeaderInfoBar(
    modifier: Modifier = Modifier,
    headerExpanded: Boolean = true,
    time: String,
    sessionClock: String,
    liveFpsText: String,
    currentMode: GameMode,
    onModeChange: (GameMode) -> Unit,
    fpsInteractor: FpsInteractor,
    onToggleExpand: () -> Unit,
    onEditClick: () -> Unit,
    tileRepository: TileRepository,
) {
    val reducedMotion = rememberExpressiveReducedMotion()
    val batteryInfo = rememberBatteryInfo()
    val systemTelemetry = rememberSystemTelemetry(fallbackTempC = batteryInfo.temperatureC)
    val liveFps by fpsInteractor.currentFps.collectAsState()

    val resolvedFpsDisplay = remember(liveFps, liveFpsText) {
        when {
            liveFps > 0.5f -> "${liveFps.roundToInt()} FPS"
            liveFpsText.isNotBlank() -> "$liveFpsText FPS"
            else -> "-- FPS"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (reducedMotion) {
                    Modifier
                } else {
                    Modifier.animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    )
                }
            )
            .clip(PanelSectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopRowHeader(
            time = time,
            sessionClock = sessionClock,
            currentMode = currentMode,
            headerExpanded = headerExpanded,
            onToggleExpand = onToggleExpand,
            onEditClick = onEditClick,
        )

        TelemetryPillStrip(
            fpsLabel = resolvedFpsDisplay,
            tempLabel = "${systemTelemetry.socOrSkinTempC.roundToInt()}\u00B0C",
            ramLabel = "${systemTelemetry.ramUsedPercent}% RAM",
            batteryInfo = batteryInfo,
        )

        if (reducedMotion) {
            if (headerExpanded) {
                ExpandedTelemetrySection(
                    tileRepository = tileRepository,
                    fpsInteractor = fpsInteractor,
                    currentMode = currentMode,
                    onModeChange = onModeChange,
                )
            }
        } else {
            AnimatedVisibility(
                visible = headerExpanded,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                ExpandedTelemetrySection(
                    tileRepository = tileRepository,
                    fpsInteractor = fpsInteractor,
                    currentMode = currentMode,
                    onModeChange = onModeChange,
                )
            }
        }
    }
}

@Composable
private fun ExpandedTelemetrySection(
    tileRepository: TileRepository,
    fpsInteractor: FpsInteractor,
    currentMode: GameMode,
    onModeChange: (GameMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tileRepository.isFpsGraphVisible.value) {
            FpsGraph(
                modifier = Modifier.fillMaxWidth(),
                interactor = fpsInteractor,
            )
        }
        GameModeSelector(
            selectedMode = currentMode,
            onModeSelected = onModeChange,
        )
    }
}

@Composable
private fun TopRowHeader(
    time: String,
    sessionClock: String,
    currentMode: GameMode,
    headerExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditClick: () -> Unit,
) {
    val reducedMotion = rememberExpressiveReducedMotion()
    val haptics = rememberExpressiveHaptics()

    val rotateArrow by animateFloatAsState(
        targetValue = if (headerExpanded) 180f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            MaterialTheme.motionScheme.fastSpatialSpec()
        },
        label = "headerChevronRotation",
    )

    val modeTint = when (currentMode) {
        GameMode.Performance -> MaterialTheme.colorScheme.tertiaryContainer
        GameMode.PowerSave -> MaterialTheme.colorScheme.secondaryContainer
        GameMode.Balanced -> MaterialTheme.colorScheme.primaryContainer
    }
    val modeOnTint = when (currentMode) {
        GameMode.Performance -> MaterialTheme.colorScheme.onTertiaryContainer
        GameMode.PowerSave -> MaterialTheme.colorScheme.onSecondaryContainer
        GameMode.Balanced -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = time,
                    style = EmphasizedTabularStyle(MaterialTheme.typography.titleLargeEmphasized),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Live session elapsed clock pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.clock_time_two_outline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = sessionClock,
                        style = EmphasizedTabularStyle(MaterialTheme.typography.labelSmallEmphasized),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(modeTint)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = currentMode.displayName,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = modeOnTint,
                )
            }
        }

        ExpressiveMorphContainer(
            active = false,
            inactivePreset = ExpressiveShapePreset.SQUIRCLE,
            activePreset = ExpressiveShapePreset.PILL,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onEditClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.materialsymbols_ic_edit_rounded_filled),
                contentDescription = stringResource(R.string.cd_edit_tiles),
                modifier = Modifier.size(18.dp),
            )
        }

        ExpressiveMorphContainer(
            active = headerExpanded,
            inactivePreset = ExpressiveShapePreset.SQUIRCLE,
            activePreset = ExpressiveShapePreset.PILL,
            containerColor = if (headerExpanded) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (headerExpanded) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            hapticOnClick = false,
            onClick = {
                haptics.performToggle(!headerExpanded)
                onToggleExpand()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.materialsymbols_ic_expand_more_rounded_filled),
                contentDescription = if (headerExpanded) {
                    stringResource(R.string.cd_collapse)
                } else {
                    stringResource(R.string.cd_expand)
                },
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotateArrow),
            )
        }
    }
}

@Composable
private fun TelemetryPillStrip(
    fpsLabel: String,
    tempLabel: String,
    ramLabel: String,
    batteryInfo: BatteryInfo,
) {
    val batteryLevel = batteryInfo.level.coerceAtLeast(0)
    val batteryIcon = when {
        batteryLevel >= 90 -> R.drawable.materialsymbols_ic_battery_android_full_rounded_filled
        batteryLevel >= 70 -> R.drawable.materialsymbols_ic_battery_android_4_rounded_filled
        batteryLevel >= 50 -> R.drawable.materialsymbols_ic_battery_android_3_rounded_filled
        batteryLevel >= 30 -> R.drawable.materialsymbols_ic_battery_android_2_rounded_filled
        batteryLevel >= 10 -> R.drawable.materialsymbols_ic_battery_android_1_rounded_filled
        else -> R.drawable.materialsymbols_ic_battery_android_0_rounded_filled
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TelemetryPillBadge(
            iconRes = R.drawable.ic_fps,
            value = fpsLabel,
            highlighted = true,
        )
        TelemetryPillBadge(
            iconRes = R.drawable.materialsymbols_ic_device_thermostat_rounded_filled,
            value = tempLabel,
        )
        TelemetryPillBadge(
            iconRes = R.drawable.materialsymbols_ic_memory_rounded_filled,
            value = ramLabel,
        )
        TelemetryPillBadge(
            iconRes = batteryIcon,
            value = if (batteryInfo.level >= 0) "${batteryInfo.level}%" else "--%",
        )
    }
}

@Composable
private fun TelemetryPillBadge(
    iconRes: Int,
    value: String,
    highlighted: Boolean = false,
) {
    val bgColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fgColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            style = EmphasizedTabularStyle(MaterialTheme.typography.labelSmallEmphasized),
            color = fgColor,
            maxLines = 1,
        )
    }
}

@Composable
fun TileEditPanel(
    tileRepository: TileRepository,
    onClose: () -> Unit,
) {
    val haptics = rememberExpressiveHaptics()
    val allTiles = remember { tileRepository.allAvailableTiles }
    val dragDropState = remember { TileDragDropState(tileRepository.tiles, columns = 4) }

    val availableList = remember {
        val activeIds = tileRepository.tiles.map { it.id }.toSet()
        allTiles.filter { it.id !in activeIds }
            .map { DragTileData(it.id, it.label, it.icon) }
            .toMutableStateList()
    }

    var activeGridOffset by remember { mutableStateOf(Offset.Zero) }
    val localMaxHeight = (LocalConfiguration.current.screenHeightDp - 64).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = localMaxHeight),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveBackButton(onClick = onClose)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cd_edit_tiles),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExpressiveMorphButton(
                onClick = {
                    tileRepository.updateTileSelection(dragDropState.tileIds())
                    onClose()
                },
                selected = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.panel_options),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
            )

            // Segmented 24dp outer / 4dp inner M3 Expressive toggle group
            Column(verticalArrangement = Arrangement.spacedBy(PreferenceTokens.ItemSpacing)) {
                SettingToggleRow(
                    title = stringResource(R.string.brightness_slider),
                    checked = tileRepository.isBrightnessVisible.value,
                    onCheckedChange = { tileRepository.setBrightnessEnabled(it) },
                    position = PreferencePosition.Top,
                )
                SettingToggleRow(
                    title = stringResource(R.string.fps_graph),
                    checked = tileRepository.isFpsGraphVisible.value,
                    onCheckedChange = { tileRepository.setFpsGraphEnabled(it) },
                    position = PreferencePosition.Bottom,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.selected_tiles),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelSectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp)
                    .onGloballyPositioned { coords ->
                        activeGridOffset = coords.positionInRoot()
                    }
                    .dragAndDropActiveGrid(
                        contentOffset = { activeGridOffset },
                        dragDropState = dragDropState,
                    ) { ids ->
                        dragDropState.draggedTile?.let { dragged ->
                            if (dragDropState.dragType == DragType.Add) {
                                availableList.removeAll { it.id == dragged.id }
                            }
                        }
                        haptics.performConfirm()
                        tileRepository.updateTileSelection(ids)
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tiles = dragDropState.tiles
                val rows = tiles.chunked(4)
                rows.forEachIndexed { rowIndex, rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTiles.forEachIndexed { colIndex, tile ->
                            val index = rowIndex * 4 + colIndex
                            val isBeingDragged = dragDropState.isMoving(tile.id)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .onGloballyPositioned { coords ->
                                        val posInRoot = coords.positionInRoot()
                                        val relativeOffset = posInRoot - activeGridOffset
                                        dragDropState.updateItemPosition(
                                            tile.id,
                                            index,
                                            IntOffset(
                                                relativeOffset.x.toInt(),
                                                relativeOffset.y.toInt()
                                            ),
                                            coords.size,
                                        )
                                    },
                            ) {
                                if (isBeingDragged) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                MaterialTheme.colorScheme.secondaryContainer.copy(
                                                    alpha = 0.55f
                                                )
                                            ),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .dragAndDropTileSource(
                                                tile,
                                                dragDropState,
                                                DragType.Move
                                            ),
                                    ) {
                                        EditorGameTile(
                                            label = tile.label,
                                            icon = tile.icon,
                                            isAdded = true,
                                            onAction = {
                                                haptics.performStepTick()
                                                val removed = dragDropState.removeTile(tile.id)
                                                if (removed != null) {
                                                    availableList.add(removed)
                                                    tileRepository.updateTileSelection(
                                                        dragDropState.tileIds()
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        repeat(4 - rowTiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (tiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.drag_tiles_here),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.available_tiles),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelSectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp)
                    .dragAndDropAvailableZone(dragDropState) { id ->
                        val removed = dragDropState.removeTile(id)
                        if (removed != null) {
                            availableList.add(removed)
                            tileRepository.updateTileSelection(dragDropState.tileIds())
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val rows = availableList.chunked(4)
                rows.forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTiles.forEach { tile ->
                            val isBeingDragged = dragDropState.isMoving(tile.id)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .graphicsLayer { alpha = if (isBeingDragged) 0.3f else 1f }
                                    .dragAndDropAvailableTileSource(tile, dragDropState),
                            ) {
                                EditorGameTile(
                                    label = tile.label,
                                    icon = tile.icon,
                                    isAdded = false,
                                    onAction = {
                                        haptics.performStepTick()
                                        availableList.remove(tile)
                                        dragDropState.addTile(tile)
                                        tileRepository.updateTileSelection(dragDropState.tileIds())
                                    },
                                )
                            }
                        }
                        repeat(4 - rowTiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (availableList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.all_tiles_added),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorGameTile(
    label: String,
    icon: Int,
    isAdded: Boolean,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            ExpressiveMorphContainer(
                active = isAdded,
                inactivePreset = ExpressiveShapePreset.SQUIRCLE,
                activePreset = ExpressiveShapePreset.PILL,
                containerColor = if (isAdded) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isAdded) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                hapticOnClick = false,
                onClick = onAction,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(
                        if (isAdded) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    .clickable { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Rounded.Remove else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (isAdded) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .width(66.dp)
                .basicMarquee(),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    position: PreferencePosition = PreferencePosition.Single,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberExpressiveHaptics()
    val shape = preferenceShape(position)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch,
            ) {
                val next = !checked
                haptics.performToggle(next)
                onCheckedChange(next)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(12.dp))
        ExpressiveSwitch(
            checked = checked,
            onCheckedChange = null,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun TileButton(tile: TileAction, modifier: Modifier = Modifier) {
    val isEnabled by tile.observeEnabled()
    val iconRes by tile.observeIcon()
    val labelText by tile.observeLabel()
    val haptics = rememberExpressiveHaptics()

    val bgColor = if (isEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fgColor = if (isEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.height(78.dp),
    ) {
        ExpressiveMorphContainer(
            active = isEnabled,
            inactivePreset = ExpressiveShapePreset.SQUIRCLE,
            activePreset = ExpressiveShapePreset.PILL,
            containerColor = bgColor,
            contentColor = fgColor,
            hapticOnClick = false,
            onClick = {
                if (tile.isOneShot) {
                    haptics.performConfirm()
                } else {
                    haptics.performToggle(!isEnabled)
                }
                tile.toggle()
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = labelText,
                tint = fgColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = labelText,
            style = if (isEnabled) {
                MaterialTheme.typography.labelSmallEmphasized
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(66.dp)
                .basicMarquee(),
        )
    }
}

@Composable
fun BrightnessSlider(interactor: BrightnessInteractor) {
    val brightnessInfo by interactor.brightnessInfo.collectAsState()
    val isAuto by interactor.isAuto.collectAsState()
    val haptics = rememberExpressiveHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var userIsAdjusting by remember { mutableStateOf(false) }

    val targetSliderPosition = remember(brightnessInfo) {
        brightnessInfo?.let { info ->
            val gamma = convertLinearToGammaFloat(
                info.brightness,
                info.brightnessMinimum,
                info.brightnessMaximum
            )
            getPercentage(
                gamma.toDouble(),
                GAMMA_SPACE_MIN.toFloat(),
                GAMMA_SPACE_MAX.toFloat()
            ).toFloat()
        } ?: 0.5f
    }

    var lastBucket by remember { mutableIntStateOf((targetSliderPosition * 20f).roundToInt()) }
    var wasAtBoundary by remember {
        mutableStateOf(targetSliderPosition <= 0.005f || targetSliderPosition >= 0.995f)
    }

    LaunchedEffect(targetSliderPosition) {
        if (!userIsAdjusting) {
            sliderPosition = targetSliderPosition
            lastBucket = (targetSliderPosition * 20f).roundToInt()
            wasAtBoundary = targetSliderPosition <= 0.005f || targetSliderPosition >= 0.995f
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { sliderPosition }
            .distinctUntilChanged()
            .collectLatest { value ->
                if (userIsAdjusting) {
                    interactor.onUserInteracted()
                    interactor.setBrightness(value)
                    delay(80)
                }
            }
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        activeTickColor = MaterialTheme.colorScheme.onPrimary,
        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
        inactiveTickColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelSectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = { rawValue ->
                val clamped = rawValue.coerceIn(0f, 1f)
                userIsAdjusting = true
                val atEdge = clamped <= 0.005f || clamped >= 0.995f
                val bucket = (clamped * 20f).roundToInt()
                if (atEdge && !wasAtBoundary) {
                    wasAtBoundary = true
                    lastBucket = bucket
                    haptics.performBoundaryBump()
                } else if (!atEdge) {
                    wasAtBoundary = false
                    if (bucket != lastBucket) {
                        lastBucket = bucket
                        haptics.performStepTick()
                    }
                }
                sliderPosition = clamped
                interactor.onUserInteracted()
                interactor.setBrightness(clamped)
            },
            onValueChangeFinished = {
                userIsAdjusting = false
            },
            enabled = true,
            valueRange = 0f..1f,
            interactionSource = interactionSource,
            colors = sliderColors,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = sliderColors,
                    enabled = true,
                    thumbSize = DpSize(
                        PreferenceTokens.SliderThumbWidth,
                        PreferenceTokens.SliderThumbHeight,
                    ),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = 11.dp,
                    modifier = Modifier.height(22.dp),
                    enabled = true,
                    colors = sliderColors,
                    thumbTrackGapSize = PreferenceTokens.SliderThumbTrackGap,
                    trackInsideCornerSize = PreferenceTokens.SliderTrackInsideCorner,
                )
            },
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .widthIn(min = 42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${(sliderPosition * 100f).roundToInt()}%",
                style = EmphasizedTabularStyle(MaterialTheme.typography.labelSmallEmphasized),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }

        val autoActive = isAuto == true
        ExpressiveMorphContainer(
            active = autoActive,
            inactivePreset = ExpressiveShapePreset.SQUIRCLE,
            activePreset = ExpressiveShapePreset.PILL,
            containerColor = if (autoActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (autoActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            enabled = brightnessInfo != null,
            hapticOnClick = false,
            onClick = {
                haptics.performToggle(!autoActive)
                interactor.toggleAutoMode()
            },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = if (autoActive) {
                    painterResource(R.drawable.materialsymbols_ic_brightness_auto_rounded_filled)
                } else {
                    painterResource(R.drawable.materialsymbols_ic_brightness_7_rounded_filled)
                },
                contentDescription = stringResource(R.string.brightness_slider),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun FpsGraph(
    modifier: Modifier = Modifier,
    interactor: FpsInteractor,
) {
    val fpsHistory by interactor.fpsHistory.collectAsState(initial = emptyList())
    val maxFps by interactor.dynamicMaxFps.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.outline

    val graphData = remember(fpsHistory, maxFps, primaryColor, tertiaryColor, errorColor, neutralColor) {
        if (fpsHistory.isNotEmpty()) {
            val minFps = fpsHistory.minOrNull() ?: 0f
            val avgFps = fpsHistory.average().toFloat()
            val maxFpsSeen = fpsHistory.maxOrNull() ?: 0f
            val clampedMaxFps = maxFps.takeIf { it > 0f } ?: 60f
            val fpsColor = when {
                avgFps >= clampedMaxFps * 0.88f -> primaryColor
                avgFps >= clampedMaxFps * 0.70f -> tertiaryColor
                else -> errorColor
            }
            Triple(Triple(minFps, avgFps, maxFpsSeen), clampedMaxFps, fpsColor)
        } else {
            Triple(Triple(0f, 0f, 0f), 60f, neutralColor)
        }
    }

    val normalizedPoints = remember(fpsHistory, graphData.second) {
        val clampedMaxFps = graphData.second
        fpsHistory.map { fps -> (fps / clampedMaxFps).coerceIn(0f, 1f) }
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (normalizedPoints.size > 1) {
                val width = size.width
                val height = size.height
                val stepX = width / (normalizedPoints.size - 1)

                val path = Path().apply {
                    moveTo(0f, height)
                    lineTo(0f, height - (normalizedPoints[0] * height))
                    for (i in 1 until normalizedPoints.size) {
                        val currentX = i * stepX
                        val currentY = height - (normalizedPoints[i] * height)
                        if (i == 1) {
                            lineTo(currentX, currentY)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = height - (normalizedPoints[i - 1] * height)
                            val controlX = (prevX + currentX) / 2f
                            val controlY = (prevY + currentY) / 2f
                            quadraticBezierTo(controlX, controlY, currentX, currentY)
                        }
                    }
                    lineTo(width, height)
                    close()
                }

                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        graphData.third.copy(alpha = 0.45f),
                        graphData.third.copy(alpha = 0.05f),
                    ),
                    startY = 0f,
                    endY = height,
                )
                drawPath(path = path, brush = gradient)

                val linePath = Path().apply {
                    moveTo(0f, height - (normalizedPoints[0] * height))
                    for (i in 1 until normalizedPoints.size) {
                        val currentX = i * stepX
                        val currentY = height - (normalizedPoints[i] * height)
                        if (i == 1) {
                            lineTo(currentX, currentY)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = height - (normalizedPoints[i - 1] * height)
                            val controlX = (prevX + currentX) / 2f
                            val controlY = (prevY + currentY) / 2f
                            quadraticBezierTo(controlX, controlY, currentX, currentY)
                        }
                    }
                }
                drawPath(
                    path = linePath,
                    color = graphData.third,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        val (minFps, avgFps, maxFpsSeen) = graphData.first
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.fps_stats,
                    minFps.toInt(),
                    avgFps.toInt(),
                    maxFpsSeen.toInt(),
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = EmphasizedTabularStyle(MaterialTheme.typography.labelSmallEmphasized),
            )
        }
    }
}

@Composable
fun GameModeSelector(
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
) {
    val modes = GameMode.values()
    val haptics = rememberExpressiveHaptics()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selectedMode
            val activeBg = when (mode) {
                GameMode.Performance -> MaterialTheme.colorScheme.tertiary
                GameMode.PowerSave -> MaterialTheme.colorScheme.secondary
                GameMode.Balanced -> MaterialTheme.colorScheme.primary
            }
            val activeFg = when (mode) {
                GameMode.Performance -> MaterialTheme.colorScheme.onTertiary
                GameMode.PowerSave -> MaterialTheme.colorScheme.onSecondary
                GameMode.Balanced -> MaterialTheme.colorScheme.onPrimary
            }

            ExpressiveMorphContainer(
                active = isSelected,
                inactivePreset = ExpressiveShapePreset.SQUIRCLE,
                activePreset = ExpressiveShapePreset.PILL,
                containerColor = if (isSelected) {
                    activeBg
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isSelected) {
                    activeFg
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                hapticOnClick = false,
                onClick = {
                    if (!isSelected) {
                        haptics.performDiscreteStepTick()
                        onModeSelected(mode)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = mode.displayName,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = mode.shortLabel,
                        style = if (isSelected) {
                            MaterialTheme.typography.labelSmallEmphasized
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickStartAppSidebar(
    apps: List<AppInfo>,
    onAppLaunched: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(PanelSectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.forEach { app ->
                QuickStartAppIcon(
                    appInfo = app,
                    onAppLaunched = onAppLaunched,
                )
            }
        }
    }
}

@Composable
fun rememberCurrentTime(): String {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeState = remember { mutableStateOf(timeFormat.format(Date())) }

    DisposableEffect(Unit) {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Main.immediate + job)

        scope.launch {
            while (isActive) {
                val now = Date()
                val newTime = timeFormat.format(now)
                if (newTime != timeState.value) {
                    timeState.value = newTime
                }
                val millisUntilNextMinute = 60000L - (now.time % 60000L)
                delay(millisUntilNextMinute)
            }
        }

        onDispose {
            job.cancel()
        }
    }

    return timeState.value
}

@Composable
fun rememberSessionElapsedClock(sessionStartElapsedMs: Long): String {
    var formatted by remember(sessionStartElapsedMs) {
        mutableStateOf(formatElapsedDuration(SystemClock.elapsedRealtime() - sessionStartElapsedMs))
    }

    LaunchedEffect(sessionStartElapsedMs) {
        while (isActive) {
            formatted = formatElapsedDuration(SystemClock.elapsedRealtime() - sessionStartElapsedMs)
            delay(1000L)
        }
    }
    return formatted
}

private fun formatElapsedDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

data class SystemTelemetry(
    val ramUsedPercent: Int = 0,
    val socOrSkinTempC: Float = 0f,
)

@Composable
fun rememberSystemTelemetry(fallbackTempC: Float): SystemTelemetry {
    val context = LocalContext.current
    var telemetry by remember { mutableStateOf(SystemTelemetry(socOrSkinTempC = fallbackTempC)) }

    LaunchedEffect(context, fallbackTempC) {
        while (isActive) {
            val sampled = withContext(Dispatchers.IO) {
                val am = context.getSystemService(ActivityManager::class.java)
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val ramPct = if (memInfo.totalMem > 0L) {
                    (((memInfo.totalMem - memInfo.availMem) * 100L) / memInfo.totalMem)
                        .toInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }

                val thermalTemp = readSocThermalCelsius() ?: fallbackTempC
                SystemTelemetry(
                    ramUsedPercent = ramPct,
                    socOrSkinTempC = thermalTemp,
                )
            }
            telemetry = sampled
            delay(2000L)
        }
    }

    return telemetry
}

private fun readSocThermalCelsius(): Float? {
    return runCatching {
        val binder = ServiceManager.getService(Context.THERMAL_SERVICE) ?: return null
        val thermalService = IThermalService.Stub.asInterface(binder) ?: return null
        val cpuTemps = thermalService.getCurrentTemperaturesWithType(Temperature.TYPE_CPU)
        val gpuTemps = thermalService.getCurrentTemperaturesWithType(Temperature.TYPE_GPU)
        val candidates = buildList {
            cpuTemps?.forEach { t ->
                if (t.value.isFinite() && t.value in 5f..125f) add(t.value)
            }
            gpuTemps?.forEach { t ->
                if (t.value.isFinite() && t.value in 5f..125f) add(t.value)
            }
        }
        if (candidates.isNotEmpty()) {
            candidates.maxOrNull()
        } else {
            val skinTemps = thermalService.getCurrentTemperaturesWithType(Temperature.TYPE_SKIN)
            skinTemps?.map { it.value }?.firstOrNull { it.isFinite() && it in 5f..120f }
        }
    }.getOrNull()
}

private val drawablePainterCache = LruCache<Int, Painter>(100)

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable?.hashCode()) {
        drawable?.let {
            val key = it.hashCode()
            drawablePainterCache.get(key) ?: run {
                val painter = it.toPainter()
                drawablePainterCache.put(key, painter)
                painter
            }
        } ?: ColorPainter(Color.Gray)
    }
}

fun Drawable.toPainter(): Painter {
    return BitmapPainter(this.toBitmap().asImageBitmap())
}

@Composable
fun QuickStartAppIcon(
    appInfo: AppInfo,
    modifier: Modifier = Modifier,
    onAppLaunched: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberExpressiveHaptics()
    val painter = rememberDrawablePainter(appInfo.icon)

    ExpressiveMorphContainer(
        active = false,
        inactivePreset = ExpressiveShapePreset.SQUIRCLE,
        activePreset = ExpressiveShapePreset.PILL,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        hapticOnClick = false,
        onClick = {
            haptics.performConfirm()
            launchAppInFreeformMode(context, appInfo.packageName)
            onAppLaunched()
        },
        modifier = modifier.size(44.dp),
    ) {
        Image(
            painter = painter,
            contentDescription = appInfo.name,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape),
        )
    }
}

fun launchAppInFreeformMode(context: Context, packageName: String) {
    try {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            val activityName = launchIntent.component?.className

            val freeformIntent = Intent("com.libremobileos.freeform.START_FREEFORM").apply {
                setPackage("com.libremobileos.freeform")
                putExtra("packageName", packageName)
                putExtra("activityName", activityName)
                putExtra("userId", UserHandle.myUserId())
            }

            context.sendBroadcast(freeformIntent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch in freeform", e)
    }
}

@Composable
fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    val batteryInfo = remember { mutableStateOf(BatteryInfo()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                    val tempTenths = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val tempCelsius = tempTenths / 10f

                    batteryInfo.value = BatteryInfo(
                        level = percentage,
                        temperatureC = tempCelsius,
                    )
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        context.registerReceiver(receiver, intentFilter)

        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempCelsius = tempTenths / 10f

            batteryInfo.value = BatteryInfo(
                level = percentage,
                temperatureC = tempCelsius,
            )
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryInfo.value
}

private fun getPercentage(value: Double, min: Float, max: Float): Double {
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

enum class GameMode(
    val displayName: String,
    val shortLabel: String,
    val icon: ImageVector,
) {
    Balanced("Balanced", "Balance", Icons.Filled.SportsEsports),
    PowerSave("Power Save", "Battery", Icons.Filled.EnergySavingsLeaf),
    Performance("Performance", "Boost", Icons.Filled.ElectricBolt),
}

fun GameMode.toSystemGameMode(): Int = when (this) {
    GameMode.Balanced -> GameManager.GAME_MODE_STANDARD
    GameMode.PowerSave -> GameManager.GAME_MODE_BATTERY
    GameMode.Performance -> GameManager.GAME_MODE_PERFORMANCE
}

fun fromSystemGameMode(value: Int): GameMode = when (value) {
    GameManager.GAME_MODE_PERFORMANCE -> GameMode.Performance
    GameManager.GAME_MODE_BATTERY -> GameMode.PowerSave
    else -> GameMode.Balanced
}

data class AppInfo(
    val name: String,
    val icon: Drawable,
    val packageName: String,
)

data class BatteryInfo(
    val level: Int = -1,
    val temperatureC: Float = 0f,
)
