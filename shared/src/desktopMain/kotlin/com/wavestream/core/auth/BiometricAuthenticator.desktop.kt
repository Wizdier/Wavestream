package com.wavestream.core.auth

actual class BiometricAuthenticator actual constructor() {
    actual fun isAvailable(): Boolean = false
    actual fun authenticate(title: String, callback: (Boolean) -> Unit) { callback(false) }
}
