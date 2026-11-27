/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.chaldeaprjkt.gamespace.preferences.appselector

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.chaldeaprjkt.gamespace.R

internal class AppSelectorStatusPreference(
    context: Context,
    private val loading: Boolean,
    private val message: CharSequence,
) : Preference(context) {

    init {
        layoutResource = R.layout.preference_app_selector_status
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val progress = holder.findViewById(R.id.app_selector_status_progress) as? ProgressBar
        val messageView = holder.findViewById(R.id.app_selector_status_message) as? TextView
        progress?.visibility = if (loading) View.VISIBLE else View.GONE
        messageView?.text = message
    }
}
