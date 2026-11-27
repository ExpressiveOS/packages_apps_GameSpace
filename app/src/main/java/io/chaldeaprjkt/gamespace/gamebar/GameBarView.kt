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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.chaldeaprjkt.gamespace.gamebar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.axion.compose.expressive.ExpressiveBlurSurface
import com.android.axion.compose.expressive.ExpressiveMorphContainer
import com.android.axion.compose.expressive.ExpressiveShapePreset
import com.android.axion.compose.expressive.rememberExpressiveHaptics
import com.android.axion.compose.expressive.rememberExpressiveReducedMotion
import com.android.axion.compose.theme.EmphasizedTabularStyle
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SwipeDetector
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import io.chaldeaprjkt.gamespace.R
import kotlin.math.abs

private const val PILL_WIDTH_DP = 60
private const val PILL_BUTTON_SIZE_DP = 48
private const val NOTCH_WIDTH_DP = 14
private const val NOTCH_HEIGHT_DP = 48
private const val NOTCH_TOUCH_WIDTH_DP = 48
private const val DRAG_THRESHOLD_DP = 12
private const val FPS_PILL_HEIGHT_DP = 32
private const val FPS_PILL_TOUCH_HEIGHT_DP = 48

@Composable
fun GameBarView(
    showFps: Boolean,
    fpsText: String,
    isLocked: Boolean,
    isIdle: Boolean,
    idleAlpha: Float,
    dockedOnLeft: Boolean,
    mapperEnabled: Boolean,
    onShowPanel: () -> Unit,
    onToggleLock: () -> Unit,
    onMapControls: () -> Unit,
    pillExpanded: Boolean,
    barTopPx: Int,
    onToggleFps: () -> Unit,
    onExpanded: () -> Unit,
    onCollapsed: () -> Unit,
    collapseRequestKey: Int,
    onDragStart: () -> Pair<Int, Int>,
    onDragUpdate: (Int, Int) -> Unit,
    onDragEnd: (Int, Int) -> Unit,
) {
    val reducedMotion = rememberExpressiveReducedMotion()
    val haptics = rememberExpressiveHaptics()
    val transitions = remember(reducedMotion) { buildGameBarTransitions(reducedMotion) }

    val state = rememberMutableSceneTransitionLayoutState(
        initialScene = GameBarScenes.Pill,
        transitions = transitions,
    )
    val scope = rememberCoroutineScope()
    val noSwipe = remember {
        object : SwipeDetector {
            override fun detectSwipe(change: PointerInputChange) = false
        }
    }

    val animatedPillAlpha by animateFloatAsState(
        targetValue = if (isIdle) idleAlpha else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            MaterialTheme.motionScheme.defaultEffectsSpec()
        },
        label = "pillAlpha",
    )

    LaunchedEffect(collapseRequestKey) {
        if (collapseRequestKey > 0) {
            state.setTargetScene(GameBarScenes.Pill, scope)
            onCollapsed()
        }
    }

    var startWindowX by remember { mutableIntStateOf(0) }
    var startWindowY by remember { mutableIntStateOf(0) }
    var accDragX by remember { mutableFloatStateOf(0f) }
    var accDragY by remember { mutableFloatStateOf(0f) }
    var repositioning by remember { mutableStateOf(false) }

    val pointerModifier = Modifier
        .pointerInput(dockedOnLeft) {
            val dragThresholdPx = DRAG_THRESHOLD_DP.dp.toPx()
            detectDragGestures(
                onDragStart = { _ ->
                    val pos = onDragStart()
                    startWindowX = pos.first
                    startWindowY = pos.second
                    accDragX = 0f
                    accDragY = 0f
                    repositioning = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accDragX += dragAmount.x
                    accDragY += dragAmount.y
                    if (!repositioning &&
                        (abs(accDragY) > dragThresholdPx || abs(accDragX) > dragThresholdPx)
                    ) {
                        repositioning = true
                        haptics.performGestureStart()
                    }
                    if (repositioning) {
                        onDragUpdate(
                            startWindowX + accDragX.toInt(),
                            startWindowY + accDragY.toInt(),
                        )
                    }
                },
                onDragEnd = {
                    if (repositioning) {
                        haptics.performSheetSnap()
                        onDragEnd(
                            startWindowX + accDragX.toInt(),
                            startWindowY + accDragY.toInt(),
                        )
                    } else {
                        onDragEnd(startWindowX, startWindowY)
                    }
                },
                onDragCancel = {
                    onDragEnd(startWindowX, startWindowY)
                },
            )
        }

    val density = LocalDensity.current
    val barTopDp = with(density) { barTopPx.toDp() }

    val sceneContent = @Composable {
        SceneTransitionLayout(
            state = state,
            swipeDetector = noSwipe,
        ) {
            scene(GameBarScenes.Pill) {
                PillTab(
                    dockedOnLeft = dockedOnLeft,
                    showFps = showFps,
                    fpsText = fpsText,
                    idleAlpha = animatedPillAlpha,
                    pointerModifier = pointerModifier,
                    onTap = {
                        haptics.performSheetSnap()
                        state.setTargetScene(GameBarScenes.VerticalPill, scope)
                        onExpanded()
                    },
                    modifier = Modifier.element(GameBarElements.PillContent),
                )
            }
            scene(GameBarScenes.VerticalPill) {
                VerticalPill(
                    isLocked = isLocked,
                    mapperEnabled = mapperEnabled,
                    dockedOnLeft = dockedOnLeft,
                    showFps = showFps,
                    onToggleFps = onToggleFps,
                    onShowPanel = onShowPanel,
                    onToggleLock = onToggleLock,
                    onMapControls = onMapControls,
                    modifier = Modifier.element(GameBarElements.VerticalPillContent),
                )
            }
        }
    }

    if (pillExpanded) {
        val dismissAlignment = if (dockedOnLeft) Alignment.TopStart else Alignment.TopEnd
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        state.setTargetScene(GameBarScenes.Pill, scope)
                        onCollapsed()
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = barTopDp),
                contentAlignment = dismissAlignment,
            ) {
                sceneContent()
            }
        }
    } else {
        sceneContent()
    }
}

