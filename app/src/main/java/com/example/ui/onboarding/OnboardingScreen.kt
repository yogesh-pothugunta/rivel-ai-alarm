package com.example.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelEmerald
import com.example.ui.theme.RivelIndigo

@Composable
fun OnboardingScreen(
    onComplete: (
        name: String,
        educationLevel: String,
        course: String,
        year: String,
        mainGoal: String,
        wakeTime: String,
        sleepTime: String,
        subjects: String,
        upcomingExam: String
    ) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    var name by remember { mutableStateOf("") }
    var educationLevel by remember { mutableStateOf("College / University") }
    var course by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2nd Year") }

    var mainGoal by remember { mutableStateOf("Wake up early and build consistent study habits") }
    var subjects by remember { mutableStateOf("Data Structures, OS, Database Systems") }
    var upcomingExam by remember { mutableStateOf("Midterms in 3 weeks") }

    var wakeTime by remember { mutableStateOf("06:30") }
    var sleepTime by remember { mutableStateOf("23:00") }

    val totalSteps = 3

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Progress
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Brand Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RIVEL FOR STUDENTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step Indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until totalSteps) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(6.dp)
                                .width(if (i == step) 28.dp else 12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == step) MaterialTheme.colorScheme.primary
                                    else if (i < step) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step Content
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step"
            ) { currentStep ->
                when (currentStep) {
                    0 -> Step1Identity(
                        name = name,
                        onNameChange = { name = it },
                        educationLevel = educationLevel,
                        onEducationChange = { educationLevel = it },
                        course = course,
                        onCourseChange = { course = it },
                        year = year,
                        onYearChange = { year = it }
                    )
                    1 -> Step2StudyGoals(
                        mainGoal = mainGoal,
                        onGoalChange = { mainGoal = it },
                        subjects = subjects,
                        onSubjectsChange = { subjects = it },
                        upcomingExam = upcomingExam,
                        onExamChange = { upcomingExam = it }
                    )
                    else -> Step3WakeRoutine(
                        wakeTime = wakeTime,
                        onWakeTimeChange = { wakeTime = it },
                        sleepTime = sleepTime,
                        onSleepTimeChange = { sleepTime = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Navigation Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (step < totalSteps - 1) {
                            step++
                        } else {
                            onComplete(
                                name.ifBlank { "Student" },
                                educationLevel,
                                course.ifBlank { "General Studies" },
                                year,
                                mainGoal,
                                wakeTime,
                                sleepTime,
                                subjects,
                                upcomingExam
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_next_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (step == totalSteps - 1) "Get Started with RIVEL" else "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (step == totalSteps - 1) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null
                    )
                }

                if (step > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { step-- },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            onComplete(
                                "Student",
                                "College / University",
                                "General Studies",
                                "1st Year",
                                "Build consistent morning study routine",
                                "06:30",
                                "23:00",
                                "General Subjects",
                                ""
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Skip for now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1Identity(
    name: String,
    onNameChange: (String) -> Unit,
    educationLevel: String,
    onEducationChange: (String) -> Unit,
    course: String,
    onCourseChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome to RIVEL",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Wake. Focus. Learn. Grow.\nLet's personalize your student companion.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("What's your name?") },
            placeholder = { Text("e.g. Alex, Maya") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_name_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = course,
            onValueChange = onCourseChange,
            label = { Text("Degree / Major / Stream") },
            placeholder = { Text("e.g. Computer Science, Medicine, Commerce") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = educationLevel,
                onValueChange = onEducationChange,
                label = { Text("Level") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = year,
                onValueChange = onYearChange,
                label = { Text("Year / Grade") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }
    }
}

@Composable
private fun Step2StudyGoals(
    mainGoal: String,
    onGoalChange: (String) -> Unit,
    subjects: String,
    onSubjectsChange: (String) -> Unit,
    upcomingExam: String,
    onExamChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            tint = RivelAmber,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your Study Priorities",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tell RIVEL what you're mastering so we can optimize your focus sessions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = subjects,
            onValueChange = onSubjectsChange,
            label = { Text("Key Subjects / Modules") },
            placeholder = { Text("e.g. Calculus, Data Structures, Physics") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = mainGoal,
            onValueChange = onGoalChange,
            label = { Text("Primary Academic Goal") },
            placeholder = { Text("e.g. Master algorithms & maintain a 3.8 GPA") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = upcomingExam,
            onValueChange = onExamChange,
            label = { Text("Next Major Exam / Deadline (Optional)") },
            placeholder = { Text("e.g. Midterm Exams next Friday") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun Step3WakeRoutine(
    wakeTime: String,
    onWakeTimeChange: (String) -> Unit,
    sleepTime: String,
    onSleepTimeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = RivelEmerald,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Sleep & Wake Rhythm",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "RIVEL's mission-driven wake system activates your brain the moment your alarm rings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⏰ Morning Wake Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = onWakeTimeChange,
                    label = { Text("Target Wake Time (HH:MM)") },
                    placeholder = { Text("06:30") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🌙 Target Bedtime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sleepTime,
                    onValueChange = onSleepTimeChange,
                    label = { Text("Target Sleep Time (HH:MM)") },
                    placeholder = { Text("23:00") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        }
    }
}
