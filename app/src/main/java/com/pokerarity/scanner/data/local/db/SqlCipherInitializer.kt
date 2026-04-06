package com.pokerarity.scanner.data.local.db

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

internal object SqlCipherInitializer {
    private const val TAG = "SqlCipherInit"
    private val loaded = AtomicBoolean(false)

    fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            System.loadLibrary("sqlcipher")
            loaded.set(true)
            Log.i(TAG, "Loaded sqlcipher native library")
        }
    }
}
