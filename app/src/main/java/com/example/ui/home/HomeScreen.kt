package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AlarmEntity
import com.example.data.local.entity.DailyProgressEntity
import com.example.data.local.entity.StudyTaskEntity
import com.example.ui.navigation.RivelScreen
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelEmerald
import com.example.ui.theme.RivelIndigo
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (RivelScreen) -> Unit,
    onOpenProfile: () -> Unit,
    onStartFocusForTask: (taskId: Long?, taskName: String, subject: String, minutes: Int) -> Unit,
    onAskAI: (prompt: String) -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val nextAlarm by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val priorityTask by viewModel.todayPriorityTask.collectAsStateWithLifecycle()
    val pendingTasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
    val progress by viewModel.todayProgress.collectAsStateWithLifecycle()

    val studentName = profile?.name?.ifBlank { "Student" } ?: "Student"
    val course = profile?.course ?: ""
    val greeting = getGreetingTime()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
            HeaderSection(
                greeting = greeting,
                studentName = studentName,
                course = course,
                streak = progress?.currentStreak ?: 0,
                onOpenProfile = onOpenProfile
            )
        }

        // 2. Today's Progress Card
        item {
            ProgressSummaryCard(progress = progress)
        }

        // 3. Next Alarm Card
        item {
            NextAlarmCard(
                alarm = nextAlarm,
                onToggle = { nextAlarm?.let { viewModel.toggleAlarm(it) } },
                onNavigateToAlarms = { onNavigate(RivelScreen.ALARMS) }
            )
        }

        // 4. Today's Study Priority
        item {
            TodayPriorityCard(
                priorityTask = priorityTask,
                firstPending = pendingTasks.firstOrNull(),
                onCompleteTask = { viewModel.completeTask(it) },
                onStartFocus = { task ->
                    onStartFocusForTask(task.id, "${task.subject}: ${task.topic}", task.subject, task.estimatedMinutes)
                },
                onNavigateToStudy = { onNavigate(RivelScreen.STUDY) }
            )
        }

        // 5. Quick Focus Action
        item {
            QuickFocusCard(
                onStartQuickFocus = {
                    onStartFocusForTask(null, "Deep Study Session", "General", 25)
                }
            )
        }

        // 6. RIVEL AI Coach Prompt Card
        item {
            RivelAICoachCard(
                onAsk = onAskAI,
                onNavigateToAI = { onNavigate(RivelScreen.AI) }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    greeting: String,
    studentName: String,
    course: String,
    streak: Int,
    onOpenProfile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = studentName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (course.isNotBlank()) {
                Text(
                    text = course,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Streak Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(RivelAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = RivelAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (streak > 0) "$streak d" else "0 d",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = RivelAmber
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Profile Button
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Student Profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgressSummaryCard(progress: DailyProgressEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("progress_summary_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Study Loop",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Consistency Engine",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    icon = Icons.Default.Timer,
                    value = "${progress?.totalStudyMinutes ?: 0}",
                    unit = "min",
                    label = "Study Time",
                    color = RivelIndigo
                )
                MetricItem(
                    icon = Icons.Default.Bolt,
                    value = "${progress?.completedSessionsCount ?: 0}",
                    unit = "sprints",
                    label = "Focus Blocks",
                    color = RivelAmber
                )
                MetricItem(
                    icon = Icons.Default.CheckCircle,
                    value = "${progress?.completedTasksCount ?: 0}",
                    unit = "done",
                    label = "Tasks Done",
                    color = RivelEmerald
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    unit: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NextAlarmCard(
    alarm: AlarmEntity?,
    onToggle: () -> Unit,
    onNavigateToAlarms: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_alarm_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Next Wake Mission",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (alarm != null) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("home_alarm_toggle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (alarm != null) {
                val formattedTime = String.format("%02d:%02d", alarm.hour, alarm.minute)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = alarm.label.ifBlank { "Study Wake-up" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // AI Proof of Wake Badge
                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RivelAmber.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🎲 Proof-of-Wake",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RivelAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Random Object Scan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "No active alarm scheduled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Set a morning study alarm with AI proof-of-wake object verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNavigateToAlarms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (alarm != null) "Manage Alarms" else "Set Wake Alarm", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayPriorityCard(
    priorityTask: StudyTaskEntity?,
    firstPending: StudyTaskEntity?,
    onCompleteTask: (Long) -> Unit,
    onStartFocus: (StudyTaskEntity) -> Unit,
    onNavigateToStudy: () -> Unit
) {
    val activeTask = priorityTask ?: firstPending

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_priority_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = RivelEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Today's Study Priority",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToStudy() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTask != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onCompleteTask(activeTask.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (activeTask.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            contentDescription = "Complete task",
                            tint = if (activeTask.isCompleted) RivelEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTask.subject,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeTask.topic.ifBlank { activeTask.taskDescription },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "⏱ ${activeTask.estimatedMinutes} min estimated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onStartFocus(activeTask) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("start_focus_for_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus")
                    }
                }
            } else {
                Text(
                    text = "No study tasks scheduled yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToStudy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add Today's Priority Task")
                }
            }
        }
    }
}

@Composable
private fun QuickFocusCard(onStartQuickFocus: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStartQuickFocus() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RivelIndigo.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = RivelIndigo
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Quick 25-Min Study Sprint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Single-task focus timer with zero distraction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Start Focus",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RivelAICoachCard(
    onAsk: (prompt: String) -> Unit,
    onNavigateToAI: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ask RIVEL AI Coach",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Open Chat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToAI() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickPromptChip(
                    text = "Help me stop procrastinating",
                    modifier = Modifier.weight(1f),
                    onClick = { onAsk("Help me stop procrastinating") }
                )
                QuickPromptChip(
                    text = "How to study for my exam",
                    modifier = Modifier.weight(1f),
                    onClick = { onAsk("How should I prepare for my upcoming exam?") }
                )
            }
        }
    }
}

@Composable
private fun QuickPromptChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getGreetingTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
