package com.example.videoresizer

import android.app.Application

/**
 * App-wide entry point. Its only job today is installing the built-in crash
 * logger as early as possible — before any Activity/Service can run — so an
 * uncaught exception anywhere in the app gets captured. See [CrashLogger].
 */
class VideoResizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
