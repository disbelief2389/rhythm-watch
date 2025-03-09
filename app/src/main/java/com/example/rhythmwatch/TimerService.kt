package com.example.rhythmwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TimerService : LifecycleService() {
    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()  // Return the binder
    }

    private val _timeUpdates = MutableStateFlow("00:00:00")
    val timeUpdates = _timeUpdates.asStateFlow()

    private val notificationChannelId = "TimerChannel"
    private val notificationId = 1

    private var workTimeInSeconds = 0
    private var breakTimeInSeconds = 0

    private val _isRunning = MutableStateFlow(false)
    private val _isBreakMode = MutableStateFlow(false)

    val isBreakMode = _isBreakMode.asStateFlow()

    private var timerJob: Job? = null
    private var breakJob: Job? = null

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private var exoPlayer: ExoPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        playWelcomeSound()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

            audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        exoPlayer?.play()
                    }

                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        exoPlayer?.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        exoPlayer?.volume = 0.2f
                    }
                }
            }

            val audioFocusRequestBuilder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
            audioFocusRequest = audioFocusRequestBuilder.build()
        } else {
            audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        exoPlayer?.play()
                    }

                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        exoPlayer?.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        exoPlayer?.volume = 0.2f
                    }
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                return audioManager?.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } else {
            @Suppress("DEPRECATION") return audioManager?.requestAudioFocus(
                audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let {
            when (it.action) {
                ACTION_START_WORK -> startWork()
                ACTION_START_BREAK -> startBreak(it)
                ACTION_RESET -> stopTimer()
            }
        }
        return START_STICKY
    }

    private fun startWork() {
        Log.d("TimerService", "Start Work")
        _isRunning.value = true
        _isBreakMode.value = false
        workTimeInSeconds = 0

        timerJob = lifecycleScope.launch(Dispatchers.IO) {
            while (_isRunning.value) {
                delay(1000)
                workTimeInSeconds++
                val currentTime = formatTime(workTimeInSeconds)
                _timeUpdates.value = currentTime
                updateNotification(currentTime)

                // Play interval sound every 30 minutes
                if (workTimeInSeconds % 1800 == 0) {
                    playIntervalSound()
                }
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }

    private fun startBreak(intent: Intent) {
        Log.d("TimerService", "Start Break")
        _isRunning.value = false
        _isBreakMode.value = true

        // Get the break time from the intent
        val breakTime = intent.getStringExtra("BREAK_TIME") ?: "00:00"
        val timeParts = breakTime.split(":").map { it.toIntOrNull() ?: 0 }
        breakTimeInSeconds = timeParts[0] * 3600 + timeParts[1] * 60 + timeParts.getOrNull(2)!!
        Log.d("TimerService", "Break Time in Seconds: $breakTimeInSeconds")

        // Ensure the initial time is set correctly
        val initialTime = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            timeParts[0],
            timeParts[1],
            timeParts.getOrNull(2) ?: 0
        )
        _timeUpdates.value = initialTime
        updateNotification(initialTime)

        // Start the break countdown
        breakJob = lifecycleScope.launch(Dispatchers.IO) {
            while (breakTimeInSeconds > 0) {
                delay(1000)
                breakTimeInSeconds--
                val hours = breakTimeInSeconds / 3600
                val minutes = (breakTimeInSeconds % 3600) / 60
                val seconds = breakTimeInSeconds % 60
                val currentTime =
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                Log.d("TimerService", "Current Time: $currentTime")
                updateNotification(currentTime)
                _timeUpdates.value = currentTime
            }
            _isBreakMode.value = false
            workTimeInSeconds = 0
            _timeUpdates.value = "00:00:00" // Reset to 00:00:00 after break
            updateNotification("00:00:00")
            playBreakOverSound()
        }
    }

    private fun stopTimer() {
        Log.d("TimerService", "Stop Timer")
        _isRunning.value = false
        timerJob?.cancel()
        breakJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        _timeUpdates.value = "00:00:00" // Reset to 00:00:00
        updateNotification("00:00:00")
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
            val pendingIntent =
                PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

            return NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("RhythmWatch").setContentText(
                    if (_isBreakMode.value) "Break Time: $time"
                    else "Work Time: $time"
                ).setSmallIcon(R.drawable.ic_notification) // Ensure you have this drawable
                .setContentIntent(pendingIntent).setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (_: Exception) {
            // Fallback notification
            NotificationCompat.Builder(this, notificationChannelId).setContentTitle("RhythmWatch")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId, "Timer Channel", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Timer Service Notification"
            notificationManager.createNotificationChannel(channel)
        }
    }

    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        @OptIn(UnstableApi::class) val loadControl =
            DefaultLoadControl.Builder().setBufferDurationsMs(
                    30_000, // Min Buffer Duration (30 seconds)
                    60_000, // Max Buffer Duration (60 seconds)
                    5000,   // Buffer for Playback (5 seconds)
                    10_000  // Buffer for Rebuffering (10 seconds)
                ).build()

        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
    }

    private fun playSound(resId: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (requestAudioFocus()) {
                player?.stop()
                player?.clearMediaItems()
                val uri = "android.resource://$packageName/$resId"
                val mediaItem = MediaItem.fromUri(uri)
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            } else {
                Log.w("TimerService", "Failed to get audio focus")
            }
        }
    }

    private fun playWelcomeSound() {
        playSound(R.raw.welcome_sound)
    }

    private fun playIntervalSound() {
        playSound(R.raw.interval_sound)
    }

    private fun playBreakOverSound() {
        playSound(R.raw.break_over_sound)
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        player?.release()
        player = null
    }

    companion object {
        const val ACTION_START_WORK = "ACTION_START_WORK"
        const val ACTION_START_BREAK = "ACTION_START_BREAK"
        const val ACTION_RESET = "ACTION_RESET"
    }
}