@Composable
private fun PillTab(
    dockedOnLeft: Boolean,
    showFps: Boolean,
    fpsText: String,
    idleAlpha: Float,
    pointerModifier: Modifier,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collapsed HUD intentionally uses lightweight tonal scrim without live Kawase window blur
    // so 3D games incur zero GPU compositing overhead during active gameplay.
    if (showFps) {
        val fpsShape = if (dockedOnLeft) {
            RoundedCornerShape(
                topStart = 6.dp,
                bottomStart = 6.dp,
                topEnd = 1000.dp,
                bottomEnd = 1000.dp,
            )
        } else {
            RoundedCornerShape(
                topStart = 1000.dp,
                bottomStart = 1000.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
            )
        }
        val resolvedFps = fpsText.ifBlank { "--" }
        val fpsDescription = stringResource(R.string.cd_fps_overlay, resolvedFps)
        val openLabel = stringResource(R.string.cd_open_game_bar)
        Box(
            modifier = modifier
                .height(FPS_PILL_TOUCH_HEIGHT_DP.dp)
                .widthIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = fpsDescription
                    onClick(label = openLabel) {
                        onTap()
                        true
                    }
                }
                .then(pointerModifier)
                .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = if (dockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .height(FPS_PILL_HEIGHT_DP.dp)
                    .widthIn(min = 48.dp)
                    .alpha(idleAlpha)
                    .clip(fpsShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        shape = fpsShape,
                    )
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = resolvedFps,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = EmphasizedTabularStyle(MaterialTheme.typography.labelMediumEmphasized),
                    maxLines = 1,
                )
            }
        }
    } else {
        val notchShape = if (dockedOnLeft) {
            RoundedCornerShape(topEnd = 1000.dp, bottomEnd = 1000.dp)
        } else {
            RoundedCornerShape(topStart = 1000.dp, bottomStart = 1000.dp)
        }
        val openLabel = stringResource(R.string.cd_open_game_bar)
        Box(
            modifier = modifier
                .width(NOTCH_TOUCH_WIDTH_DP.dp)
                .height(NOTCH_HEIGHT_DP.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = openLabel
                    onClick(label = openLabel) {
                        onTap()
                        true
                    }
                }
                .then(pointerModifier)
                .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = if (dockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(NOTCH_WIDTH_DP.dp)
                    .height(NOTCH_HEIGHT_DP.dp)
                    .alpha(idleAlpha)
                    .clip(notchShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape = notchShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun VerticalPill(
    isLocked: Boolean,
    mapperEnabled: Boolean,
    dockedOnLeft: Boolean,
    showFps: Boolean,
    onToggleFps: () -> Unit,
    onShowPanel: () -> Unit,
    onToggleLock: () -> Unit,
    onMapControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberExpressiveHaptics()
    val pillShape = CircleShape

    ExpressiveBlurSurface(
        modifier = modifier
            .padding(
                start = if (dockedOnLeft) 10.dp else 0.dp,
                end = if (dockedOnLeft) 0.dp else 10.dp,
            )
            .width(PILL_WIDTH_DP.dp)
            .shadow(
                elevation = 8.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.36f),
            ),
        shape = pillShape,
        tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimAlpha = 0.88f,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuickRailActionButton(
                active = showFps,
                iconRes = R.drawable.ic_fps,
                contentDescription = stringResource(R.string.tile_fps_info),
                isToggle = true,
                onClick = {
                    haptics.performToggle(!showFps)
                    onToggleFps()
                },
            )
            QuickRailActionButton(
                active = true,
                iconRes = R.drawable.materialsymbols_ic_dashboard_rounded_filled,
                contentDescription = stringResource(R.string.settings_title),
                containerOverride = MaterialTheme.colorScheme.primaryContainer,
                contentOverride = MaterialTheme.colorScheme.onPrimaryContainer,
                isToggle = false,
                onClick = {
                    haptics.performSheetSnap()
                    onShowPanel()
                },
            )
            QuickRailActionButton(
                active = isLocked,
                iconRes = if (isLocked) {
                    R.drawable.materialsymbols_ic_lock_rounded_filled
                } else {
                    R.drawable.materialsymbols_ic_lock_open_rounded_filled
                },
                contentDescription = stringResource(R.string.tile_gesture_lock),
                isToggle = true,
                onClick = {
                    haptics.performToggle(!isLocked)
                    onToggleLock()
                },
            )
            if (mapperEnabled) {
                QuickRailActionButton(
                    active = false,
                    iconRes = R.drawable.materialsymbols_ic_sports_esports_rounded_filled,
                    contentDescription = stringResource(R.string.mapper_map_controls),
                    isToggle = false,
                    onClick = {
                        haptics.performConfirm()
                        onMapControls()
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickRailActionButton(
    active: Boolean,
    iconRes: Int,
    contentDescription: String?,
    isToggle: Boolean,
    onClick: () -> Unit,
    containerOverride: Color? = null,
    contentOverride: Color? = null,
) {
    val resolvedContainer = containerOverride ?: if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
    }
    val resolvedContent = contentOverride ?: if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ExpressiveMorphContainer(
        active = active,
        inactivePreset = ExpressiveShapePreset.SQUIRCLE,
        activePreset = ExpressiveShapePreset.PILL,
        containerColor = resolvedContainer,
        contentColor = resolvedContent,
        hapticOnClick = false,
        onClick = onClick,
        modifier = Modifier.size(PILL_BUTTON_SIZE_DP.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = resolvedContent,
            modifier = Modifier.size(20.dp),
        )
    }
}
