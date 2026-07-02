package com.example.recallai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft pulse shimmer for calm loading states (no flashy strobing).
 */
@Composable
fun ShimmerBar(
    modifier: Modifier = Modifier,
    heightDp: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            .semantics { contentDescription = "Loading" }
    )
}

@Composable
fun MemoryListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(5) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBar(heightDp = 18.dp, modifier = Modifier.fillMaxWidth(0.55f))
                ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth())
                ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth(0.85f))
            }
        }
    }
}

@Composable
fun RecallAnswerSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShimmerBar(heightDp = 20.dp, modifier = Modifier.fillMaxWidth(0.45f))
        ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth(0.35f))
        Spacer(Modifier.height(8.dp))
        ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth())
        ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth())
        ShimmerBar(heightDp = 14.dp, modifier = Modifier.fillMaxWidth(0.92f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
            }
        }
    }
}

@Composable
fun LoadingPhaseCaption(
    phaseLabel: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = phaseLabel,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 8.dp)
    )
}
