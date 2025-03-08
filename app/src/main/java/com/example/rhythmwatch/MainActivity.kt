package com.example.rhythmwatch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.rhythmwatch.ui.theme.RhythmWatchTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TimerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel (critical for Android 8+)
        createNotificationChannel()

        enableEdgeToEdge()

        // Initialize ViewModel with custom factory
        val viewModelFactory = TimerViewModelFactory(application)
        viewModel = ViewModelProvider(this, viewModelFactory)[TimerViewModel::class.java]

        setContent {
            RhythmWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TimerScreen(
                        viewModel = viewModel, modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "TimerChannel",  // Must match service channel ID
                "Timer Notifications", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background timer updates"
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun TimerScreen(viewModel: TimerViewModel, modifier: Modifier = Modifier) {
    val currentTime by viewModel.currentTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isBreakMode by viewModel.isBreakMode.collectAsState()

    val tapLabel = remember(isRunning, isBreakMode) {
        if (isBreakMode) "Enjoy your break!"
        else if (!isRunning) "Tap to start work"
        else "Tap to start break"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable {
                if (isBreakMode) {
                    // Do nothing in break mode
                } else {
                    if (isRunning) {
                        viewModel.startBreak()
                    } else {
                        viewModel.startWork()
                    }
                }
            }, contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),  // Debug background
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Log.d("TimerScreen", "Current Time: $currentTime")
                Text(
                    text = currentTime,  // Always show currentTime
                    fontSize = 48.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = tapLabel, fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier.padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(
                    text = "Reset", fontSize = 16.sp, color = Color.White
                )
            }
        }
    }
}

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private var timerService: WeakReference<TimerService>? = null

    private class TimerServiceConnection(
        private val viewModelRef: WeakReference<TimerViewModel>
    ) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            viewModelRef.get()?.onServiceConnected(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModelRef.get()?.onServiceDisconnected()
        }
    }

    private fun onServiceConnected(binder: IBinder?) {
        val service = (binder as TimerService.LocalBinder).getService()
        timerService = WeakReference(service) // Wrap in WeakReference
        observeTimeUpdates()
    }

    private fun onServiceDisconnected() {
        timerService = null
    }

    private val serviceConnection = TimerServiceConnection(WeakReference(this))

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) { /* Ignored */ }
        timerService = null
    }

    init {
        // Start observing when ViewModel is created
        getApplication<Application>().bindService(
            Intent(getApplication(), TimerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun observeTimeUpdates() {
        viewModelScope.launch {
            timerService?.get()?.timeUpdates?.collect { time: String ->
                _currentTime.value = time
            }
        }
        viewModelScope.launch {
            timerService?.get()?.isBreakMode?.collect { isBreak ->
                _isBreakMode.value = isBreak
            }
        }
    }

    private val _currentTime = MutableStateFlow("00:00:00")
    val currentTime = _currentTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isBreakMode = MutableStateFlow(false)
    val isBreakMode = _isBreakMode.asStateFlow()

    fun startWork() {
        Log.d("TimerViewModel", "Start Work")
        _isRunning.value = true
        _isBreakMode.value = false

        val context = getApplication<Application>()
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START_WORK
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startBreak() {
        Log.d("TimerViewModel", "Start Break")
        _isRunning.value = false
        _isBreakMode.value = true

        // Immediately set the break time to the current work time
        val currentWorkTime = _currentTime.value
        _currentTime.value = currentWorkTime

        val context = getApplication<Application>()
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START_BREAK
            putExtra("BREAK_TIME", currentWorkTime)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun resetTimer() {
        Log.d("TimerViewModel", "Reset Timer")
        val context = getApplication<Application>()
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET
        }
        context.startService(intent)
        _isRunning.value = false
        _isBreakMode.value = false
        _currentTime.value = "00:00:00" // Ensure the UI reflects the reset
    }
}

class TimerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return TimerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Preview(showBackground = true)
@Composable
fun TimerScreenPreview() {
    RhythmWatchTheme {
        TimerScreen(viewModel = TimerViewModel(ApplicationProvider.getApplicationContext()))
    }
}