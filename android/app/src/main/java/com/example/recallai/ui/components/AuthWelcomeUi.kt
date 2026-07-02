package com.example.recallai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun RecallLogoFloating(
    widthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "logoFloat")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    RecallLogo(
        widthDp = widthDp,
        modifier = modifier.graphicsLayer {
            translationY = (bob - 0.5f) * 10f
        }
    )
}

@Composable
private fun CartoonBuddyFace(
    modifier: Modifier = Modifier,
    waveAngle: Float = 0f
) {
    Box(modifier = modifier.size(72.dp)) {
        Surface(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.BottomStart),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = 8.dp)
                .graphicsLayer { rotationZ = waveAngle }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        )
    }
}

/**
 * Friendly cartoon pops in, says hi, waves, then leaves the screen.
 */
@Composable
fun CartoonHiGreeting(
    greeting: String,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val infinite = rememberInfiniteTransition(label = "wave")
    val wave by infinite.animateFloat(
        initialValue = -18f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAngle"
    )

    LaunchedEffect(Unit) {
        delay(350)
        show = true
        delay(2800)
        leaving = true
        delay(550)
        show = false
    }

    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(400)) + scaleIn(initialScale = 0.85f),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(450, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(350))
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer {
                if (leaving) alpha = 0.6f
            }
        ) {
            CartoonBuddyFace(waveAngle = if (leaving) 0f else wave)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = greeting,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun LoginScreenEntrance(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(650, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(500)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut()
    ) {
        content()
    }
}

@Composable
fun SignUpStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepLabels: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalSteps) { index ->
                val active = index <= currentStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Step ${currentStep + 1} of $totalSteps · ${stepLabels.getOrElse(currentStep) { "" }}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SignUpGenderPills(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("Male", "Female").forEach { option ->
            val active = selected == option
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(option) },
                shape = RoundedCornerShape(50),
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                )
            ) {
                Text(
                    text = if (active) "$option ✓" else option,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
