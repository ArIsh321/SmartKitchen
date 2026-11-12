package com.arise.smartkitchenai

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds: App Check debug provider. On first run, Logcat prints a debug token
 * ("DebugAppCheckProvider: Enter this debug secret...") — register it in the Firebase console.
 * Never ship this provider in release builds.
 */
internal fun installAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
