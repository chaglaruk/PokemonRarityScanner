package com.pokerarity.scanner.data.local.db

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseInitializationLockTest {
    @Test
    fun serializesDatabaseInitializationAcrossProcesses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceIntent = Intent(context, HistoryDatabaseInitializationLockProbeService::class.java)
        val connectionReady = CountDownLatch(1)
        val statuses = LinkedBlockingQueue<Message>()
        var remoteService: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remoteService = Messenger(binder)
                connectionReady.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                remoteService = null
            }
        }
        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            statuses.offer(Message.obtain(message))
            true
        })
        assertTrue(context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE))
        assertTrue("debug probe service connected", connectionReady.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val service = checkNotNull(remoteService)
        var releaseSent = false

        try {
            HistoryDatabaseInitializationLock.withExclusiveLock(context) {
                service.send(
                    Message.obtain(null, HistoryDatabaseInitializationLockProbeService.MESSAGE_BEGIN_LOCK_ATTEMPT).apply {
                        replyTo = replyMessenger
                    }
                )
                val started = statuses.poll(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertNotNull("second process attempted a nonblocking lock", started)
                assertEquals(
                    HistoryDatabaseInitializationLockProbeService.MESSAGE_NON_BLOCKING_LOCK_RESULT,
                    checkNotNull(started).what
                )
                assertFalse("probe ran in another process", Process.myPid() == checkNotNull(started).arg1)
                assertEquals(
                    "nonblocking acquisition is rejected while the main process owns the lock",
                    0,
                    checkNotNull(started).arg2
                )
            }

            assertEquals(
                HistoryDatabaseInitializationLockProbeService.MESSAGE_LOCK_ACQUIRED,
                checkNotNull(statuses.poll(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).what
            )
            service.send(Message.obtain(null, HistoryDatabaseInitializationLockProbeService.MESSAGE_RELEASE_LOCK))
            releaseSent = true
            assertEquals(
                HistoryDatabaseInitializationLockProbeService.MESSAGE_LOCK_RELEASED,
                checkNotNull(statuses.poll(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).what
            )
        } finally {
            if (!releaseSent) {
                runCatching {
                    service.send(Message.obtain(null, HistoryDatabaseInitializationLockProbeService.MESSAGE_RELEASE_LOCK))
                }
            }
            context.unbindService(connection)
            context.stopService(serviceIntent)
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT_SECONDS = 10L
    }
}
