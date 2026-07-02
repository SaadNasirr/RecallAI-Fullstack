package com.example.recallai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MindcareColors {
    val Ink = Color(0xFF121212)
    val CanvasTop = Color(0xFFE8E4FF)
    val CanvasBottom = Color(0xFFFDFBFF)
    val CardPink = Color(0xFFFFE4EF)
    val CardLavender = Color(0xFFE8E4FE)
    val CardMint = Color(0xFFCCF4F1)
    val CardPeach = Color(0xFFFFF0E6)
    val MoodAngry = Color(0xFFFF6B6B)
    val MoodSad = Color(0xFF4DC9C0)
    val MoodNeutral = Color(0xFF7B9EFF)
    val MoodHappy = Color(0xFFFFD166)
    val MoodGreat = Color(0xFF6BCB77)
    val NavPill = Color(0xFF121212)
}

@Composable
fun MindcareGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MindcareColors.CanvasTop,
                        MindcareColors.CanvasBottom,
                        Color.White
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-30).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        content()
    }
}

@Composable
fun MindcareCircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    contentDescription: String? = null
) {
    val button = Surface(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = MindcareColors.Ink)
        }
    }
    if (badgeCount > 0) {
        BadgedBox(
            badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }
        ) { button }
    } else {
        button
    }
}

@Composable
fun MindcareProfileHeader(
    displayName: String,
    subtitle: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clickable(onClick = onProfileClick)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MindcareColors.CardLavender
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MindcareColors.Ink)
                }
            }
            Column {
                Text(
                    text = "Hi, $displayName",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = MindcareColors.Ink
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MindcareColors.Ink.copy(alpha = 0.65f)
                )
            }
        }
        trailing()
    }
}

@Composable
fun MindcareFilterPills(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { label ->
            val active = label == selected
            Surface(
                modifier = Modifier.clickable { onSelected(label) },
                shape = RoundedCornerShape(50),
                color = if (active) MindcareColors.Ink else Color.White,
                border = if (active) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MindcareColors.Ink.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = if (active) Color.White else MindcareColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun MindcareSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MindcareColors.Ink.copy(alpha = 0.45f))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = MindcareColors.Ink,
                    fontSize = 16.sp
                ),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(placeholder, color = MindcareColors.Ink.copy(alpha = 0.4f))
                    }
                    inner()
                }
            )
        }
    }
}

data class MindcareMoodOption(val key: String, val emoji: String, val color: Color)

@Composable
fun MindcareMoodRow(
    options: List<MindcareMoodOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEach { opt ->
            val selected = opt.key == selectedKey
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onSelect(opt.key) },
                shape = CircleShape,
                color = opt.color.copy(alpha = if (selected) 1f else 0.35f),
                border = if (selected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MindcareColors.Ink)
                } else {
                    null
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(opt.emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun MindcareActivityCard(
    title: String,
    icon: ImageVector,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(140.dp)
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = background
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MindcareColors.Ink.copy(alpha = 0.75f))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink
            )
        }
    }
}

@Composable
fun MindcarePrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MindcareColors.Ink
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun MindcareSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MindcareColors.Ink
    )
}

data class MindcareNavTab(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val badge: Int = 0
)

@Composable
fun MindcareFloatingNavBar(
    tabs: List<MindcareNavTab>,
    selectedRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(64.dp),
        shape = RoundedCornerShape(50),
        color = MindcareColors.NavPill,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == selectedRoute
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onTabSelected(tab.route) },
                    contentAlignment = Alignment.Center
                ) {
                    if (tab.badge > 0) {
                        BadgedBox(badge = { Badge { Text(tab.badge.coerceAtMost(99).toString()) } }) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) MindcareColors.Ink else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MindcareColors.Ink else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MindcareSimpleCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
