package com.example.recallai.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.RecallAiPreferences
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionRefresh++ }

    val postNotificationsRuntimeOk =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    val systemNotificationsOn =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    var activityRemindersOn by remember(permissionRefresh) {
        mutableStateOf(RecallAiPreferences.isNotifyEnabled(context))
    }
    LaunchedEffect(permissionRefresh) {
        activityRemindersOn = RecallAiPreferences.isNotifyEnabled(context)
    }

    val alarmMgr = remember { context.getSystemService(AlarmManager::class.java)!! }
    val exactAlarmsOk =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmMgr.canScheduleExactAlarms()
        else true

    AppBackdrop() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { RecallTopBar(title = "Reminders", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Alerts & visibility",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Reminder pops only if Android allows notifications + (on newer phones) exact alarms. Use the checklist below so nothing is silent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusLine(
                        ok = activityRemindersOn,
                        label = "RecallAI “Activity reminders” in Settings menu"
                    )
                    StatusLine(
                        ok = postNotificationsRuntimeOk,
                        label = "POST_NOTIFICATIONS permission (Android 13+)"
                    )
                    StatusLine(
                        ok = systemNotificationsOn,
                        label = "App notifications enabled in system Settings"
                    )
                    StatusLine(
                        ok = exactAlarmsOk,
                        label = "Alarms & reminders (exact time) — Settings → Apps → RecallAI"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Reminder alerts on",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = activityRemindersOn,
                            onCheckedChange = { v ->
                                RecallAiPreferences.setNotifyEnabled(context, v)
                                activityRemindersOn = v
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (Build.VERSION.SDK_INT >= 33 && !postNotificationsRuntimeOk) {
                        PrimaryActionButton(
                            text = "Allow notification permission",
                            onClick = {
                                notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!systemNotificationsOn) {
                        TextButton(
                            onClick = {
                                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                runCatching { context.startActivity(i) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open app notification settings")
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsOk) {
                        TextButton(
                            onClick = {
                                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                runCatching { context.startActivity(i) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow exact alarms (needed for on-time reminders)")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "Send test notification",
                        onClick = {
                            ReminderNotificationController.ensureChannel(context)
                            ReminderNotificationController.postTestNotification(context)
                            permissionRefresh++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activityRemindersOn &&
                            postNotificationsRuntimeOk &&
                            systemNotificationsOn
                    )
                    if (!activityRemindersOn || !postNotificationsRuntimeOk || !systemNotificationsOn) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Fix the red items above first — then test again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                SectionTitle("Upcoming")
                if (ui.upcoming.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No upcoming reminders yet. When you mention a time in chat (e.g., “doctor at 8pm”), RecallAI will offer to save it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ui.upcoming.take(50).forEach { r ->
                        ReminderRow(
                            reminder = r,
                            onToggleDone = { viewModel.toggleDone(r) },
                            onDelete = { viewModel.delete(r) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionTitle("Completed")
                if (ui.completed.isEmpty()) {
                    Text("Nothing completed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ui.completed.take(20).forEach { r ->
                        ReminderRow(
                            reminder = r,
                            onToggleDone = { viewModel.toggleDone(r) },
                            onDelete = { viewModel.delete(r) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ok) "✓" else "✗",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = reminder.status == ReminderStatus.COMPLETED
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Filled.Done else Icons.Filled.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        ReminderUiFormatter.formatReminderListSubtitle(reminder),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    reminder.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onToggleDone) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Toggle done",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

