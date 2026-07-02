package com.example.recallai.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.AuthManager
import com.example.recallai.ui.components.RecallSettingsMenu
import com.example.recallai.ui.components.SecondaryActionButton
import com.example.recallai.ui.components.memoryAccentForType
import com.example.recallai.ui.components.memoryTypeChipLabel
import com.example.recallai.ui.patient.PatientAssignedCareTasksSection
import com.example.recallai.ui.patient.PatientScheduleSection
import com.example.recallai.ui.screens.PatientHomeViewModel

private data class PatientActivityItem(
    val title: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val keywords: List<String>,
    val onClick: () -> Unit
)

@Composable
fun MindcarePatientHomeScreen(
    onBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToRecallAssistant: () -> Unit,
    onNavigateToObjectLocator: () -> Unit,
    onNavigateToFaceInsights: () -> Unit,
    onNavigateToGeofencing: () -> Unit,
    onNavigateToMedication: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToPeopleBook: () -> Unit,
    onNavigateToDemoMode: () -> Unit,
    onNavigateToRoutine: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToConnectCaregiver: () -> Unit,
    onNavigateToFlashcard: () -> Unit,
    onLogout: () -> Unit,
    viewModel: PatientHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("Today") }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val displayName = AuthManager.userName?.takeIf { it.isNotBlank() } ?: "Patient"
    val moodOptions = remember {
        listOf(
            MindcareMoodOption("angry", "😠", MindcareColors.MoodAngry),
            MindcareMoodOption("sad", "😢", MindcareColors.MoodSad),
            MindcareMoodOption("neutral", "😐", MindcareColors.MoodNeutral),
            MindcareMoodOption("happy", "🙂", MindcareColors.MoodHappy),
            MindcareMoodOption("excited", "😄", MindcareColors.MoodGreat)
        )
    }

    val activities = remember(
        onNavigateToChat,
        onNavigateToMedication,
        onNavigateToRoutine,
        onNavigateToEmergency,
        onNavigateToGeofencing,
        onNavigateToFlashcard,
        onNavigateToRecallAssistant,
        onNavigateToObjectLocator,
        onNavigateToFaceInsights,
        onNavigateToPeopleBook,
        onNavigateToReminders,
        onNavigateToMemories,
        onNavigateToDemoMode
    ) {
        listOf(
            PatientActivityItem("Therapist Chat", Icons.AutoMirrored.Filled.Chat, MindcareColors.CardPink, listOf("chat", "ai", "therapist"), onNavigateToChat),
            PatientActivityItem("Medication", Icons.Filled.MedicalServices, MindcareColors.CardLavender, listOf("med", "pill", "dose"), onNavigateToMedication),
            PatientActivityItem("Smart Routine", Icons.Filled.Schedule, MindcareColors.CardMint, listOf("routine", "habit"), onNavigateToRoutine),
            PatientActivityItem("Emergency", Icons.Filled.Warning, MindcareColors.CardPink, listOf("sos", "urgent"), onNavigateToEmergency),
            PatientActivityItem("Where am I?", Icons.Filled.LocationOn, MindcareColors.CardLavender, listOf("geo", "home", "map"), onNavigateToGeofencing),
            PatientActivityItem("Memory Games", Icons.Filled.Psychology, MindcareColors.CardPeach, listOf("flash", "game"), onNavigateToFlashcard),
            PatientActivityItem("Semantic Recall", Icons.Filled.SelfImprovement, MindcareColors.CardMint, listOf("recall", "memory"), onNavigateToRecallAssistant),
            PatientActivityItem("Object AI", Icons.Filled.Visibility, MindcareColors.CardLavender, listOf("object", "vision"), onNavigateToObjectLocator),
            PatientActivityItem("Face Insights", Icons.Filled.Face, MindcareColors.CardPink, listOf("face"), onNavigateToFaceInsights),
            PatientActivityItem("People Book", Icons.Filled.Group, MindcareColors.CardPeach, listOf("people", "contact"), onNavigateToPeopleBook),
            PatientActivityItem("Reminders", Icons.Filled.Notifications, MindcareColors.CardMint, listOf("remind", "alarm"), onNavigateToReminders),
            PatientActivityItem("Memories", Icons.Filled.Psychology, MindcareColors.CardLavender, listOf("memo", "timeline"), onNavigateToMemories),
            PatientActivityItem("Demo Mode", Icons.Filled.Visibility, MindcareColors.CardPeach, listOf("demo"), onNavigateToDemoMode)
        )
    }

    val filteredActivities = remember(searchQuery, activities) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) activities
        else activities.filter { item ->
            item.title.lowercase().contains(q) || item.keywords.any { it.contains(q) }
        }
    }

    MindcareGradientBackground {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MindcareProfileHeader(
                    displayName = displayName,
                    subtitle = "How are you doing today?",
                    onProfileClick = onBack,
                    trailing = {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = androidx.compose.ui.graphics.Color.White,
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MindcareColors.Ink.copy(alpha = 0.08f)
                            )
                        ) {
                            RecallSettingsMenu(
                                roleTitle = "Patient",
                                onNavigatePrivacy = onNavigateToPrivacy,
                                onLogout = onLogout,
                                onRefreshDashboard = viewModel::refresh
                            )
                        }
                    }
                )
                MindcareFilterPills(
                    options = listOf("Today", "Next week", "Next month"),
                    selected = period,
                    onSelected = { period = it }
                )
                MindcareSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search tools & activities"
                )
                MindcareSectionTitle("Daily mood")
                MindcareMoodRow(
                    options = moodOptions,
                    selectedKey = state.todayMood,
                    onSelect = viewModel::submitMood
                )
                MindcareSectionTitle("Activities")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredActivities, key = { it.title }) { item ->
                        MindcareActivityCard(
                            title = item.title,
                            icon = item.icon,
                            background = item.color,
                            onClick = item.onClick
                        )
                    }
                }
                MindcarePrimaryPillButton(text = "Link caregiver", onClick = onNavigateToConnectCaregiver)
                PatientScheduleSection(
                    state = state,
                    onAddAlarm = viewModel::addAlarm,
                    onDeleteAlarm = viewModel::deleteAlarm,
                    onAddReminder = viewModel::addReminder,
                    modifier = Modifier.fillMaxWidth()
                )
                PatientAssignedCareTasksSection(
                    tasks = state.assignedCareTasks,
                    onDone = viewModel::markAssignedCareTaskDone,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MindcareSectionTitle("Recent memories")
                    SecondaryActionButton("All", onClick = onNavigateToMemories)
                }
                val recent = state.recent.take(3)
                if (recent.isEmpty()) {
                    MindcareSimpleCard {
                        Text(
                            "No memories yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    recent.forEach { item ->
                        MindcareSimpleCard {
                            Text(
                                memoryTypeChipLabel(item.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = memoryAccentForType(item.type)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.title ?: "Memory",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            if (!item.text.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}
