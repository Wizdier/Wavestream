package com.wavestream.app

import android.app.Application

/**
 * Wavestream application class — initializes global singletons.
 *
 * Mirrors CloudStream's CloudStreamApp.
 */
class WaveStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global singletons are initialized in MainActivity.onCreate
        // (since they need a Context which isn't available in Application.onCreate
        // for some operations on Android 14+).
    }

    companion object {
        const val TAG = "Wavestream"
    }
}
