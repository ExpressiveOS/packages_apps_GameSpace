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
package io.chaldeaprjkt.gamespace.gamebar.fps

import android.app.ActivityTaskManager
import android.content.Context
import android.view.WindowManager
import android.window.TaskFpsCallback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Singleton
class FpsInteractor @Inject constructor(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val taskManager = ActivityTaskManager.getService()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _fpsHistory = MutableStateFlow<List<Float>>(emptyList())
    val fpsHistory: StateFlow<List<Float>> get() = _fpsHistory

    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> get() = _currentFps

    private val _dynamicMaxFps = MutableStateFlow(60f)
    val dynamicMaxFps: StateFlow<Float> get() = _dynamicMaxFps

    val maxRefreshRate: Float by lazy {
        wm.defaultDisplay?.mode?.refreshRate ?: 60f
    }

    private val historySize = 100
    private val buffer = ArrayDeque<Float>(historySize)

    private val fpsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    private var registered = false

    private val fpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            if (fps.isFinite() && fps >= 0f) {
                _currentFps.value = fps
                fpsFlow.tryEmit(fps)
            }
        }
    }

    init {
        coroutineScope.launch {
            fpsFlow
                .sample(1000)
                .map { fps ->
                    val smoothed = (buffer.lastOrNull() ?: fps) * 0.75f + fps * 0.25f
                    if (buffer.size >= historySize) buffer.removeFirst()
                    buffer.addLast(smoothed)
                    val dynamicMax = buffer.maxOrNull()?.coerceAtLeast(15f) ?: 60f
                    smoothed to dynamicMax
                }
                .flowOn(Dispatchers.Default)
                .collect { (_, dynamicMax) ->
                    _fpsHistory.value = buffer.toList()
                    _dynamicMaxFps.value = dynamicMax
                }
        }
    }

    fun start() {
        if (registered) return
        val taskId = taskManager?.focusedRootTaskInfo?.taskId ?: return
        runCatching {
            wm.registerTaskFpsCallback(taskId, Runnable::run, fpsCallback)
            registered = true
        }
    }

    fun dispose() {
        if (!registered) return
        registered = false
        runCatching {
            wm.unregisterTaskFpsCallback(fpsCallback)
        }
    }
}
