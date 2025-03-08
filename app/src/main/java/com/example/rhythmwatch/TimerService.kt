package com.example.rhythmwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TimerService : LifecycleService() {
    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()  // Return the binder
    }

    private val _timeUpdates = MutableStateFlow("00:00")
    val timeUpdates = _timeUpdates.asStateFlow()

    private val notificationChannelId = "TimerChannel"
    private val notificationId = 1

    private var workTimeInSeconds = 0
    private var breakTimeInSeconds = 0
    private var isRunning = false
    private var isBreakMode = false

    private var timerJob: Job? = null
    private var breakJob: Job? = null

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let {
            when (it.action) {
                ACTION_START_WORK -> startWork()
                ACTION_START_BREAK -> startBreak()
                ACTION_RESET -> stopTimer()
            }
        }
        return START_STICKY
    }

    private fun startWork() {
        isRunning = true
        isBreakMode = false
        workTimeInSeconds = 0

        timerJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isRunning) {
                delay(1000)
                workTimeInSeconds++
                val minutes = (workTimeInSeconds / 60) % 60
                val seconds = workTimeInSeconds % 60
                val currentTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                updateNotification(currentTime)
            }
        }
    }

    private fun startBreak() {
        isRunning = false
        isBreakMode = true

        breakTimeInSeconds = workTimeInSeconds

        breakJob = lifecycleScope.launch(Dispatchers.IO) {
            while (breakTimeInSeconds > 0) {
                delay(1000)
                breakTimeInSeconds--
                // Update notification
            }
            isBreakMode = false
            workTimeInSeconds = 0
            stopTimer() // Automatically stop service after break
        }
    }

    private fun stopTimer() {
        isRunning = false
        timerJob?.cancel()
        breakJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(time: String) {
        val notification = createNotification(time)
        startForeground(notificationId, notification)
    }

    private fun createNotification(time: String): Notification {
        return try {
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

            return NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("RhythmWatch")
                .setContentText(if (isBreakMode) "Break Time: $time" else "Work Time: $time")
                .setSmallIcon(R.drawable.ic_notification) // Ensure you have this drawable
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            // Fallback notification
            NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("RhythmWatch")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Timer Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Timer Service Notification"
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START_WORK = "ACTION_START_WORK"
        const val ACTION_START_BREAK = "ACTION_START_BREAK"
        const val ACTION_RESET = "ACTION_RESET"
    }
}