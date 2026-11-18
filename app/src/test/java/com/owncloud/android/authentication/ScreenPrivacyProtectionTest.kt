/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.authentication

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.Window
import android.view.WindowManager
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
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

class ScreenPrivacyProtectionTest {
    private lateinit var manager: PassCodeManager
    private lateinit var callbacks: GlobalPassCodeProtectionCallbacks
    private lateinit var preferences: AppPreferences
    private lateinit var content: Activity
    private lateinit var window: Window
    private lateinit var root: View

    @Before
    fun setUp() {
        mockkObject(MainApp)
        every { MainApp.getAppContext() } returns mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        every { preferences.lockPreference } returns SettingsActivity.LOCK_PASSCODE
        val clock = mockk<Clock>()
        every { clock.millisSinceBoot } returns 200L
        manager = PassCodeManager(preferences, clock)
        callbacks = GlobalPassCodeProtectionCallbacks(mockk(relaxed = true), manager)
        root = mockk(relaxed = true)
        window = mockk(relaxed = true)
        content = mockk(relaxed = true)
        every { content.window } returns window
        every { window.findViewById<View>(android.R.id.content) } returns root
        manager.updateLockTimestamp()
    }

    @After
    fun tearDown() {
        unmockkObject(MainApp)
    }

    @Test
    fun unlockedContentIsSecureFromCreation() {
        callbacks.onActivityCreated(content, null)

        verify { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        verify(exactly = 0) { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun creationDoesNotHideTheFrameworkContainerBeforeAppCompatWrapsIt() {
        val frameworkContainer = mockk<View>(relaxed = true)
        every { window.findViewById<View>(android.R.id.content) } returns frameworkContainer
        val coldManager = PassCodeManager(preferences, mockk(relaxed = true))
        val coldCallbacks = GlobalPassCodeProtectionCallbacks(mockk(relaxed = true), coldManager)
        coldCallbacks.onActivityCreated(content, null)

        every { window.findViewById<View>(android.R.id.content) } returns root
        coldCallbacks.onActivityStarted(content)
        coldManager.updateLockTimestamp()
        coldCallbacks.onActivityResumed(content)

        verify(exactly = 0) { frameworkContainer.visibility = View.INVISIBLE }
        verifyOrder {
            root.visibility = View.INVISIBLE
            root.visibility = View.VISIBLE
        }
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun lockedCreationDoesNotResolveContentBeforeLayoutInstallation() {
        val coldManager = PassCodeManager(preferences, mockk(relaxed = true))
        val coldCallbacks = GlobalPassCodeProtectionCallbacks(mockk(relaxed = true), coldManager)
        coldCallbacks.onActivityCreated(content, null)

        verify(exactly = 0) { window.findViewById<View>(android.R.id.content) }
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun contentIsHiddenBeforePauseOnModernAndroid() {
        callbacks.onActivityPrePaused(content)

        verify { root.visibility = View.INVISIBLE }
    }

    @Test
    fun contentIsHiddenOnPauseWithoutWaitingForReturn() {
        callbacks.onActivityPaused(content)

        verify { root.visibility = View.INVISIBLE }
        verify(exactly = 0) { content.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun returnKeepsContentHiddenBeforeLaunchingPin() {
        callbacks.onActivityPaused(content)
        callbacks.onActivityResumed(content)

        verifyOrder {
            root.visibility = View.INVISIBLE
            content.startActivityForResult(any<Intent>(), PassCodeManager.PASSCODE_ACTIVITY)
        }
        verify(exactly = 0) { root.visibility = View.VISIBLE }
    }

    @Test
    fun coldStartContentIsHiddenBeforeResume() {
        manager = PassCodeManager(preferences, mockk(relaxed = true))
        callbacks = GlobalPassCodeProtectionCallbacks(mockk(relaxed = true), manager)
        callbacks.onActivityCreated(content, null)
        callbacks.onActivityStarted(content)

        verify { root.visibility = View.INVISIBLE }
        verify(exactly = 0) { root.visibility = View.VISIBLE }
        verify(exactly = 0) { content.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun successfulAuthenticationRestoresContentAndKeepsWindowSecure() {
        val authentication = mockk<PassCodeActivity>(relaxed = true)
        callbacks.onActivityPaused(content)
        callbacks.onActivityResumed(content)
        callbacks.onActivityResumed(authentication)
        manager.updateLockTimestamp()
        callbacks.onActivityPaused(authentication)
        callbacks.onActivityResumed(content)

        verifyOrder {
            root.visibility = View.INVISIBLE
            content.startActivityForResult(any<Intent>(), PassCodeManager.PASSCODE_ACTIVITY)
            root.visibility = View.VISIBLE
        }
        verify(exactly = 0) { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun internalNavigationBackRestoresPausedContent() {
        val second = mockk<Activity>(relaxed = true)
        callbacks.onActivityPaused(content)
        callbacks.onActivityResumed(second)
        callbacks.onActivityPaused(second)
        callbacks.onActivityResumed(content)

        verifyOrder {
            root.visibility = View.INVISIBLE
            root.visibility = View.VISIBLE
        }
        verify(exactly = 0) { content.startActivityForResult(any<Intent>(), any()) }
    }

    @Test
    fun disabledLockDoesNotConcealContent() {
        every { preferences.lockPreference } returns SettingsActivity.LOCK_NONE
        callbacks.onActivityCreated(content, null)
        callbacks.onActivityPrePaused(content)
        callbacks.onActivityPaused(content)
        callbacks.onActivityResumed(content)

        verify(exactly = 0) { root.visibility = View.INVISIBLE }
        verify(exactly = 0) { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        verify { root.visibility = View.VISIBLE }
    }

    @Test
    fun deviceCredentialLockAlsoConcealsContent() {
        every { preferences.lockPreference } returns SettingsActivity.LOCK_DEVICE_CREDENTIALS
        callbacks.onActivityPrePaused(content)

        verify { root.visibility = View.INVISIBLE }
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun authenticationScreenStaysVisibleWhenPaused() {
        val authentication = mockk<PassCodeActivity>(relaxed = true)
        every { authentication.window } returns window
        callbacks.onActivityPrePaused(authentication)
        callbacks.onActivityPaused(authentication)

        verify(exactly = 0) { root.visibility = View.INVISIBLE }
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
