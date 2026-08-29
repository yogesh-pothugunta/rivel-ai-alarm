package com.example.ui.study

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelEmerald
import com.example.ui.theme.RivelRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationSheet(
    sheetState: SheetState,
    suggestedSubjects: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveTask: (
        subject: String,
        topic: String,
        description: String,
        deadline: String,
        estimatedMinutes: Int,
        priority: String,
        isPriorityToday: Boolean
    ) -> Unit
) {
    var subject by remember { mutableStateOf(suggestedSubjects.firstOrNull() ?: "") }
    var topic by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("Today") }
    var estimatedMinutes by remember { mutableIntStateOf(30) }
    var priority by remember { mutableStateOf("HIGH") }
    var isPriorityToday by remember { mutableStateOf(true) }

    val minuteOptions = listOf(15, 25, 45, 60, 90)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Add Study Task",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subject
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject / Course") },
                placeholder = { Text("e.g. Data Structures, Physics, History") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_subject_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (suggestedSubjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedSubjects.take(3).forEach { subj ->
                        FilterChip(
                            selected = subject.equals(subj, ignoreCase = true),
                            onClick = { subject = subj },
                            label = { Text(subj) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Topic / Title
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic / Chapter") },
                placeholder = { Text("e.g. Binary Search Trees & AVL rotations") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_topic_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Specific Task Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Specific Action / Practice Goal") },
                placeholder = { Text("e.g. Solve 5 LeetCode problems & review lecture slides") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Estimated Duration
            Text(
                text = "Estimated Time Required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                minuteOptions.forEach { mins ->
                    FilterChip(
                        selected = estimatedMinutes == mins,
                        onClick = { estimatedMinutes = mins },
                        label = { Text("$mins m") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Level
            Text(
                text = "Priority",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("HIGH", "MEDIUM", "LOW").forEach { p ->
                    val color = when (p) {
                        "HIGH" -> RivelRed
                        "MEDIUM" -> RivelAmber
                        else -> RivelEmerald
                    }
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Today Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mark as Today's Priority",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pins this task directly to your Home Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPriorityToday,
                    onCheckedChange = { isPriorityToday = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSaveTask(
                            subject.ifBlank { "General" },
                            topic.ifBlank { "Study Session" },
                            description,
                            deadline,
                            estimatedMinutes,
                            priority,
                            isPriorityToday
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_task_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Add Task")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
