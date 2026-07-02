package com.example.recallai.geofence

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.remote.GeofenceEventResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceAlertHistoryScreen(
    onBack: () -> Unit,
    viewModel: GeofenceAlertHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }

    val formatter = remember {
        DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone activity", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.load() }) {
                        Text("Refresh", fontSize = 18.sp)
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                state.error ?: "",
                fontSize = 18.sp,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            state.events.isEmpty() -> Text(
                "No activity yet.",
                fontSize = 18.sp,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.events, key = { it._id }) { ev ->
                    val entered = ev.eventType == "entered"
                    val tint = if (entered) Color(0xFF2E7D32) else Color(0xFFC62828)
                    val timeLabel = runCatching {
                        formatter.format(Instant.parse(ev.triggeredAt))
                    }.getOrElse { ev.triggeredAt }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = tint
                            )
                            Column(Modifier.weight(1f)) {
                                Text(ev.zoneName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (entered) "Entered" else "Exited",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(timeLabel, fontSize = 18.sp)
                            }
                        }
                        TextButton(
                            onClick = {
                                val u = Uri.parse(
                                    "geo:${ev.location.lat},${ev.location.lng}?q=${ev.location.lat},${ev.location.lng}"
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW, u))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                        ) {
                            Text("View on Map", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
