package com.example.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.StudentProfileEntity

@Composable
fun ProfileDialog(
    profile: StudentProfileEntity?,
    onDismiss: () -> Unit,
    onSaveProfile: (StudentProfileEntity) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var course by remember { mutableStateOf(profile?.course ?: "") }
    var educationLevel by remember { mutableStateOf(profile?.educationLevel ?: "College") }
    var year by remember { mutableStateOf(profile?.year ?: "2nd Year") }
    var mainGoal by remember { mutableStateOf(profile?.mainGoal ?: "") }
    var subjects by remember { mutableStateOf(profile?.subjects ?: "") }
    var upcomingExam by remember { mutableStateOf(profile?.upcomingExam ?: "") }
    var wakeTime by remember { mutableStateOf(profile?.preferredWakeTime ?: "06:30") }
    var sleepTime by remember { mutableStateOf(profile?.preferredSleepTime ?: "23:00") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Student Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = course,
                    onValueChange = { course = it },
                    label = { Text("Major / Course") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subjects,
                    onValueChange = { subjects = it },
                    label = { Text("Enrolled Subjects") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = mainGoal,
                    onValueChange = { mainGoal = it },
                    label = { Text("Academic Goal") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = upcomingExam,
                    onValueChange = { upcomingExam = it },
                    label = { Text("Upcoming Exam Deadline") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                var backendServerUrl by remember {
                    mutableStateOf(
                        if (com.example.ai.BackendConfig.isConfigured) com.example.ai.BackendConfig.baseUrl else ""
                    )
                }

                OutlinedTextField(
                    value = backendServerUrl,
                    onValueChange = { backendServerUrl = it },
                    label = { Text("AI Backend URL (Render / Cloud / ngrok)") },
                    placeholder = { Text("https://your-service.onrender.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                val context = androidx.compose.ui.platform.LocalContext.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (backendServerUrl.isNotBlank()) {
                                com.example.ai.BackendConfig.setCustomBaseUrl(context, backendServerUrl.trim())
                            }
                            val updated = (profile ?: StudentProfileEntity()).copy(
                                name = name.trim(),
                                course = course.trim(),
                                educationLevel = educationLevel.trim(),
                                year = year.trim(),
                                mainGoal = mainGoal.trim(),
                                subjects = subjects.trim(),
                                upcomingExam = upcomingExam.trim(),
                                preferredWakeTime = wakeTime,
                                preferredSleepTime = sleepTime,
                                isOnboarded = true,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSaveProfile(updated)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
