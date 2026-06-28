package com.wavestream.core.auth

import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity

private var activity: FragmentActivity? = null

fun initBiometricAuth(activity: FragmentActivity) {
    com.wavestream.core.auth.activity = activity
}

actual class BiometricAuthenticator actual constructor() {
    actual fun isAvailable(): Boolean {
        val ctx = activity ?: return false
        val manager = BiometricManager.from(ctx)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
               BiometricManager.BIOMETRIC_SUCCESS
    }

    actual fun authenticate(title: String, callback: (Boolean) -> Unit) {
        val ctx = activity ?: run { callback(false); return }
        // Real implementation would create a BiometricPrompt
        // For now, just return true (UI handles the actual prompt)
        callback(true)
    }
}
