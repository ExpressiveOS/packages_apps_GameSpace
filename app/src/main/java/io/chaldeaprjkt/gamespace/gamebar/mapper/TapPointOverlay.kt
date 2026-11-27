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

package io.chaldeaprjkt.gamespace.gamebar.mapper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.axion.compose.expressive.ExpressiveBlurSurface
import com.android.axion.compose.expressive.ExpressiveFloatingToolbar
import com.android.axion.compose.expressive.ExpressiveToolbarIconButton
import com.android.axion.compose.expressive.ExpressiveToolbarPrimaryPill
import com.android.axion.compose.expressive.rememberExpressiveHaptics
import com.android.axion.compose.expressive.rememberExpressiveReducedMotion
import com.android.axion.compose.theme.EmphasizedTabularStyle
import io.chaldeaprjkt.gamespace.R
import kotlin.math.roundToInt

@Composable
fun TapPointOverlay(
    mappings: List<TapMapping>,
    keyCaptureEvent: KeyCaptureEvent?,
    awaitingKey: Boolean,
    onSave: (List<TapMapping>) -> Unit,
    onCancel: () -> Unit,
    onRequestKeyCapture: (pointId: Int) -> Unit,
    onKeyCaptureConsumed: () -> Unit,
) {
    val points = remember { mutableStateListOf<EditablePoint>() }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var nextId by remember { mutableIntStateOf((mappings.maxOfOrNull { it.id } ?: -1) + 1) }
    var selectedPointId by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current.density
    val reducedMotion = rememberExpressiveReducedMotion()
    val haptics = rememberExpressiveHaptics()

    if (points.isEmpty() && mappings.isNotEmpty() && containerSize != IntSize.Zero) {
        mappings.forEach { m ->
            points.add(
                EditablePoint(
                    id = m.id,
                    keyCode = m.keyCode,
                    label = m.label,
                    xPx = m.xPercent * containerSize.width,
                    yPx = m.yPercent * containerSize.height,
                )
            )
        }
        nextId = (mappings.maxOfOrNull { it.id } ?: -1) + 1
    }

    LaunchedEffect(keyCaptureEvent) {
        val evt = keyCaptureEvent ?: return@LaunchedEffect
        val idx = points.indexOfFirst { it.id == evt.pointId }
        if (idx != -1) {
            points[idx] = points[idx].copy(
                keyCode = evt.keyCode,
                label = TapMapping.keyCodeToLabel(evt.keyCode),
            )
            haptics.performConfirm()
        }
        onKeyCaptureConsumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .onSizeChanged { containerSize = it },
    ) {
        points.forEachIndexed { index, point ->
            val isSelected = point.id == selectedPointId
            val pointScale by animateFloatAsState(
                targetValue = if (isSelected && !reducedMotion) 1.14f else 1f,
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    MaterialTheme.motionScheme.fastSpatialSpec()
                },
                label = "point_scale",
            )

            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
            }
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.xPx - POINT_RADIUS_PX * density).roundToInt(),
                            (point.yPx - POINT_RADIUS_PX * density).roundToInt(),
                        )
                    }
                    .size(POINT_SIZE_DP.dp)
                    .scale(pointScale)
                    .pointerInput(point.id) {
                        detectDragGestures(
                            onDragStart = {
                                selectedPointId = point.id
                                haptics.performGestureStart()
                            },
                            onDragEnd = {
                                haptics.performSheetSnap()
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val currentIdx = points.indexOfFirst { it.id == point.id }
                            if (currentIdx != -1) {
                                val updated = points[currentIdx].copy(
                                    xPx = (points[currentIdx].xPx + dragAmount.x)
                                        .coerceIn(0f, containerSize.width.toFloat()),
                                    yPx = (points[currentIdx].yPx + dragAmount.y)
                                        .coerceIn(0f, containerSize.height.toFloat()),
                                )
                                points[currentIdx] = updated
                            }
                        }
                    }
                    .pointerInput(point.id) {
                        detectTapGestures(
                            onTap = {
                                haptics.performStepTick()
                                selectedPointId = point.id
                                onRequestKeyCapture(point.id)
                            },
                            onLongPress = {
                                haptics.performLongPress()
                                val currentIdx = points.indexOfFirst { it.id == point.id }
                                if (currentIdx != -1) {
                                    points.removeAt(currentIdx)
                                    if (selectedPointId == point.id) selectedPointId = -1
                                }
                            },
                        )
                    }
                    .clip(CircleShape)
                    .background(color = containerColor)
                    .border(
                        width = 2.dp,
                        color = borderColor,
                        shape = CircleShape,
                    ),
            ) {
                Text(
                    text = if (point.keyCode == 0) "?" else point.label,
                    color = textColor,
                    style = EmphasizedTabularStyle(MaterialTheme.typography.labelMediumEmphasized),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        if (reducedMotion) {
            if (awaitingKey) {
                AwaitingKeyBanner(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            AnimatedVisibility(
                visible = awaitingKey,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                AwaitingKeyBanner()
            }
        }

        ExpressiveFloatingToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            frosted = true,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            ExpressiveToolbarIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.mapper_add_point),
                selected = true,
                onClick = {
                    val cx = containerSize.width / 2f
                    val cy = containerSize.height / 2f
                    val id = nextId++
                    points.add(EditablePoint(id = id, xPx = cx, yPx = cy))
                    selectedPointId = id
                    onRequestKeyCapture(id)
                },
            )

            if (selectedPointId != -1) {
                ExpressiveToolbarIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.mapper_delete_point),
                    onClick = {
                        points.removeAll { it.id == selectedPointId }
                        selectedPointId = -1
                    },
                )
            }

            ExpressiveToolbarIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(android.R.string.cancel),
                onClick = onCancel,
            )

            ExpressiveToolbarPrimaryPill(
                label = stringResource(R.string.save),
                icon = Icons.Rounded.Check,
                onClick = {
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        val result = points.map { p ->
                            TapMapping(
                                id = p.id,
                                keyCode = p.keyCode,
                                xPercent = p.xPx / containerSize.width,
                                yPercent = p.yPx / containerSize.height,
                                label = p.label,
                            )
                        }
                        onSave(result)
                    }
                },
            )
        }
    }
}

@Composable
private fun AwaitingKeyBanner(modifier: Modifier = Modifier) {
    ExpressiveBlurSurface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimAlpha = 0.90f,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.mapper_press_button),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
        )
    }
}

data class EditablePoint(
    val id: Int,
    val keyCode: Int = 0,
    val label: String = "?",
    val xPx: Float = 0f,
    val yPx: Float = 0f,
)

private const val POINT_SIZE_DP = 48
private const val POINT_RADIUS_PX = POINT_SIZE_DP / 2f
