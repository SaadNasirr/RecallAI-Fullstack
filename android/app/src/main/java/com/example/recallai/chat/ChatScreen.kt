@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.reminders.ReminderDraft
import com.example.recallai.reminders.ReminderUiFormatter
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.dashboard.MindcareColors
import com.example.recallai.ui.dashboard.MindcareGradientBackground
import com.example.recallai.voice.AudioRecorder
import com.example.recallai.voice.RemoteSpeechPlayer
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MessengerBlue = Color(0xFF1E88E5)
private val MessengerBottomBarBg = Color.White.copy(alpha = 0.92f)
private val AssistantBubbleGray = Color(0xFFE0E0E0)
private val AssistantTextDark = Color(0xFF212121)

private val TherapistEmojiPanelHeight = 280.dp

private val TherapistQuickEmojis = listOf(
    "😀", "😂", "🥰", "😊", "😢", "😰", "😴", "🤗",
    "👍", "👎", "🙏", "💪", "❤️", "✨", "🔥", "💬",
    "🙂", "😅", "😇", "🤔", "😮", "😌", "🥺", "😤",
    "🎉", "🙌", "👋", "💙", "💚", "💛", "🧡", "💜"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    onNavigateWhereAmI: () -> Unit = {},
    onNavigateObjectLocator: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val recorder = remember { AudioRecorder() }
    val scope = rememberCoroutineScope()
    val isReadingAloud by RemoteSpeechPlayer.isReadingAloud.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val messageFocusRequester = remember { FocusRequester() }
    var emojiPanelOpen by remember { mutableStateOf(false) }
    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val reminderTimeFmt = remember {
        DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
    }
    var isRecording by remember { mutableStateOf(false) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }
    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
    }
    LaunchedEffect(state.patientNavigation) {
        when (state.patientNavigation) {
            ChatPatientNavigation.WhereAmI -> {
                onNavigateWhereAmI()
                viewModel.clearPatientNavigation()
            }
            ChatPatientNavigation.Recall -> {
                onNavigateRecall()
                viewModel.clearPatientNavigation()
            }
            ChatPatientNavigation.ObjectLocator -> {
                onNavigateObjectLocator()
                viewModel.clearPatientNavigation()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.messages.size) {
        val lastIndex = state.messages.lastIndex
        if (lastIndex >= 0) {
            listState.scrollToItem(lastIndex)
        }
    }

    LaunchedEffect(state.input) {
        if (state.input != inputField.text) {
            inputField = TextFieldValue(state.input, TextRange(state.input.length))
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            emojiPanelOpen = false
            val last = state.messages.lastIndex
            if (last >= 0) {
                listState.animateScrollToItem(last)
            }
        }
    }

    BackHandler(enabled = emojiPanelOpen) {
        emojiPanelOpen = false
    }

    fun insertEmojiAtCursor(emoji: String) {
        val sel = inputField.selection
        val start = min(sel.start, sel.end).coerceIn(0, inputField.text.length)
        val end = max(sel.start, sel.end).coerceIn(0, inputField.text.length)
        val newText = inputField.text.replaceRange(start, end, emoji)
        val caret = (start + emoji.length).coerceIn(0, newText.length)
        inputField = TextFieldValue(newText, TextRange(caret))
        viewModel.onInputChange(newText)
    }

    fun startRecording() {
        if (!micGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        scope.launch {
            val started = withContext(Dispatchers.IO) {
                runCatching { recorder.start(context.cacheDir) }.isSuccess
            }
            isRecording = started
        }
    }

    fun stopAndSendRecording() {
        scope.launch {
            val file: File? = withContext(Dispatchers.IO) { recorder.stop() }
            isRecording = false
            if (file != null) {
                viewModel.sendVoiceMessage(file)
            }
        }
    }

    MindcareGradientBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                MessengerChatTopBar(onBack = onBack)
            }
        ) { padding ->
        state.pendingReminder?.let { draft ->
            ReminderConfirmDialog(
                draft = draft,
                onDismiss = { viewModel.dismissReminderDraft() },
                onConfirm = { title, desc, whenMs, warn10 ->
                    viewModel.confirmSaveReminder(
                        title = title,
                        description = desc,
                        datetime = whenMs,
                        warn10Min = warn10
                    )
                },
                formatWhen = { ms ->
                    reminderTimeFmt.format(
                        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                    )
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (state.error != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (state.info != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.info.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val showJumpToLatest by remember {
                    derivedStateOf {
                        val last = state.messages.lastIndex
                        if (last < 0) false else (listState.firstVisibleItemIndex < last - 1)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (emojiPanelOpen) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(onTap = { emojiPanelOpen = false })
                                }
                            } else {
                                Modifier
                            }
                        ),
                    state = listState,
                    userScrollEnabled = !emojiPanelOpen,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = AssistantBubbleGray.copy(alpha = 0.65f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Your conversation starts here",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AssistantTextDark
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Type below or use the microphone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AssistantTextDark.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = if (msg.fromUser) {
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = 18.dp,
                                        bottomEnd = 4.dp
                                    )
                                } else {
                                    RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 18.dp,
                                        bottomEnd = 18.dp,
                                        bottomStart = 18.dp
                                    )
                                },
                                color = if (msg.fromUser) MessengerBlue else AssistantBubbleGray,
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.fromUser) Color.White else AssistantTextDark,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                            Text(
                                text = timeFmt.format(Date(msg.createdAt)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AssistantTextDark.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                    if (state.isSending) {
                        item {
                            TherapistTypingBubble()
                        }
                    }
                }
                if (showJumpToLatest) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val last = state.messages.lastIndex
                                    if (last >= 0) listState.animateScrollToItem(last)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Jump to latest"
                            )
                        }
                    }
                }
            }

            if (emojiPanelOpen) {
                TherapistEmojiPickerPanel(
                    onPickEmoji = { insertEmojiAtCursor(it) }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MessengerBottomBarBg,
                tonalElevation = 2.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextField(
                            value = inputField,
                            onValueChange = { tfv ->
                                inputField = tfv
                                viewModel.onInputChange(tfv.text)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .focusRequester(messageFocusRequester)
                                .onFocusChanged { focus ->
                                    if (focus.isFocused) {
                                        emojiPanelOpen = false
                                    }
                                },
                            placeholder = {
                                Text(
                                    "Message…",
                                    color = AssistantTextDark.copy(alpha = 0.38f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                disabledContainerColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MessengerBlue
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = false,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AssistantTextDark),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (!state.isSending && state.input.trim().isNotEmpty()) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.send()
                                    }
                                }
                            )
                        )
                        IconButton(
                            onClick = {
                                if (emojiPanelOpen) {
                                    emojiPanelOpen = false
                                    messageFocusRequester.requestFocus()
                                    keyboardController?.show()
                                } else {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    emojiPanelOpen = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (emojiPanelOpen) {
                                    Icons.Filled.Keyboard
                                } else {
                                    Icons.Outlined.Mood
                                },
                                contentDescription = if (emojiPanelOpen) {
                                    "Show keyboard"
                                } else {
                                    "Emoji"
                                },
                                tint = AssistantTextDark.copy(alpha = 0.65f)
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (!isRecording) startRecording() else stopAndSendRecording()
                            },
                            enabled = !state.isSending
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Voice",
                                tint = if (isRecording) MessengerBlue else AssistantTextDark.copy(alpha = 0.65f)
                            )
                        }
                    }
                    if (state.messages.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedAssistChip(
                                label = "Help me calm down",
                                onClick = {
                                    viewModel.onInputChange("I feel anxious today, help me calm down.")
                                    viewModel.send()
                                }
                            )
                            AnimatedAssistChip(
                                label = "Summarize this week",
                                onClick = {
                                    viewModel.onInputChange("Can you summarize how I sounded this week?")
                                    viewModel.send()
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedAssistChip(
                                label = when {
                                    isReadingAloud -> "Stop audio"
                                    else -> "Read aloud"
                                },
                                onClick = {
                                    if (isReadingAloud) {
                                        RemoteSpeechPlayer.stop()
                                    } else {
                                        val latest = state.messages.lastOrNull { !it.fromUser }?.text.orEmpty()
                                        if (latest.isNotBlank()) {
                                            scope.launch { runCatching { RemoteSpeechPlayer.speak(context, latest) } }
                                        }
                                    }
                                },
                                enabled = isReadingAloud || state.messages.any { !it.fromUser }
                            )
                            AnimatedAssistChip(
                                label = "Copy reply",
                                onClick = {
                                    val latest = state.messages.lastOrNull { !it.fromUser }?.text.orEmpty()
                                    if (latest.isNotBlank()) clipboard.setText(AnnotatedString(latest))
                                }
                            )
                            AnimatedAssistChip(
                                label = "Save reply",
                                onClick = { viewModel.saveLatestAssistantMessage() }
                            )
                            AnimatedAssistChip(
                                label = "Clear chat",
                                onClick = { viewModel.clearConversationView() }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TherapistEmojiPickerPanel(
    onPickEmoji: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(TherapistEmojiPanelHeight),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = "Emoji",
                style = MaterialTheme.typography.labelLarge,
                color = AssistantTextDark.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                TherapistQuickEmojis.chunked(8).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPickEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessengerChatTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MindcareColors.NavPill)
            .statusBarsPadding()
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Therapist Chatbot",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
    }
}

@Composable
private fun TherapistTypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp,
                bottomStart = 18.dp
            ),
            color = AssistantBubbleGray,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Therapist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = AssistantTextDark
                )
                TherapistTypingDots()
            }
        }
    }
}

