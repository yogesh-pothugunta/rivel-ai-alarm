package com.example.ui.ringing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.engine.ActiveAlarmData
import com.example.alarm.engine.AlarmActiveState
import com.example.alarm.receiver.AlarmReceiver
import com.example.alarm.service.AlarmSoundPlayer
import com.example.mission.camera.WakeObject
import com.example.mission.camera.WakeObjectPool
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelEmerald
import com.example.ui.theme.RivelRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RingingStage {
    INITIAL_RINGING,
    OBJECT_ROULETTE,
    CAMERA_SCAN,
    SUCCESS
}

@Composable
fun AlarmRingingOverlay(
    alarmData: ActiveAlarmData,
    soundPlayer: AlarmSoundPlayer,
    onCompleteMissionAndDismiss: () -> Unit,
    onStartFirstStudyBlock: () -> Unit
) {
    val context = LocalContext.current
    var currentStage by remember { mutableStateOf(RingingStage.INITIAL_RINGING) }

    // Pick randomized wake object with non-repeating history tracking
    val selectedWakeObject = remember {
        if (alarmData.targetObject.isNotBlank() && alarmData.targetObject != "Book") {
            WakeObjectPool.findObjectByName(alarmData.targetObject)
        } else {
            WakeObjectPool.pickNextWakeObject(context)
        }
    }

    DisposableEffect(alarmData.alarmId) {
        soundPlayer.startAlarmSound(isVibrationEnabled = true)
        onDispose {
            soundPlayer.stop()
            AlarmReceiver.cancelNotification(context)
        }
    }

    val timeString = remember {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(alarmData.triggerTimestamp))
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("alarm_ringing_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ringing_stage_transition"
        ) { stage ->
            when (stage) {
                RingingStage.INITIAL_RINGING -> {
                    InitialRingingView(
                        timeString = timeString,
                        label = alarmData.label,
                        onTurnOffAlarmClicked = {
                            currentStage = RingingStage.OBJECT_ROULETTE
                        }
                    )
                }

                RingingStage.OBJECT_ROULETTE -> {
                    ObjectSelectionRouletteScreen(
                        targetObject = selectedWakeObject,
                        onOpenCamera = {
                            currentStage = RingingStage.CAMERA_SCAN
                        }
                    )
                }

                RingingStage.CAMERA_SCAN -> {
                    ObjectMissionScreen(
                        targetObject = selectedWakeObject.name,
                        onMissionSuccess = {
                            soundPlayer.stop()
                            currentStage = RingingStage.SUCCESS
                        }
                    )
                }

                RingingStage.SUCCESS -> {
                    SuccessAndStudyTransition(
                        label = alarmData.label,
                        verifiedObject = selectedWakeObject.name,
                        onDismissOnly = {
                            soundPlayer.stop()
                            AlarmActiveState.dismissAlarm()
                            onCompleteMissionAndDismiss()
                        },
                        onStartStudy = {
                            soundPlayer.stop()
                            AlarmActiveState.dismissAlarm()
                            onCompleteMissionAndDismiss()
                            onStartFirstStudyBlock()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialRingingView(
    timeString: String,
    label: String,
    onTurnOffAlarmClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Ringing Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(RivelRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = RivelRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = timeString,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp),
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label.ifBlank { "RIVEL Study Alarm" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Time to rise and conquer today's study goals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Proof-of-Wake Rule Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AI PROOF-OF-WAKE REQUIRED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = RivelAmber,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To ensure you don't fall back asleep, this alarm requires scanning a randomly selected real-world object.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Single Main Action: Turn Off Alarm (NO snooze / skip buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onTurnOffAlarmClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("turn_off_alarm_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RivelRed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TURN OFF ALARM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Physical proof-of-wake verification will begin",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SuccessAndStudyTransition(
    label: String,
    verifiedObject: String,
    onDismissOnly: () -> Unit,
    onStartStudy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(RivelEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = RivelEmerald,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "OBJECT VERIFIED!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$verifiedObject successfully confirmed. You are officially awake and ready to study!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Bridge to Study Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Next Step: Build Morning Momentum",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The first 20 minutes after waking are the highest leverage for memory and deep focus. Launch a 25-minute study sprint now.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = onStartStudy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("launch_study_after_alarm_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start First Focus Sprint",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDismissOnly,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Go to Dashboard")
            }
        }
    }
}
