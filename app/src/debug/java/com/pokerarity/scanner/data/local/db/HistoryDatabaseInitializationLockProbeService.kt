package com.pokerarity.scanner.data.local.db

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Debug-only IPC probe for proving the initialization lock coordinates separate app processes. */
class HistoryDatabaseInitializationLockProbeService : Service() {
    private val releaseLock = CountDownLatch(1)

    private val messenger by lazy {
        Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                when (message.what) {
                    MESSAGE_BEGIN_LOCK_ATTEMPT -> beginLockAttempt(message.replyTo)
                    MESSAGE_RELEASE_LOCK -> releaseLock.countDown()
                    else -> super.handleMessage(message)
                }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        releaseLock.countDown()
        super.onDestroy()
    }

    private fun beginLockAttempt(replyTo: Messenger?) {
        if (replyTo == null) return
        Thread {
            runCatching {
                val nonBlockingAcquired = HistoryDatabaseInitializationLock.tryWithExclusiveLock(applicationContext) { }
                replyTo.send(
                    statusMessage(MESSAGE_NON_BLOCKING_LOCK_RESULT).apply {
                        arg2 = if (nonBlockingAcquired) 1 else 0
                    }
                )
                HistoryDatabaseInitializationLock.withExclusiveLock(applicationContext) {
                    replyTo.send(statusMessage(MESSAGE_LOCK_ACQUIRED))
                    check(releaseLock.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                }
                replyTo.send(statusMessage(MESSAGE_LOCK_RELEASED))
            }.onFailure {
                runCatching { replyTo.send(statusMessage(MESSAGE_LOCK_PROBE_FAILED)) }
            }
        }.start()
    }

    private fun statusMessage(what: Int): Message = Message.obtain(null, what).apply {
        arg1 = Process.myPid()
    }

    companion object {
        const val MESSAGE_BEGIN_LOCK_ATTEMPT = 1
        const val MESSAGE_RELEASE_LOCK = 2
        const val MESSAGE_LOCK_ATTEMPT_STARTED = 3
        const val MESSAGE_LOCK_ACQUIRED = 4
        const val MESSAGE_LOCK_RELEASED = 5
        const val MESSAGE_LOCK_PROBE_FAILED = 6
        const val MESSAGE_NON_BLOCKING_LOCK_RESULT = 7
        private const val PROBE_TIMEOUT_SECONDS = 20L
    }
}
