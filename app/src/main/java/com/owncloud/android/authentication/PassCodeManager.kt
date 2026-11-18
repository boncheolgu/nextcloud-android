/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2022-2023 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.owncloud.android.authentication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import com.nextcloud.client.core.Clock
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.MainApp
import com.owncloud.android.ui.activity.PassCodeActivity
import com.owncloud.android.ui.activity.RequestCredentialsActivity
import com.owncloud.android.ui.activity.SettingsActivity
import com.owncloud.android.utils.DeviceCredentialUtils
import java.lang.ref.WeakReference

class PassCodeManager(private val preferences: AppPreferences, private val clock: Clock) {
    companion object {
        private val exemptOfPasscodeActivities = setOf(
            PassCodeActivity::class.java,
            RequestCredentialsActivity::class.java
        )
        const val PASSCODE_ACTIVITY = 9999

        fun setSecureFlag(activity: Activity, isSet: Boolean) {
            activity.window?.let { window ->
                if (isSet) {
                    println("flag added")
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    println("flag cleared")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    private var canAskPin = true
    private val startedActivities = mutableSetOf<Activity>()
    private var pausedActivity: WeakReference<Activity>? = null
    private var askPinWhenDeviceLocked = false

    private fun isExemptActivity(activity: Activity): Boolean = exemptOfPasscodeActivities.contains(activity.javaClass)

    fun onActivityResumed(activity: Activity): Boolean {
        val returningToPausedActivity = pausedActivity?.get() === activity
        pausedActivity = null
        if (isExemptActivity(activity)) {
            return false
        }
        if (returningToPausedActivity) {
            canAskPin = true
        }

        val passcodeRequested = passCodeShouldBeRequested()
        val credentialsRequested = deviceCredentialsShouldBeRequested(activity)
        val askedForPin = passcodeRequested || credentialsRequested
        getActivityRootView(activity)?.visibility = if (askedForPin) View.GONE else View.VISIBLE

        if (passcodeRequested) {
            requestPasscode(activity)
        } else if (credentialsRequested) {
            requestCredentials(activity)
        }
        if (askedForPin) {
            preferences.lockTimestamp = 0
        }

        return askedForPin
    }

    fun isLocked(activity: Activity): Boolean {
        var askedForPin = false

        if (!isExemptActivity(activity)) {
            val passcodeRequested = passCodeShouldBeRequested()
            val credentialsRequested = deviceCredentialsShouldBeRequested(activity)
            askedForPin = passcodeRequested || credentialsRequested
        }

        return askedForPin
    }

    private fun requestPasscode(activity: Activity) {
        val i = Intent(MainApp.getAppContext(), PassCodeActivity::class.java).apply {
            action = PassCodeActivity.ACTION_CHECK
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        activity.startActivityForResult(i, PASSCODE_ACTIVITY)
    }

    private fun requestCredentials(activity: Activity) {
        val i = Intent(MainApp.getAppContext(), RequestCredentialsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        activity.startActivityForResult(i, PASSCODE_ACTIVITY)
    }

    fun onActivityStarted(activity: Activity) {
        startedActivities.add(activity)
    }

    fun onActivityPaused(activity: Activity) {
        pausedActivity = WeakReference(activity)
    }

    fun onActivityStopped(activity: Activity) {
        startedActivities.remove(activity)
        if (startedActivities.isEmpty() && !activity.isChangingConfigurations) {
            canAskPin = true
        }

        val powerMgr = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if ((isPassCodeEnabled() || deviceCredentialsAreEnabled(activity)) && !powerMgr.isInteractive) {
            askPinWhenDeviceLocked = true
        }
    }

    fun updateLockTimestamp() {
        preferences.lockTimestamp = clock.millisSinceBoot
        canAskPin = false
        askPinWhenDeviceLocked = false
        pausedActivity = null
    }

    private fun shouldBeLocked(): Boolean = canAskPin || askPinWhenDeviceLocked

    @VisibleForTesting
    fun passCodeShouldBeRequested(): Boolean = shouldBeLocked() && isPassCodeEnabled()

    fun isPassCodeEnabled(): Boolean = SettingsActivity.LOCK_PASSCODE == preferences.lockPreference

    private fun deviceCredentialsShouldBeRequested(activity: Activity): Boolean =
        shouldBeLocked() && deviceCredentialsAreEnabled(activity)

    private fun deviceCredentialsAreEnabled(activity: Activity): Boolean =
        (preferences.lockPreference == SettingsActivity.LOCK_DEVICE_CREDENTIALS) &&
            DeviceCredentialUtils.areCredentialsAvailable(activity)

    private fun getActivityRootView(activity: Activity): View? = activity.window?.findViewById(android.R.id.content)
        ?: activity.window?.decorView?.findViewById(android.R.id.content)
}
