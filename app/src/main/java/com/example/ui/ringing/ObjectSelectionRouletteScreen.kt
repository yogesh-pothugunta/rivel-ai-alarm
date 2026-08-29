package com.example.ui.ringing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mission.camera.WakeObject
import com.example.mission.camera.WakeObjectPool
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelIndigo
import kotlinx.coroutines.delay

@Composable
fun ObjectSelectionRouletteScreen(
    targetObject: WakeObject,
    onOpenCamera: (WakeObject) -> Unit
) {
    val pool = remember { WakeObjectPool.ALL_OBJECTS }
    var isSpinning by remember { mutableStateOf(true) }
    var currentItemIndex by remember { mutableIntStateOf(0) }

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "roulette_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Slot-machine roulette spinning effect
    LaunchedEffect(targetObject) {
        isSpinning = true
        var stepDelay = 60L
        val totalSteps = 22

        for (i in 0 until totalSteps) {
            currentItemIndex = (currentItemIndex + 1) % pool.size
            delay(stepDelay)
            // Gradually slow down to build anticipation
            if (i > 14) {
                stepDelay += 40L
            }
        }

        // Lock onto chosen target object
        isSpinning = false
    }

    val displayObject = if (isSpinning) pool[currentItemIndex] else targetObject

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("object_roulette_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(RivelAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        tint = RivelAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI PROOF-OF-WAKE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RivelAmber
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PROVE YOU'RE AWAKE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isSpinning) "Selecting today's random wake-up object..." else "Target locked! Physical scan required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Central Roulette Slot Wheel Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(if (!isSpinning) pulseScale else 1.0f)
                    .testTag("selected_object_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSpinning) "ROLLING OBJECT POOL..." else "TODAY'S WAKE-UP OBJECT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSpinning) MaterialTheme.colorScheme.onSurfaceVariant else RivelAmber,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Object Emoji Display
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        RivelAmber.copy(alpha = if (isSpinning) 0.15f else 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                width = 3.dp,
                                color = if (isSpinning) MaterialTheme.colorScheme.surfaceVariant else RivelAmber,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayObject.emoji,
                            fontSize = 56.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Object Name
                    Text(
                        text = displayObject.name.uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        fontWeight = FontWeight.Black,
                        color = if (isSpinning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Object description / hint
                    Text(
                        text = if (isSpinning) {
                            "📚 🥤 🪥 👟 🔑 🎒 🖊️ ☕ 🎧"
                        } else {
                            "Find this object in your room and scan it with your camera to turn off the alarm."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Bottom Action Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = !isSpinning,
                    enter = fadeIn(tween(400)) + scaleIn(tween(400))
                ) {
                    Button(
                        onClick = { onOpenCamera(targetObject) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("open_camera_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RivelAmber
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "OPEN CAMERA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (isSpinning) {
                    Text(
                        text = "Alarm sound will continue until verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
