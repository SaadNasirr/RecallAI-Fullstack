@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.example.recallai.BuildConfig
import com.example.recallai.R
import com.example.recallai.data.AuthManager
import com.example.recallai.data.HapticsConfig
import com.example.recallai.data.RecallAiPreferences

private val CardElevation = 5.dp
private val FeatureCardElevation = 6.dp
private val ContentPadding = 16.dp
private val FeaturePadding = 16.dp
private val PrimaryButtonHeight = 46.dp
private val HeroImageHeight = 136.dp
private const val FastMotionMs = 140
private const val SlowMotionMs = 220

private fun androidx.compose.ui.hapticfeedback.HapticFeedback.lightTap() {
    if (HapticsConfig.reduceHaptics) return
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

private fun androidx.compose.ui.hapticfeedback.HapticFeedback.strongTap() {
    // Keep feedback snappy; LongPress vibration can feel laggy on repeated taps.
    lightTap()
}

/** Transparent brain logo — width fixed, natural height. No background, tint, or colorFilter. */
@Composable
fun RecallLogo(
    widthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "RecallAI logo"
) {
    Image(
        painter = painterResource(R.drawable.recallai_logo),
        contentDescription = contentDescription,
        modifier = modifier
            .width(widthDp)
            .wrapContentHeight(),
        contentScale = ContentScale.Fit
    )
}

/** Splash: 240dp wide, wrap content height. */
@Composable
fun RecallBrandLogoSplash(
    modifier: Modifier = Modifier,
    contentDescription: String = "RecallAI logo"
) {
    RecallLogo(widthDp = 240.dp, modifier = modifier, contentDescription = contentDescription)
}

/** Toolbar leading icon: 44dp × 44dp box, brain fit inside. */
@Composable
fun RecallBrandLogoToolbar(
    modifier: Modifier = Modifier,
    contentDescription: String = "RecallAI logo"
) {
    Image(
        painter = painterResource(R.drawable.recallai_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(44.dp),
        contentScale = ContentScale.Fit
    )
}

/** Welcome / onboarding: 200dp wide, wrap content height. */
@Composable
fun RecallBrandLogoOnboarding(
    modifier: Modifier = Modifier,
    contentDescription: String = "RecallAI logo"
) {
    RecallLogo(widthDp = 200.dp, modifier = modifier, contentDescription = contentDescription)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // Reference: white surface, very fine bottom divider, clean minimal look
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawLine(
                        color = Color(0xFFEBE8FF),
                        start = Offset(0f, size.height - stroke),
                        end = Offset(size.width, size.height - stroke),
                        strokeWidth = stroke
                    )
                }
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RecallBrandLogoToolbar()
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.1).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallSettingsMenu(
    roleTitle: String,
    onNavigatePrivacy: (() -> Unit)?,
    onLogout: () -> Unit,
    onRefreshDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        RecallAiPreferences.syncHapticsFromDisk(context)
    }

    var expanded by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var notifyEnabled by remember {
        mutableStateOf(RecallAiPreferences.isNotifyEnabled(context))
    }
    var reduceHaptics by remember {
        mutableStateOf(RecallAiPreferences.isReduceHaptics(context))
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            notifyEnabled = RecallAiPreferences.isNotifyEnabled(context)
            reduceHaptics = RecallAiPreferences.isReduceHaptics(context)
        }
    }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 272.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileMenuAvatar(size = 44.dp)
                    Column {
                        Text(
                            text = "Signed in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AuthManager.userName?.takeIf { it.isNotBlank() } ?: "User",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = roleTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Profile") },
                onClick = {
                    expanded = false
                    showProfile = true
                },
                leadingIcon = {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
            )
            onNavigatePrivacy?.let { goPrivacy ->
                DropdownMenuItem(
                    text = { Text("Privacy controls") },
                    onClick = {
                        expanded = false
                        goPrivacy()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Policy, contentDescription = null)
                    }
                )
            }
            if (onRefreshDashboard != null) {
                DropdownMenuItem(
                    text = { Text("Refresh dashboard") },
                    onClick = {
                        expanded = false
                        onRefreshDashboard()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text(
                            text = "Activity reminders",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notifyEnabled,
                            onCheckedChange = { v ->
                                notifyEnabled = v
                                RecallAiPreferences.setNotifyEnabled(context, v)
                            }
                        )
                    }
                },
                onClick = { }
            )
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null)
                        Text(
                            text = "Reduce haptics",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reduceHaptics,
                            onCheckedChange = { v ->
                                reduceHaptics = v
                                RecallAiPreferences.setReduceHaptics(context, v)
                            }
                        )
                    }
                },
                onClick = { }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Help") },
                onClick = {
                    expanded = false
                    showHelp = true
                },
                leadingIcon = {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("About RecallAI") },
                onClick = {
                    expanded = false
                    showAbout = true
                },
                leadingIcon = {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "Log out",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                onClick = {
                    expanded = false
                    showLogoutConfirm = true
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }

    if (showProfile) {
        ProfileSettingsDialog(
            roleTitle = roleTitle,
            onDismiss = { showProfile = false }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK") }
            },
            title = { Text("About") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RecallAI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Memory support tools for patients and caregivers. Data stays on this device unless you choose to share.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            },
            title = { Text("Help") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "• Use Home cards to reach chat, zones, and emergency tools quickly.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Memories and AI features work best when you allow storage in Privacy controls.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Pull to refresh: open Settings → Refresh dashboard after new activity.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Log out from Settings when you finish on a shared device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text("Log out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "You will return to the login screen. Unsaved work in other tabs may be lost.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
private fun ProfileMenuAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val path = AuthManager.avatarLocalPath
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(path))
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsDialog(
    roleTitle: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(AuthManager.userName.orEmpty()) }
    var bio by remember { mutableStateOf(AuthManager.bio.orEmpty()) }
    var gender by remember { mutableStateOf(AuthManager.userGender.orEmpty()) }
    var draftAvatarPath by remember { mutableStateOf(AuthManager.avatarLocalPath) }

    LaunchedEffect(Unit) {
        name = AuthManager.userName.orEmpty()
        bio = AuthManager.bio.orEmpty()
        gender = AuthManager.userGender.orEmpty()
        draftAvatarPath = AuthManager.avatarLocalPath
    }

    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = RecallAiPreferences.copyUriToProfileAvatarFile(context, uri)
            if (path != null) draftAvatarPath = path
        }
    }

    val genderOptions = listOf(
        "female" to "Female",
        "male" to "Male",
        "non-binary" to "Non-binary",
        "prefer not to say" to "Prefer not to say"
    )
    var genderMenuExpanded by remember { mutableStateOf(false) }

    val genderDisplay = genderOptions.find { it.first.equals(gender, ignoreCase = true) }?.second
        ?: gender.takeIf { it.isNotBlank() }
        ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    RecallAiPreferences.saveLocalProfile(
                        context = context,
                        displayName = name,
                        bio = bio,
                        gender = gender,
                        avatarAbsolutePath = draftAvatarPath
                    )
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Profile") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val path = draftAvatarPath
                        if (path != null && File(path).exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Column {
                        TextButton(
                            onClick = {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("Choose photo")
                        }
                        TextButton(
                            onClick = {
                                RecallAiPreferences.deleteStoredAvatarFile(context)
                                draftAvatarPath = null
                            }
                        ) {
                            Text("Remove photo", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                ExposedDropdownMenuBox(
                    expanded = genderMenuExpanded,
                    onExpandedChange = { genderMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = genderDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender") },
                        placeholder = { Text("Optional") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = genderMenuExpanded,
                        onDismissRequest = { genderMenuExpanded = false }
                    ) {
                        genderOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    gender = key
                                    genderMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    "Role: ${AuthManager.userRole ?: roleTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Session: ${if (AuthManager.token.isNullOrBlank()) "Not connected" else "Active"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Profile is saved on this device and stays in sync across RecallAI screens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        letterSpacing = (-0.1).sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "buttonScale"
    )
    Button(
        onClick = {
            haptics.strongTap()
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .height(PrimaryButtonHeight)
            .scale(scale),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TonalActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "tonalButtonScale"
    )
    FilledTonalButton(
        onClick = {
            haptics.lightTap()
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .height(PrimaryButtonHeight)
            .scale(scale),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = {
            if (enabled) {
                haptics.lightTap()
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier.heightIn(min = PrimaryButtonHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Reference: white card, soft shadow, moderate corner, no gradient
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface   // pure white
        ),
        shape = MaterialTheme.shapes.large,                      // 20dp corners
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ContentPadding),
            content = content
        )
    }
}

@Composable
fun AppBackdrop(
    backgroundRes: Int? = null,
    clearScreen: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    // The reference design uses a near-flat very-light periwinkle background.
    // Orbs are present but extremely subtle — alpha ≤ 0.07.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!clearScreen) {
            // Top-right pink whisper
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-50).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Bottom-left violet whisper
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-80).dp, y = 80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        if (backgroundRes != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageResource(backgroundRes)
                        alpha = 0.12f
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        content()
    }
}

@Composable
fun HeroHeaderCard(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 24.sp,
    illustrationRes: Int? = null
) {
    // Reference: white card, very subtle lavender tint only at the top strip
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Very light lavender accent strip at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
                if (illustrationRes != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HeroImageHeight),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                        )
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                                    setImageResource(illustrationRes)
                                    alpha = 0.97f
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String) {
    // Reference: white background pill with light border, value in primary color
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 2.dp,
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.extraLarge
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PremiumFeatureCard(
    title: String,
    subtitle: String = "",
    actionLabel: String,
    icon: ImageVector,
    illustrationRes: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var isNavigating by remember { mutableStateOf(false) }
    var sweepActive by remember { mutableStateOf(false) }
    LaunchedEffect(sweepActive) {
        if (sweepActive) {
            delay(SlowMotionMs.toLong())
            sweepActive = false
            isNavigating = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "featureCardScale"
    )
    val sweep by animateFloatAsState(
        targetValue = if (sweepActive) 1f else 0f,
        animationSpec = tween(SlowMotionMs),
        label = "featureCardSweep"
    )

    fun handleCardClick() {
        if (isNavigating) return
        isNavigating = true
        sweepActive = true
        haptics.strongTap()
        onClick()
    }

    ElevatedCard(
        modifier = modifier.scale(scale),
        interactionSource = interaction,
        onClick = { handleCardClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface   // white — matches reference
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = FeatureCardElevation)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeaturePadding)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (illustrationRes != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    setImageResource(illustrationRes)
                                    alpha = 0.98f
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { handleCardClick() },
                    enabled = !isNavigating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            if (sweepActive || sweep > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (sweep * 780f) - 390f
                        }
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "assistChipScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "assistChipAlpha"
    )
    AssistChip(
        onClick = {
            haptics.lightTap()
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        label = { Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.scale(scale).alpha(alpha)
    )
}

@Composable
fun QuickSwitchRow(
    onHome: () -> Unit = {},
    onChat: () -> Unit = {},
    onFace: () -> Unit = {},
    onMemories: () -> Unit = {},
    onRecall: () -> Unit = {}
) {
    // Intentionally left empty: bottom navigation is the single source of screen switching.
}

@Composable
fun ReadableAnswerPanel(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 140.dp,
    maxHeight: Dp = 360.dp
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun memoryAccentForType(type: String): Color = when (type) {
    "FACE_ANALYSIS" -> Color(0xFF5C6CFF)
    "OBJECT_DETECTION" -> Color(0xFF009688)
    "CHATBOT" -> Color(0xFF2962FF)
    "VOICE_CHATBOT", "VOICE" -> Color(0xFF7C4DFF)
    "RECALL_ASSISTANT", "RECALL_NOTE" -> Color(0xFFC51162)
    "MEDICATION_LOG", "MEDICATION_ALERT" -> Color(0xFFE65100)
    "ROUTINE_LOG" -> Color(0xFF00897B)
    "GEOFENCE_ALERT" -> Color(0xFFD32F2F)
    "EMERGENCY_EVENT" -> Color(0xFFB71C1C)
    else -> Color(0xFF5E73D2)
}

fun memoryTypeChipLabel(type: String): String = when (type) {
    "FACE_ANALYSIS" -> "Face"
    "OBJECT_DETECTION" -> "Object"
    "CHATBOT" -> "Chat"
    "VOICE_CHATBOT" -> "Voice"
    "VOICE" -> "Voice"
    "RECALL_ASSISTANT", "RECALL_NOTE" -> "Recall"
    "MEDICATION_LOG" -> "Medication"
    "MEDICATION_ALERT" -> "Med alert"
    "ROUTINE_LOG" -> "Routine"
    "GEOFENCE_ALERT" -> "Zone"
    "EMERGENCY_EVENT" -> "Emergency"
    else -> type.replace("_", " ").take(14).lowercase()
        .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
}

private val MedalGoldA = Color(0xFFFFF6E5)
private val MedalGoldB = Color(0xFFFFE08A)
private val MedalGoldC = Color(0xFFFFC233)
private val MedalGoldEdge = Color(0xFFB8860B)

@Composable
fun ModelGoldMedalMiniCard(
    title: String,
    statLine: String,
    icon: ImageVector,
    illustrationRes: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(FastMotionMs),
        label = "medalScale"
    )
    val rim = Brush.linearGradient(listOf(MedalGoldA, MedalGoldB, MedalGoldC, MedalGoldEdge))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 268.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .border(2.2.dp, rim, RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(interactionSource = interaction, indication = null) {
                haptics.lightTap()
                onOpen()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MedalGoldEdge,
                modifier = Modifier.size(22.dp)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(7.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = statLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(illustrationRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryMedalTimelineCard(
    memoryId: Long,
    rankLabel: String,
    typeLabel: String,
    title: String,
    preview: String,
    createdAt: Long,
    accentColor: Color,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember(memoryId) { mutableStateOf(false) }
    val rim = Brush.verticalGradient(listOf(MedalGoldB, MedalGoldC, MedalGoldEdge))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(listOf(MedalGoldA, MedalGoldEdge.copy(alpha = 0.55f))),
                shape = RoundedCornerShape(22.dp)
            )
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(7.dp)
                .heightIn(min = 112.dp)
                .background(rim)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = typeLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = rankLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MedalGoldEdge
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        haptics.lightTap()
                        onOpenTimeline()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open memory feed",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatMemoryInstantMs(createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) preview else preview.take(96).let { if (preview.length > 96) "$it…" else it },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) 12 else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.length > 96) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.lightTap()
                            expanded = !expanded
                        },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}