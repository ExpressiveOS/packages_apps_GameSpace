/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
 * Copyright (C) 2021 AOSP-Krypton Project
 * Copyright (C) 2022 Nameless-AOSP Project
 * Copyright (C) 2023 the risingOS Android Project
 * Copyright (C) 2025 AxionOS
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.chaldeaprjkt.gamespace.gamebar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioSystem
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.axion.compose.expressive.ExpressiveBlurSurface
import com.android.axion.compose.expressive.rememberExpressiveHaptics
import com.android.axion.compose.expressive.rememberExpressiveReducedMotion
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.android.axion.compose.theme.AxionTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.utils.dp as extDp
import javax.inject.Inject
import kotlinx.coroutines.delay

@ServiceScoped
class CallListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)!!
    private val telecomManager = context.getSystemService(TelecomManager::class.java)!!
    private val windowManager = context.getSystemService(WindowManager::class.java)!!

    private val callsMode get() = appSettings.callsMode
    private val callOverlayEnabled get() = appSettings.callOverlayEnabled

    private var previousAudioMode = audioManager.mode

    private var ringerOverlay: ComposeView? = null
    private var isOverlayShowing = false

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            val number = incomingNumber.orEmpty()
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> handleIncomingCall(number)
                TelephonyManager.CALL_STATE_OFFHOOK -> handleOffhookState()
                TelephonyManager.CALL_STATE_IDLE -> handleIdleState()
            }
        }
    }

    fun init() {
        previousAudioMode = audioManager.mode
        @Suppress("DEPRECATION")
        telephonyManager.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    fun destroy() {
        @Suppress("DEPRECATION")
        telephonyManager.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_NONE
        )
        dismissRingerOverlay(immediate = true)
    }

    private fun handleIncomingCall(incomingNumber: String) {
        val displayName = lookupContactName(incomingNumber).ifEmpty { incomingNumber }

        when (callsMode) {
            1 -> {
                telecomManager.acceptRingingCall()
                Toast.makeText(
                    context,
                    context.getString(R.string.in_game_calls_received_number, displayName),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            2 -> {
                telecomManager.endCall()
                Toast.makeText(
                    context,
                    context.getString(R.string.in_game_calls_rejected_number, displayName),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        if (callOverlayEnabled) {
            showRingerOverlay(incomingNumber)
        }
    }

    private fun handleOffhookState() {
        dismissRingerOverlay()

        if (callsMode == 0 || callsMode == 2) return

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (isHeadsetPluggedIn()) {
            audioManager.isSpeakerphoneOn = false
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_NONE)
        } else {
            audioManager.isSpeakerphoneOn = true
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_SPEAKER)
        }
    }

    private fun handleIdleState() {
        dismissRingerOverlay()

        if (callsMode == 0 || callsMode == 2) return

        audioManager.mode = previousAudioMode
        AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_NONE)
    }

    private fun isHeadsetPluggedIn(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun lookupContactName(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            } ?: ""
        } catch (_: SecurityException) {
            ""
        }
    }

    private fun showRingerOverlay(incomingNumber: String) {
        if (isOverlayShowing) return

        val sidebarX = appSettings.x

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (sidebarX < 0) {
                Gravity.TOP or Gravity.END
            } else {
                Gravity.TOP or Gravity.START
            }
            x = 1
            y = appSettings.y - 71.extDp
        }

        val callerPhoto = loadContactPhoto(context, incomingNumber)

        ringerOverlay = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        AxionTheme(applySystemBars = false) {
                            CallOverlay(
                                onAccept = {
                                    telecomManager.acceptRingingCall()
                                    handleOffhookState()
                                    dismissRingerOverlay()
                                },
                                onReject = {
                                    telecomManager.endCall()
                                    dismissRingerOverlay()
                                },
                                onDismiss = { dismissRingerOverlay() },
                                alignRight = sidebarX < 0,
                                onDismissAnimation = {
                                    delay(0)
                                },
                                callerPhoto = callerPhoto,
                            )
                        }
                    }
                }
            }
        }
        windowManager.addView(ringerOverlay, layoutParams)
        isOverlayShowing = true
    }

    private fun dismissRingerOverlay(immediate: Boolean = false) {
        val overlay = ringerOverlay ?: return
        try {
            windowManager.removeView(overlay)
        } catch (_: IllegalArgumentException) {
        }
        ringerOverlay = null
        isOverlayShowing = false
    }

    private fun loadContactPhoto(context: Context, phoneNumber: String): ImageBitmap? {
        if (phoneNumber.isBlank()) return null
        val resolver = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val photoUri = cursor.getString(0) ?: return null
                    resolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
        } catch (_: SecurityException) {
            null
        }
    }
}

@Composable
fun CallOverlay(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
    alignRight: Boolean,
    onDismissAnimation: suspend () -> Unit,
    callerPhoto: ImageBitmap? = null
) {
    val animatorsEnabled = !rememberExpressiveReducedMotion() && ValueAnimator.areAnimatorsEnabled()
    var isVisible by remember { mutableStateOf(!animatorsEnabled) }
    var isDismissing by remember { mutableStateOf(false) }
    var btnPressed by remember { mutableStateOf(false) }
    val haptics = rememberExpressiveHaptics()

    val scale by animateFloatAsState(
        targetValue = when {
            isDismissing -> 0f
            isVisible || !animatorsEnabled -> 1f
            else -> 0f
        },
        animationSpec = if (!animatorsEnabled) {
            snap()
        } else {
            MaterialTheme.motionScheme.fastSpatialSpec()
        },
        label = "shrink_scale"
    )

    val fadeAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0f else 1f,
        animationSpec = if (!animatorsEnabled) {
            snap()
        } else {
            MaterialTheme.motionScheme.fastEffectsSpec()
        },
        label = "fade"
    )

    val pulseAlpha = if (animatorsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animated
    } else {
        1f
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (btnPressed && animatorsEnabled) 0.90f else 1f,
        animationSpec = if (!animatorsEnabled) {
            snap()
        } else {
            MaterialTheme.motionScheme.fastSpatialSpec()
        },
        label = "button_press"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            isVisible = false
            if (animatorsEnabled) {
                delay(200)
            }
            onDismissAnimation()
        }
    }

    val containerShape = RoundedCornerShape(36.dp)
    val columnAlign = if (alignRight) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = fadeAlpha
            ),
        contentAlignment = if (alignRight) Alignment.TopEnd else Alignment.TopStart
    ) {
        ExpressiveBlurSurface(
            modifier = Modifier.shadow(8.dp, containerShape),
            shape = containerShape,
            tintColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = if (callerPhoto != null) columnAlign else Alignment.CenterHorizontally,
                modifier = Modifier.padding(12.dp),
            ) {
                IconButton(
                    onClick = {
                        haptics.performConfirm()
                        btnPressed = true
                        onReject()
                        isDismissing = true
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .scale(buttonScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_reject),
                        tint = MaterialTheme.colorScheme.onError.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alignRight && callerPhoto != null) {
                        CallerAvatarBadge(photo = callerPhoto)
                    }

                    IconButton(
                        onClick = {
                            haptics.performConfirm()
                            btnPressed = true
                            onAccept()
                            isDismissing = true
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .scale(buttonScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = stringResource(R.string.cd_accept),
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (!alignRight && callerPhoto != null) {
                        CallerAvatarBadge(photo = callerPhoto)
                    }
                }

                IconButton(
                    onClick = {
                        haptics.performStepTick()
                        isDismissing = true
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallerAvatarBadge(photo: ImageBitmap) {
    Image(
        bitmap = photo,
        contentDescription = stringResource(R.string.cd_caller_photo),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    )
}