@Composable
private fun TherapistTypingDots() {
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = index * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MessengerBlue.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ReminderConfirmDialog(
    draft: ReminderDraft,
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String?, whenMs: Long, warn10Min: Boolean) -> Unit,
    formatWhen: (Long) -> String
) {
    var title by remember(draft.title) { mutableStateOf(draft.title) }
    var desc by remember(draft.description) { mutableStateOf(draft.description ?: "") }
    var warn10 by remember(draft.warn10Min) { mutableStateOf(draft.warn10Min) }

    val supporting = if (draft.ambiguous) {
        "We guessed the date/time. Confirm before saving."
    } else {
        "Looks like a time-based task. Save as a reminder?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, desc.takeIf { it.isNotBlank() }, draft.datetime, warn10) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
        title = { Text("Save reminder?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("When: ${formatWhen(draft.datetime)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (draft.repeatMode != ReminderRepeatMode.NONE) {
                    val rep = when (draft.repeatMode) {
                        ReminderRepeatMode.DAILY -> "Daily"
                        ReminderRepeatMode.WEEKLY ->
                            ReminderUiFormatter.maskToLabel(draft.daysOfWeekMask).ifBlank { "Weekly" }
                        else -> ""
                    }
                    Text(
                        "Repeat: $rep",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    maxLines = 3
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Switch(checked = warn10, onCheckedChange = { warn10 = it })
                    Text("Warn 10 minutes before", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    )
}
