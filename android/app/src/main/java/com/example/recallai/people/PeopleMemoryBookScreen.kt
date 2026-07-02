package com.example.recallai.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.R
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.QuickSwitchRow
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill
import com.example.recallai.ui.components.TonalActionButton

private val avatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043),
    Color(0xFF66BB6A), Color(0xFFEC407A), Color(0xFF26C6DA),
    Color(0xFFFFA726)
)

private fun avatarColor(name: String): Color =
    avatarPalette[Math.abs(name.hashCode()) % avatarPalette.size]

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

@Composable
fun PeopleMemoryBookScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: PeopleMemoryBookViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { RecallTopBar(title = "People Directory", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HeroHeaderCard(
                    title = "People Directory",
                    subtitle = "Save faces, relations & contacts for instant recall",
                    illustrationRes = R.drawable.img_tool_face
                )

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Total", state.people.size.toString())
                    StatPill("Relations", state.people.count { it.relation.isNotBlank() }.toString())
                    StatPill("With Phone", state.people.count { it.phone.isNotBlank() }.toString())
                    StatPill("With Notes", state.people.count { it.note.isNotBlank() }.toString())
                }

                QuickSwitchRow(
                    onHome = onNavigateHome,
                    onChat = onNavigateChat,
                    onFace = onNavigateFace,
                    onMemories = onNavigateMemories,
                    onRecall = onNavigateRecall
                )

                // Add / Edit form
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(if (state.isEditing) "Edit Person" else "Add Person")
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.relationInput,
                        onValueChange = viewModel::onRelationChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Relation") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(6.dp))

                    // Quick-fill relation chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Mother", "Father", "Sibling", "Doctor", "Friend", "Caregiver").forEach { rel ->
                            SuggestionChip(
                                onClick = { viewModel.onRelationChange(rel) },
                                label = { Text(rel, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.phoneInput,
                        onValueChange = viewModel::onPhoneChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes / Context") },
                        minLines = 2,
                        maxLines = 4
                    )

                    if (!state.info.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.info ?: "",
                            color = if (state.info?.contains("removed", ignoreCase = true) == true)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryActionButton(
                            text = if (state.isEditing) "Save Changes" else "Add to Directory",
                            onClick = viewModel::savePerson,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedVisibility(visible = state.isEditing) {
                            TonalActionButton(
                                text = "Cancel",
                                onClick = viewModel::cancelEdit
                            )
                        }
                    }
                }

                // Search bar
                AnimatedVisibility(
                    visible = state.people.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search by name, relation or note") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchChange("") }) {
                                        Icon(Icons.Default.Clear, "Clear search")
                                    }
                                }
                            },
                            singleLine = true
                        )
                    }
                }

                // People list
                val displayed = state.filtered
                if (displayed.isEmpty() && state.people.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No people match \"${state.searchQuery}\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                displayed.forEach { person ->
                    PersonCard(
                        person = person,
                        isBeingEdited = state.editingId == person.id,
                        onEdit = { viewModel.startEdit(person) },
                        onRemove = { viewModel.removePerson(person.id) },
                        onEnrollFace = onNavigateFace
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: com.example.recallai.data.KnownPersonItem,
    isBeingEdited: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onEnrollFace: () -> Unit
) {
    val color = avatarColor(person.name)

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(person.name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (person.relation.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = person.relation,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (person.phone.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = person.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (person.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = person.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(if (isBeingEdited) "Editing..." else "Edit", fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isBeingEdited)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    )
                    AssistChip(
                        onClick = onEnrollFace,
                        label = { Text("Enroll Face", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.FaceRetouchingNatural, null, modifier = Modifier.size(14.dp))
                        }
                    )
                    AssistChip(
                        onClick = onRemove,
                        label = { Text("Remove", fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}
