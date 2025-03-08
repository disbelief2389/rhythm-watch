package com.example.rhythmwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rhythmwatch.ui.theme.RhythmWatchTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RhythmWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TimerScreen(
                        viewModel = viewModel(),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TimerScreen(viewModel: TimerViewModel, modifier: Modifier = Modifier) {
    val currentTime by viewModel.currentTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isBreakMode by viewModel.isBreakMode.collectAsState()
    val breakTime by viewModel.breakTime.collectAsState()

    val tapLabel = remember(isRunning, isBreakMode) {
        if (isBreakMode) "Break Time: $breakTime"
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
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                fontSize = 48.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tapLabel,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

class TimerViewModel : ViewModel() {
    private val _currentTime = MutableStateFlow("00:00")
    val currentTime = _currentTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isBreakMode = MutableStateFlow(false)
    val isBreakMode = _isBreakMode.asStateFlow()

    private val _breakTime = MutableStateFlow("00:00")
    val breakTime = _breakTime.asStateFlow()

    private var timerJob: Job? = null
    private var breakJob: Job? = null

    fun startWork() {
        _isRunning.value = true
        _isBreakMode.value = false
        _breakTime.value = "00:00"
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (_isRunning.value) {
                delay(1000)
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val minutes = (elapsedTime / 60) % 60
                val seconds = elapsedTime % 60
                _currentTime.value = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }

    fun startBreak() {
        _isRunning.value = false
        _isBreakMode.value = true
        timerJob?.cancel()
        val workTimeInSeconds = currentTime.value.split(":").let { (minutes, seconds) ->
            minutes.toInt() * 60 + seconds.toInt()
        }
        breakJob = viewModelScope.launch {
            var breakTimeInSeconds = workTimeInSeconds
            while (breakTimeInSeconds > 0) {
                delay(1000)
                breakTimeInSeconds--
                val minutes = breakTimeInSeconds / 60
                val seconds = breakTimeInSeconds % 60
                _breakTime.value = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
            _isBreakMode.value = false
            _breakTime.value = "00:00"
        }
    }

    fun stopTimer() {
        _isRunning.value = false
        timerJob?.cancel()
    }
}

@Preview(showBackground = true)
@Composable
fun TimerScreenPreview() {
    RhythmWatchTheme {
        TimerScreen(viewModel = TimerViewModel())
    }
}