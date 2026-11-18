/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.authentication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.nextcloud.client.core.Clock
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.MainApp
import com.owncloud.android.ui.activity.PassCodeActivity
import com.owncloud.android.ui.activity.SettingsActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlobalPassCodeProtectionCallbacksTest {
    private lateinit var manager: PassCodeManager
    private lateinit var callbacks: GlobalPassCodeProtectionCallbacks
    private var timestamp = 200L

    @Before
    fun setUp() {
        mockkObject(MainApp)
        every { MainApp.getAppContext() } returns mockk(relaxed = true)
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.lockPreference } returns SettingsActivity.LOCK_PASSCODE
        every { preferences.lockTimestamp } answers { timestamp }
        every { preferences.lockTimestamp = any() } answers { timestamp = firstArg() }
        val clock = mockk<Clock>()
        every { clock.millisSinceBoot } returns 200L
        manager = PassCodeManager(preferences, clock)
        callbacks = GlobalPassCodeProtectionCallbacks(mockk(relaxed = true), manager)
        manager.updateLockTimestamp()
    }

    @After
    fun tearDown() {
        unmockkObject(MainApp)
    }

    @Test
    fun returningBeforeOnStopRequestsAuthentication() {
        val content = activity()
        callbacks.onActivityStarted(content)
        callbacks.onActivityPaused(content)
        callbacks.onActivityResumed(content)

        verify(exactly = 1) { content.startActivityForResult(any<Intent>(), PassCodeManager.PASSCODE_ACTIVITY) }
        assertEquals(0L, timestamp)
    }

    @Test
    fun duplicateResumeDoesNotRequestAuthentication() {
        val content = activity()
        callbacks.onActivityStarted(content)
        callbacks.onActivityResumed(content)
        callbacks.onActivityResumed(content)

        verify(exactly = 0) { content.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun internalNavigationAndBackDoNotRequestAuthentication() {
        val first = activity()
        val second = activity()
        callbacks.onActivityStarted(first)
        callbacks.onActivityPaused(first)
        callbacks.onActivityStarted(second)
        callbacks.onActivityResumed(second)
        callbacks.onActivityStopped(first)
        callbacks.onActivityPaused(second)
        callbacks.onActivityStarted(first)
        callbacks.onActivityResumed(first)
        callbacks.onActivityStopped(second)

        verify(exactly = 0) { first.startActivityForResult(any<Intent>(), any()) }
        verify(exactly = 0) { second.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun configurationRecreationDoesNotRequestAuthentication() {
        val previous = activity(changingConfigurations = true)
        val recreated = activity()
        callbacks.onActivityStarted(previous)
        callbacks.onActivityPaused(previous)
        callbacks.onActivityStopped(previous)
        callbacks.onActivityStarted(recreated)
        callbacks.onActivityResumed(recreated)

        verify(exactly = 0) { recreated.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun returningFromSuccessfulAuthenticationDoesNotRelock() {
        val content = activity()
        val authentication = mockk<PassCodeActivity>(relaxed = true)
        callbacks.onActivityStarted(content)
        callbacks.onActivityPaused(content)
        callbacks.onActivityStarted(authentication)
        callbacks.onActivityResumed(authentication)
        callbacks.onActivityStopped(content)
        manager.updateLockTimestamp()
        callbacks.onActivityPaused(authentication)
        callbacks.onActivityStarted(content)
        callbacks.onActivityResumed(content)

        verify(exactly = 0) { content.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun resumingAuthenticationDoesNotUnlockWithoutSuccess() {
        val content = activity()
        val authentication = mockk<PassCodeActivity>(relaxed = true)
        callbacks.onActivityStarted(content)
        callbacks.onActivityStopped(content)
        callbacks.onActivityStarted(authentication)
        callbacks.onActivityResumed(authentication)

        assertTrue(manager.passCodeShouldBeRequested())
    }

    private fun activity(changingConfigurations: Boolean = false): Activity {
        val activity = mockk<Activity>(relaxed = true)
        val powerManager = mockk<PowerManager>()
        every { activity.isChangingConfigurations } returns changingConfigurations
        every { powerManager.isInteractive } returns true
        every { activity.getSystemService(Context.POWER_SERVICE) } returns powerManager
        return activity
    }
}
