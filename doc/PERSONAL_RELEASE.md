<!--
SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# Personal Android releases

Repository: https://github.com/boncheolgu/nextcloud-android

The personal app uses the versionDev source set, the name Nextcloud Personal,
and the application ID com.boncheolgu.nextcloud. Its account and provider
identifiers are separate from the official app. Sign in to the server again
when first installing it; existing official-app accounts are not migrated.

## Builds

A push to master runs the personal unit tests and builds a test APK artifact.
Test APKs use the runner debug key and are for temporary testing; use signed
release APKs for persistent installation and subsequent updates.

Pushing a tag such as personal-v0.1.0 runs tests, builds a release APK, signs
and verifies it, and publishes it with SHA256SUMS as a GitHub prerelease.
The same workflow can be started manually with a new personal-vX.Y.Z tag.
An existing tag must point to the selected workflow commit. Version codes
increase with the workflow run number; keep this workflow and signing key
when updating the installed app.

## Signing setup (one time)

Create a dedicated Android signing keystore or use one you already own.
Do not commit signing keys, passwords, or their Base64 representation.
Keep a private backup: losing the key prevents updates over installed releases.

Configure these repository Actions secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the signing keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Signing key alias inside the keystore |
| ANDROID_KEY_PASSWORD | Signing key password |

The workflow fails before building a release if signing secrets are absent.
Only the release-publishing job has contents write permission. Signing keys
are decoded into a temporary runner file and removed after signing. No
signing secrets are uploaded as artifacts. Pull requests cannot trigger this
workflow.

## Verify a release

Download the APK and SHA256SUMS, verify the checksum, and install the APK.
Confirm immediate relocking after Home, quick app switching, and screen off.
Check internal navigation, rotation, fold/unfold, passcode authentication,
and device-credential success and cancellation on the actual device.

This fork includes AI-assisted code (Codex). It has not been submitted upstream.
