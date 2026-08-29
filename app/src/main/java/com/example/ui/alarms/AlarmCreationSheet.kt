package com.example.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AlarmEntity
import com.example.ui.theme.RivelAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmCreationSheet(
    sheetState: SheetState,
    initialAlarm: AlarmEntity? = null,
    onDismiss: () -> Unit,
    onSaveAlarm: (
        id: Long,
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        label: String,
        missionType: String,
        missionDifficulty: String,
        targetObject: String,
        movementTargetSteps: Int,
        snoozeMinutes: Int,
        isVibrationEnabled: Boolean
    ) -> Unit
) {
    val initialHour = initialAlarm?.hour ?: 6
    val initialMinute = initialAlarm?.minute ?: 30
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    var label by remember { mutableStateOf(initialAlarm?.label ?: "Morning Study Wake-up") }
    var selectedDays by remember {
        mutableStateOf(
            if (initialAlarm != null && initialAlarm.daysOfWeek.isNotBlank()) {
                initialAlarm.daysOfWeek.split(",").map { it.trim() }.toSet()
            } else {
                setOf("MON", "TUE", "WED", "THU", "FRI")
            }
        )
    }

    var isVibrationEnabled by remember { mutableStateOf(initialAlarm?.isVibrationEnabled ?: true) }

    val daysList = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (initialAlarm == null) "Set Study Alarm" else "Edit Study Alarm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time Picker
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Alarm Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Alarm Label / Goal") },
                placeholder = { Text("e.g. Morning Study Wake-up") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alarm_label_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Repeat Days Selector
            Text(
                text = "Repeat Days",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysList.forEach { day ->
                    val isSelected = selectedDays.contains(day)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedDays = if (isSelected) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.take(1),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Informative AI Proof-of-Wake Badge / Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(RivelAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = RivelAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "AI Proof-of-Wake Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A random everyday object (water bottle, book, shoes, etc.) is chosen when the alarm rings. You must scan it to dismiss.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vibration switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vibration & Gradual Escalation",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = isVibrationEnabled,
                    onCheckedChange = { isVibrationEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save / Cancel Buttons
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
                        val daysStr = if (selectedDays.isEmpty()) "ONCE" else selectedDays.joinToString(",")
                        onSaveAlarm(
                            initialAlarm?.id ?: 0L,
                            timePickerState.hour,
                            timePickerState.minute,
                            daysStr,
                            label,
                            "OBJECT",
                            "MEDIUM",
                            "Book",
                            20,
                            0,
                            isVibrationEnabled
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_alarm_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Save Alarm")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
