/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2023 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.owncloud.android.authentication

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import com.nextcloud.client.core.Clock
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.ui.activity.PassCodeActivity
import com.owncloud.android.ui.activity.SettingsActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PassCodeManagerTest {
    @MockK
    lateinit var appPreferences: AppPreferences

    @MockK
    lateinit var clockImpl: Clock

    lateinit var sut: PassCodeManager

    @Before
    fun before() {
        MockKAnnotations.init(this, relaxed = true)
        sut = PassCodeManager(appPreferences, clockImpl)
    }

    @Test
    fun testLocked() {
        every { appPreferences.lockPreference } returns SettingsActivity.LOCK_PASSCODE
        every { clockImpl.millisSinceBoot } returns 10000
        assertTrue("Passcode not requested", sut.passCodeShouldBeRequested())
    }

    @Test
    fun testPasscodeNotRequested_notEnabled() {
        every { appPreferences.lockPreference } returns ""
        every { clockImpl.millisSinceBoot } returns 10000
        assertFalse("Passcode requested but it shouldn't have been", sut.passCodeShouldBeRequested())
    }

    @Test
    fun testColdStartLocksEvenWithRecentPersistedTimestamp() {
        every { appPreferences.lockPreference } returns SettingsActivity.LOCK_PASSCODE
        every { appPreferences.lockTimestamp } returns 200
        every { clockImpl.millisSinceBoot } returns 210
        assertTrue("Background app must lock without a grace period", sut.passCodeShouldBeRequested())
    }

    @Test
    fun testUnlockedWhileVisible() {
        enablePasscodeAndUnlock()
        every { clockImpl.millisSinceBoot } returns 100000
        assertFalse(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testLocksImmediatelyAfterLastActivityStops() {
        val activity = activity()
        enablePasscodeAndUnlock()
        sut.onActivityStarted(activity)
        sut.onActivityStopped(activity)
        assertTrue(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testInternalActivityTransitionDoesNotLock() {
        val first = activity()
        val second = activity()
        enablePasscodeAndUnlock()
        sut.onActivityStarted(first)
        sut.onActivityStarted(second)
        sut.onActivityStopped(first)
        assertFalse(sut.passCodeShouldBeRequested())
        sut.onActivityStopped(second)
        assertTrue(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testConfigurationChangeDoesNotLock() {
        val previous = activity(changingConfigurations = true)
        val recreated = activity()
        enablePasscodeAndUnlock()
        sut.onActivityStarted(previous)
        sut.onActivityStopped(previous)
        sut.onActivityStarted(recreated)
        assertFalse(sut.passCodeShouldBeRequested())
        sut.onActivityStopped(recreated)
        assertTrue(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testScreenOffLocksEvenDuringConfigurationChange() {
        val activity = activity(changingConfigurations = true, interactive = false)
        enablePasscodeAndUnlock()
        sut.onActivityStarted(activity)
        sut.onActivityStopped(activity)
        assertTrue(sut.passCodeShouldBeRequested())
        sut.updateLockTimestamp()
        assertFalse(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testAuthenticationActivityTransitionDoesNotRelock() {
        val content = activity()
        val authentication = mockk<PassCodeActivity>(relaxed = true)
        val powerManager = mockk<PowerManager>()
        every { powerManager.isInteractive } returns true
        every { authentication.getSystemService(Context.POWER_SERVICE) } returns powerManager
        enablePasscodeAndUnlock()
        sut.onActivityStarted(content)
        sut.onActivityStarted(authentication)
        sut.onActivityStopped(content)
        sut.updateLockTimestamp()
        sut.onActivityStarted(content)
        sut.onActivityStopped(authentication)
        assertFalse(sut.passCodeShouldBeRequested())
    }

    @Test
    fun testLockDisabledAfterBackground() {
        val activity = activity()
        enablePasscodeAndUnlock()
        sut.onActivityStarted(activity)
        sut.onActivityStopped(activity)
        every { appPreferences.lockPreference } returns ""
        assertFalse(sut.passCodeShouldBeRequested())
    }

    private fun enablePasscodeAndUnlock() {
        every { appPreferences.lockPreference } returns SettingsActivity.LOCK_PASSCODE
        every { clockImpl.millisSinceBoot } returns 200
        sut.updateLockTimestamp()
    }

    private fun activity(changingConfigurations: Boolean = false, interactive: Boolean = true): Activity {
        val activity = mockk<Activity>(relaxed = true)
        val powerManager = mockk<PowerManager>()
        every { activity.isChangingConfigurations } returns changingConfigurations
        every { powerManager.isInteractive } returns interactive
        every { activity.getSystemService(Context.POWER_SERVICE) } returns powerManager
        return activity
    }
}
