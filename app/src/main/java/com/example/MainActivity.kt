package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarm.engine.AlarmActiveState
import com.example.data.local.entity.AlarmEntity
import com.example.data.local.entity.StudyTaskEntity
import com.example.ui.ai.AIChatScreen
import com.example.ui.ai.AIChatViewModel
import com.example.ui.alarms.AlarmsScreen
import com.example.ui.alarms.AlarmsViewModel
import com.example.ui.focus.FocusScreen
import com.example.ui.focus.FocusViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.navigation.RivelScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.profile.ProfileDialog
import com.example.ui.ringing.AlarmRingingOverlay
import com.example.ui.study.StudyScreen
import com.example.ui.study.StudyViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

import android.view.WindowManager
import com.example.alarm.engine.ActiveAlarmData
import com.example.alarm.receiver.AlarmReceiver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Activity shows when device is locked and turns screen on for alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        enableEdgeToEdge()
        handleAlarmIntent(intent)

        val app = application as RivelApplication

        setContent {
            MyApplicationTheme {
                RivelApp(app = app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent?) {
        if (intent == null) return
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        if (alarmId != -1L) {
            val label = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "RIVEL Wake Alarm"
            val missionType = intent.getStringExtra(AlarmReceiver.EXTRA_MISSION_TYPE) ?: "BRAIN"
            val missionDifficulty = intent.getStringExtra(AlarmReceiver.EXTRA_MISSION_DIFFICULTY) ?: "MEDIUM"
            val targetObject = intent.getStringExtra(AlarmReceiver.EXTRA_TARGET_OBJECT) ?: "Book"
            val targetSteps = intent.getIntExtra(AlarmReceiver.EXTRA_TARGET_STEPS, 20)

            AlarmActiveState.triggerRinging(
                ActiveAlarmData(
                    alarmId = alarmId,
                    label = label,
                    missionType = missionType,
                    missionDifficulty = missionDifficulty,
                    targetObject = targetObject,
                    movementTargetSteps = targetSteps,
                    triggerTimestamp = System.currentTimeMillis()
                )
            )
        }
    }
}

@Composable
fun RivelApp(app: RivelApplication) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Request permissions on first launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // ViewModels
    val homeViewModel = remember {
        HomeViewModel(
            app.studentProfileRepository,
            app.alarmRepository,
            app.studyTaskRepository,
            app.dailyProgressRepository,
            app.alarmScheduler
        )
    }

    val alarmsViewModel = remember {
        AlarmsViewModel(app.alarmRepository, app.alarmScheduler)
    }

    val studyViewModel = remember {
        StudyViewModel(
            app.studyTaskRepository,
            app.dailyProgressRepository,
            app.studentProfileRepository
        )
    }

    val focusViewModel = remember {
        FocusViewModel(
            app.focusSessionRepository,
            app.dailyProgressRepository
        )
    }

    val aiViewModel = remember {
        AIChatViewModel(
            app.aiService,
            app.chatMessageRepository,
            app.studentProfileRepository,
            app.dailyProgressRepository
        )
    }

    val profile by homeViewModel.profile.collectAsStateWithLifecycle()
    val ringingAlarm by AlarmActiveState.currentRingingAlarm.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(RivelScreen.HOME) }
    var isProfileDialogOpen by remember { mutableStateOf(false) }

    // Seed default tasks and alarm on first launch if student has none
    LaunchedEffect(profile) {
        if (profile?.isOnboarded == true) {
            val alarms = app.alarmRepository.getEnabledAlarmsOnce()
            if (alarms.isEmpty()) {
                val defaultAlarm = AlarmEntity(
                    hour = 6,
                    minute = 30,
                    daysOfWeek = "MON,TUE,WED,THU,FRI",
                    label = "Morning Study Wake-up",
                    missionType = "BRAIN",
                    missionDifficulty = "MEDIUM",
                    targetObject = "Book"
                )
                val id = app.alarmRepository.insertAlarm(defaultAlarm)
                app.alarmScheduler.scheduleAlarm(defaultAlarm.copy(id = id))
            }
        }
    }

    // 1. Alarm Ringing Overlay (Top Priority)
    if (ringingAlarm != null) {
        AlarmRingingOverlay(
            alarmData = ringingAlarm!!,
            soundPlayer = app.alarmSoundPlayer,
            onCompleteMissionAndDismiss = {
                scope.launch {
                    app.dailyProgressRepository.recordWakeAlarmMissionSuccess()
                }
            },
            onStartFirstStudyBlock = {
                currentScreen = RivelScreen.FOCUS
                focusViewModel.setupSession(null, "Morning Study Sprint", "Focus", 25)
                focusViewModel.startTimer()
            }
        )
        return
    }

    // 2. Onboarding Flow
    if (profile == null || !profile!!.isOnboarded) {
        OnboardingScreen(
            onComplete = { name, education, course, year, goal, wakeTime, sleepTime, subjects, exam ->
                scope.launch {
                    app.studentProfileRepository.completeOnboarding(
                        name, education, course, year, goal, wakeTime, sleepTime, subjects, exam
                    )

                    // Also create a sample first study task
                    val firstSubject = subjects.split(",").firstOrNull()?.trim() ?: "Core Subject"
                    app.studyTaskRepository.insertTask(
                        StudyTaskEntity(
                            subject = firstSubject,
                            topic = "Chapter 1 Summary & Active Recall",
                            taskDescription = "Read notes, test with active recall, and solve 3 practice questions",
                            deadline = "Today",
                            estimatedMinutes = 30,
                            priority = "HIGH",
                            isPriorityToday = true
                        )
                    )
                }
            }
        )
        return
    }

    // 3. Main Scaffold with Bottom Navigation Bar
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                RivelScreen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("nav_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                RivelScreen.HOME -> HomeScreen(
                    viewModel = homeViewModel,
                    onNavigate = { screen -> currentScreen = screen },
                    onOpenProfile = { isProfileDialogOpen = true },
                    onStartFocusForTask = { taskId, taskName, subject, minutes ->
                        focusViewModel.setupSession(taskId, taskName, subject, minutes)
                        currentScreen = RivelScreen.FOCUS
                        focusViewModel.startTimer()
                    },
                    onAskAI = { prompt ->
                        currentScreen = RivelScreen.AI
                        aiViewModel.sendMessage(prompt)
                    }
                )

                RivelScreen.ALARMS -> AlarmsScreen(
                    viewModel = alarmsViewModel
                )

                RivelScreen.STUDY -> StudyScreen(
                    viewModel = studyViewModel,
                    onStartFocus = { taskId, taskName, subject, minutes ->
                        focusViewModel.setupSession(taskId, taskName, subject, minutes)
                        currentScreen = RivelScreen.FOCUS
                        focusViewModel.startTimer()
                    }
                )

                RivelScreen.FOCUS -> FocusScreen(
                    viewModel = focusViewModel
                )

                RivelScreen.AI -> AIChatScreen(
                    viewModel = aiViewModel,
                    onNavigateToFocus = { minutes ->
                        focusViewModel.setDuration(minutes)
                        currentScreen = RivelScreen.FOCUS
                        focusViewModel.startTimer()
                    },
                    onNavigateToStudy = {
                        currentScreen = RivelScreen.STUDY
                    }
                )
            }
        }
    }

    if (isProfileDialogOpen) {
        ProfileDialog(
            profile = profile,
            onDismiss = { isProfileDialogOpen = false },
            onSaveProfile = { updated ->
                scope.launch {
                    app.studentProfileRepository.saveProfile(updated)
                }
            }
        )
    }
}

