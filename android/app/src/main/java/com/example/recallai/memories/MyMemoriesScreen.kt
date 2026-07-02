@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.memories

import com.example.recallai.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.MemoryListSkeleton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.memoryTypeChipLabel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMemoriesScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    onMemoryClick: (MemoryEntity) -> Unit = {},
    viewModel: MemoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("ALL") }

    val filterTypes = remember(state.memories) {
        buildList {
            add("ALL")
            addAll(
                state.memories
                    .map { it.type.uppercase(Locale.getDefault()) }
                    .distinct()
                    .sorted()
            )
        }
    }

    val filtered = remember(state.memories, query, selectedType) {
        state.memories.filter { memory ->
            val matchesType = selectedType == "ALL" || memory.type.equals(selectedType, ignoreCase = true)
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                    memory.text.lowercase().contains(q) ||
                    (memory.title?.lowercase()?.contains(q) == true) ||
                    (memory.tags?.lowercase()?.contains(q) == true)
            matchesType && matchesQuery
        }
    }

    AppBackdrop() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                RecallTopBar(
                    title = "Memories",
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
            Text(
                text = "Timeline",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MemorySummaryTile(
                    label = "Total",
                    value = state.memories.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                MemorySummaryTile(
                    label = "Showing",
                    value = filtered.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                MemorySummaryTile(
                    label = "Types",
                    value = filterTypes.count { it != "ALL" }.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    placeholder = { Text("Search memories…") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                IconButton(
                    onClick = viewModel::loadMemories,
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh memories",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionTitle("Filter by type")
            Spacer(Modifier.height(8.dp))

            FilterRow(
                selected = selectedType,
                onSelect = { selectedType = it },
                availableTypes = filterTypes
            )

            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Loading your memories…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        MemoryListSkeleton()
                    }
                }

                state.error != null -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                filtered.isEmpty() -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ill_patient_caregiver),
                            contentDescription = "Memory empty state",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.large),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "No memories found yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Try a chat session or voice capture to begin your memory story.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        filtered.forEach { memory ->
                            MemoryCard(
                                memory = memory,
                                dateFormat = dateFormat,
                                onClick = { onMemoryClick(memory) }
                            )
                        }
                        Spacer(Modifier.height(96.dp))
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MemorySummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilterRow(
    selected: String,
    onSelect: (String) -> Unit,
    availableTypes: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableTypes.forEach { type ->
            val isSelected = selected == type
            val label = when (type) {
                "ALL" -> "All"
                else -> memoryTypeChipLabel(type)
            }
            AnimatedAssistChip(
                label = if (isSelected) "• $label" else label,
                onClick = { onSelect(type) }
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!memory.title.isNullOrBlank()) {
                Text(
                    text = memory.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = memory.text,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = memory.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = dateFormat.format(Date(memory.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!memory.tags.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tags: ${memory.tags}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}