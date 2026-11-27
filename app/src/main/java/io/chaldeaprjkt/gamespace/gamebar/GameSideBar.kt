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

import android.app.ActivityTaskManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.window.TaskFpsCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import com.android.axion.compose.expressive.isExpressiveReducedMotionEnabled
import com.android.axion.compose.expressive.isExpressiveWindowBlurSupported
import com.android.axion.compose.expressive.rememberExpressiveReducedMotion
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.android.axion.compose.theme.AxionTheme
import com.android.axion.platform.AxPlatformClient
import io.chaldeaprjkt.gamespace.BuildFlags
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.mapper.MapperController
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.utils.statusbarHeight
import java.math.RoundingMode
import java.text.DecimalFormat

class GameSidebar(
    private val context: Context,
    private val wm: WindowManager,
    private val handler: Handler,
    private val appSettings: AppSettings,
    private val screenUtils: ScreenUtils,
    private val danmakuService: DanmakuService,
    private val brightnessInteractor: BrightnessInteractor,
    private val fpsInteractor: FpsInteractor,
    private val gameModeUtils: GameModeUtils,
    private val settings: SystemSettings,
    private val tileRepository: TileRepository,
    private val platform: AxPlatformClient,
    private val mapperController: MapperController,
) {
    private val gameBarLayoutParam = createGameBarLayoutParam()
    private val panelLayoutParam = createPanelLayoutParam()

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0
    private var shouldClose = false
    private var panelShowing = false
    private var fpsCallbackRegistered = false

    private lateinit var gameBarView: ComposeView
    private var panelView: View? = null

    private val firstPaint = Runnable { initActions() }

    private var circleOnLeft = false
    private val dockedOnLeftState = mutableStateOf(false)

    private val noOpBackDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        private val reg = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = reg
    }

    private val panelDismissing = mutableStateOf(false)

    private val showFpsState = mutableStateOf(false)
    private val fpsTextState = mutableStateOf("")
    private val isLockedState = mutableStateOf(false)
    private val isIdleState = mutableStateOf(true)
    private val collapseRequestState = mutableIntStateOf(0)
    private var pillExpanded = false
    private val pillExpandedState = mutableStateOf(false)
    private val barTopState = mutableIntStateOf(0)
    private val cutoutLeftPxState = mutableIntStateOf(0)
    private val cutoutRightPxState = mutableIntStateOf(0)
    private val sessionStartElapsedMsState = mutableLongStateOf(SystemClock.elapsedRealtime())
    private val taskManager by lazy { ActivityTaskManager.getService() }

    private val taskFpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            if (::gameBarView.isInitialized && fps.isFinite() && fps >= 0f) {
                val formatted = DecimalFormat("#").apply {
                    roundingMode = RoundingMode.HALF_EVEN
                }.format(fps)
                handler.post { fpsTextState.value = formatted }
            }
        }
    }

    private val recordingListener = object : AxPlatformClient.Listener() {
        override fun onStateChanged(key: String, state: Bundle) {}
    }

    fun onCreate() {
        tileRepository.onDismissPanelRequested = {
            handler.post { requestDismissPanel() }
        }
        tileRepository.onEnterMapperRequested = {
            handler.post { enterMapperEdit() }
        }
        tileRepository.onFpsVisibilityChanged = { visible ->
            handler.post {
                showFpsState.value = visible
                updateFpsTracking()
            }
        }
        tileRepository.onGestureLockChanged = { locked ->
            handler.post { setGestureLock(locked) }
        }

        gameBarView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides noOpBackDispatcherOwner,
                        ) {
                            AxionTheme(
                                darkTheme = true,
                                applySystemBars = false,
                            ) {
                                GameBarView(
                                    showFps = showFpsState.value,
                                    fpsText = fpsTextState.value,
                                    isLocked = isLockedState.value,
                                    isIdle = isIdleState.value,
                                    idleAlpha = (appSettings.iconIdleAlpha / 100f).coerceAtLeast(0.05f),
                                    dockedOnLeft = dockedOnLeftState.value,
                                    mapperEnabled = BuildFlags.MAPPER_ENABLED,
                                    onShowPanel = { handler.post { showPanel() } },
                                    onMapControls = { handler.post { enterMapperEdit() } },
                                    onToggleLock = {
                                        val next = !isLockedState.value
                                        appSettings.lockGesture = next
                                        setGestureLock(next)
                                        scheduleIdle()
                                    },
                                    onToggleFps = {
                                        val next = !showFpsState.value
                                        showFpsState.value = next
                                        appSettings.showFps = next
                                        tileRepository.syncFpsInfoState(next)
                                        updateFpsTracking()
                                        scheduleIdle()
                                    },
                                    pillExpanded = pillExpandedState.value,
                                    barTopPx = barTopState.intValue,
                                    onExpanded = { handler.post { setPillExpanded(true) } },
                                    onCollapsed = {
                                        handler.post { setPillExpanded(false) }
                                        scheduleIdle()
                                    },
                                    collapseRequestKey = collapseRequestState.intValue,
                                    onDragStart = {
                                        val loc = IntArray(2)
                                        gameBarView.getLocationOnScreen(loc)
                                        gameBarLayoutParam.gravity = Gravity.TOP or Gravity.START
                                        gameBarLayoutParam.x = loc[0]
                                        gameBarLayoutParam.y = loc[1]
                                        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                        Pair(loc[0], loc[1])
                                    },
                                    onDragUpdate = { x, y ->
                                        gameBarLayoutParam.x = x
                                        gameBarLayoutParam.y = y
                                        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                    },
                                    onDragEnd = { x, y ->
                                        circleOnLeft = x < halfWidth
                                        dockedOnLeftState.value = circleOnLeft
                                        appSettings.x = if (circleOnLeft) -1 else 1
                                        appSettings.y = y
                                        dockGameBar()
                                        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                        scheduleIdle()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.setSystemGestureExclusionRects(
                    listOf(Rect(0, 0, v.width, v.height))
                )
            }
        }

        updateScreenMetrics()
        danmakuService.init()
    }

    fun onGameStart(packageName: String) {
        shouldClose = false
        sessionStartElapsedMsState.longValue = SystemClock.elapsedRealtime()
        if (BuildFlags.MAPPER_ENABLED) {
            mapperController.onGameStart(packageName)
        }
        platform.addListener(recordingListener)
        handler.post {
            if (!::gameBarView.isInitialized) return@post
            runCatching {
                dockGameBar()
                wm.addView(gameBarView, gameBarLayoutParam)
                gameBarView.visibility = View.INVISIBLE
                gameBarView.alpha = 0f
                handler.postDelayed(firstPaint, 500)
            }
        }
    }

    fun onGameLeave() {
        mapperController.onGameLeave()
        platform.removeListener(recordingListener)
        stopFpsTracking()
        shouldClose = true
        setGestureLock(false)
        handler.removeCallbacksAndMessages(null)
        forceRemovePanel()
        runCatching { wm.removeViewImmediate(gameBarView) }
    }

    fun setGestureLock(value: Boolean) {
        isLockedState.value = value
        tileRepository.syncGestureLockState(value)
        Settings.Secure.putIntForUser(
            context.contentResolver,
            "ax_gaming_gesture_lock",
            if (value) 1 else 0,
            UserHandle.USER_CURRENT
        )
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        mapperController.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        forceRemovePanel()
        if (gameBarView.visibility != View.VISIBLE) {
            handler.removeCallbacks(firstPaint)
            handler.postDelayed({ firstPaint.run() }, 100)
        } else {
            dockGameBar()
            runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
        }
        danmakuService.updateConfiguration(newConfig)
    }

    private fun showPanel() {
        if (panelShowing) return
        collapseVerticalPill()
        panelShowing = true
        panelDismissing.value = false
        handler.removeCallbacks(idleRunnable)
        isIdleState.value = false

        tileRepository.refreshPlatformStates()
        fpsInteractor.start()
        brightnessInteractor.start()

        // Apply Kawase cross-window blur behind the expanded dashboard ONLY while open,
        // keeping zero blur / zero GPU compositing bloat during active gameplay.
        val canBlur = isExpressiveWindowBlurSupported(context)
        panelLayoutParam.setBlurBehindRadius(if (canBlur) PANEL_BLUR_BEHIND_RADIUS_PX else 0)
        panelLayoutParam.flags = if (canBlur) {
            panelLayoutParam.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        } else {
            panelLayoutParam.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        }

        val pv = createPanelView()
        panelView = pv

        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            Process.setThreadGroupAndCpuset(Process.myTid(), Process.THREAD_GROUP_SYSTEM)
            Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_SYSTEM)
            wm.addView(pv, panelLayoutParam)
            pv.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
            gameBarView.visibility = View.GONE
        } catch (_: Exception) {
            brightnessInteractor.dispose()
            fpsInteractor.dispose()
            panelShowing = false
        }
    }

    private fun requestDismissPanel() {
        panelDismissing.value = true
    }

    private fun removePanelAndRestoreCircle() {
        if (!panelShowing) return
        panelShowing = false

        brightnessInteractor.dispose()
        fpsInteractor.dispose()

        panelView?.let { pv ->
            runCatching { wm.removeViewImmediate(pv) }
            panelView = null
        }
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        }

        if (!shouldClose) {
            gameBarView.visibility = View.VISIBLE
            scheduleIdle()
        }
    }

    private fun forceRemovePanel() {
        if (!panelShowing) return
        panelShowing = false
        panelDismissing.value = true

        brightnessInteractor.dispose()
        fpsInteractor.dispose()

        panelView?.let { pv ->
            runCatching { wm.removeViewImmediate(pv) }
            panelView = null
        }
        if (::gameBarView.isInitialized) gameBarView.visibility = View.VISIBLE
    }

    private fun createPanelView(): ComposeView {
        return ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        AxionTheme(
                            darkTheme = true,
                            applySystemBars = false,
                        ) {
                            PanelOverlayHost()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelOverlayHost() {
        val apps = remember { getQuickStartApps(context) }
        val dismissing by panelDismissing
        val reducedMotion = rememberExpressiveReducedMotion()

        var entered by remember { mutableStateOf(reducedMotion) }
        LaunchedEffect(Unit) { entered = true }

        LaunchedEffect(dismissing, reducedMotion) {
            if (dismissing && reducedMotion) {
                removePanelAndRestoreCircle()
            }
        }

        val show = entered && !dismissing
        val slideSign = if (circleOnLeft) -1f else 1f

        val spatialSpec = if (reducedMotion) {
            snap()
        } else {
            MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        }
        val effectsSpec = if (reducedMotion) {
            snap()
        } else {
            MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        }

        val slideOffset by animateFloatAsState(
            targetValue = if (show) 0f else slideSign * 360f,
            animationSpec = spatialSpec,
            label = "panel_slide",
            finishedListener = {
                if (dismissing) handler.post { removePanelAndRestoreCircle() }
            },
        )

        val panelAlpha by animateFloatAsState(
            targetValue = if (show) 1f else 0f,
            animationSpec = effectsSpec,
            label = "panel_alpha",
        )

        val panelScale by animateFloatAsState(
            targetValue = if (show) 1f else 0.90f,
            animationSpec = spatialSpec,
            label = "panel_scale",
        )

        val density = LocalDensity.current
        val barTopPx = gameBarLayoutParam.y
        val barTopDp = with(density) { barTopPx.toDp() }
        val navBottomDp = with(density) {
            context.resources.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height
            ).toDp()
        }
        val screenHeightDp = with(density) { safeHeight.toDp() }
        val spaceBelow = (screenHeightDp - barTopDp - navBottomDp - 16.dp).coerceAtLeast(0.dp)
        val spaceAbove = (barTopDp - navBottomDp - 16.dp).coerceAtLeast(0.dp)
        val bottomAligned = spaceBelow < 260.dp && spaceAbove > spaceBelow
        val panelMaxHeight = (if (bottomAligned) spaceAbove else spaceBelow).coerceIn(260.dp, 560.dp)
        val alignment = when {
            bottomAligned && circleOnLeft -> Alignment.BottomStart
            bottomAligned && !circleOnLeft -> Alignment.BottomEnd
            !bottomAligned && circleOnLeft -> Alignment.TopStart
            else -> Alignment.TopEnd
        }
        val transformOriginX = if (circleOnLeft) 0f else 1f
        val transformOriginY = if (bottomAligned) 1f else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f * panelAlpha.coerceIn(0f, 1f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (!dismissing) requestDismissPanel() },
            contentAlignment = alignment,
        ) {
            val cutoutStartDp = with(density) { cutoutLeftPxState.intValue.toDp() }
            val cutoutEndDp = with(density) { cutoutRightPxState.intValue.toDp() }
            val edgePadding = if (bottomAligned) navBottomDp + 8.dp else barTopDp
            Box(
                modifier = Modifier
                    .padding(
                        start = 12.dp + cutoutStartDp,
                        end = 12.dp + cutoutEndDp,
                        top = if (!bottomAligned) edgePadding else 0.dp,
                        bottom = if (bottomAligned) edgePadding else 0.dp,
                    )
                    .graphicsLayer {
                        translationX = slideOffset
                        alpha = panelAlpha
                        scaleX = panelScale
                        scaleY = panelScale
                        transformOrigin = TransformOrigin(transformOriginX, transformOriginY)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
            ) {
                GamePanelCard(
                    interactor = brightnessInteractor,
                    fpsInteractor = fpsInteractor,
                    apps = apps,
                    gameModeUtils = gameModeUtils,
                    systemSettings = settings,
                    tileRepository = tileRepository,
                    maxHeight = panelMaxHeight,
                    sessionStartElapsedMs = sessionStartElapsedMsState.longValue,
                    liveFpsText = fpsTextState.value,
                    onDismissPanel = { requestDismissPanel() },
                )
            }
        }
    }

    private fun setPillExpanded(expanded: Boolean) {
        if (pillExpanded == expanded) return
        pillExpanded = expanded
        pillExpandedState.value = expanded
        if (expanded) {
            barTopState.intValue = gameBarLayoutParam.y
            gameBarLayoutParam.width = WindowManager.LayoutParams.MATCH_PARENT
            gameBarLayoutParam.height = WindowManager.LayoutParams.MATCH_PARENT
            gameBarLayoutParam.flags = gameBarLayoutParam.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        } else {
            gameBarLayoutParam.width = WindowManager.LayoutParams.WRAP_CONTENT
            gameBarLayoutParam.height = WindowManager.LayoutParams.WRAP_CONTENT
            gameBarLayoutParam.flags = gameBarLayoutParam.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            dockGameBar()
        }
        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
    }

    private fun collapseVerticalPill() {
        if (!pillExpanded) return
        setPillExpanded(false)
        collapseRequestState.intValue = collapseRequestState.intValue + 1
    }

    private fun updateFpsTracking() {
        if (showFpsState.value) {
            if (!fpsCallbackRegistered) {
                taskManager?.focusedRootTaskInfo?.taskId?.let {
                    runCatching {
                        wm.registerTaskFpsCallback(it, Runnable::run, taskFpsCallback)
                        fpsCallbackRegistered = true
                    }
                }
            }
        } else {
            stopFpsTracking()
        }
    }

    private fun stopFpsTracking() {
        if (!fpsCallbackRegistered) return
        fpsCallbackRegistered = false
        runCatching { wm.unregisterTaskFpsCallback(taskFpsCallback) }
    }

    private fun updateScreenMetrics() {
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        val cutout = metrics.windowInsets.displayCutout
        cutoutLeftPxState.intValue = cutout?.safeInsetLeft ?: 0
        cutoutRightPxState.intValue = cutout?.safeInsetRight ?: 0
        val topCutout = cutout?.safeInsetTop ?: 0
        halfWidth = bounds.width() / 2
        safeArea = maxOf(context.statusbarHeight, topCutout) +
            (4 * context.resources.displayMetrics.density).toInt()
        safeHeight = bounds.height() - safeArea
    }

    private fun initActions() {
        if (shouldClose) return
        val reducedMotion = isExpressiveReducedMotionEnabled(context)
        gameBarView.visibility = View.VISIBLE
        if (reducedMotion) {
            gameBarView.alpha = 1f
        } else {
            gameBarView.animate().alpha(1f).setDuration(250).start()
        }
        circleOnLeft = appSettings.x < 0
        dockedOnLeftState.value = circleOnLeft
        gameBarLayoutParam.y = appSettings.y
        dockGameBar()
        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
        showFpsState.value = appSettings.showFps
        tileRepository.syncFpsInfoState(appSettings.showFps)
        updateFpsTracking()
        setGestureLock(appSettings.lockGesture)
        scheduleIdle()
    }

    private fun dockGameBar() {
        gameBarLayoutParam.gravity = Gravity.TOP or (if (circleOnLeft) Gravity.START else Gravity.END)
        gameBarLayoutParam.x = if (circleOnLeft) cutoutLeftPxState.intValue else cutoutRightPxState.intValue
        gameBarLayoutParam.y = gameBarLayoutParam.y.coerceIn(safeArea, safeHeight)
    }

    private val idleRunnable = Runnable {
        isIdleState.value = true
    }

    private fun enterMapperEdit() {
        val reducedMotion = isExpressiveReducedMotionEnabled(context)
        forceRemovePanel()
        collapseVerticalPill()
        if (reducedMotion) {
            gameBarView.alpha = 0f
            gameBarView.visibility = View.GONE
        } else {
            gameBarView.animate().alpha(0f).setDuration(150).withEndAction {
                gameBarView.visibility = View.GONE
            }.start()
        }
        mapperController.onEditStarted = null
        mapperController.onEditFinished = {
            handler.post {
                gameBarView.visibility = View.VISIBLE
                if (isExpressiveReducedMotionEnabled(context)) {
                    gameBarView.alpha = 1f
                } else {
                    gameBarView.animate().alpha(1f).setDuration(200).start()
                }
                scheduleIdle()
            }
        }
        mapperController.enterEditMode()
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        isIdleState.value = false
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    private fun getQuickStartApps(context: Context): List<AppInfo> {
        val appList = mutableListOf<AppInfo>()
        val packageManager = context.packageManager
        if (!appSettings.quickStartApps.isNullOrEmpty()) {
            val packages = appSettings.quickStartApps.split(",")
            for (pkg in packages) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    appList.add(AppInfo(name = appName, icon = icon, packageName = pkg))
                } catch (_: Exception) {}
            }
        }
        return appList
    }

    private fun createGameBarLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
        gravity = Gravity.TOP or Gravity.END
    }

    private fun createPanelLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 3000L
        private const val PANEL_BLUR_BEHIND_RADIUS_PX = 28
    }
}
