@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.ui.screens

import com.example.recallai.R
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.recallai.auth.LoginViewModel
import com.example.recallai.ui.patient.PatientAssignedCareTasksSection
import com.example.recallai.ui.patient.PatientScheduleSection
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.MemoryMedalTimelineCard
import com.example.recallai.ui.components.ModelGoldMedalMiniCard
import com.example.recallai.ui.components.memoryAccentForType
import com.example.recallai.ui.components.memoryTypeChipLabel
import com.example.recallai.ui.components.RecallSettingsMenu
import com.example.recallai.ui.components.CartoonHiGreeting
import com.example.recallai.ui.components.LoginScreenEntrance
import com.example.recallai.ui.components.RecallBrandLogoOnboarding
import com.example.recallai.ui.components.RecallLogo
import com.example.recallai.ui.components.RecallLogoFloating
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SecondaryActionButton
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill
import com.example.recallai.data.AuthManager

sealed class AuthRoute(val route: String) {
    data object Splash : AuthRoute("splash")
    data object RoleSelection : AuthRoute("role_selection")
    data object PatientLogin : AuthRoute("patient_login")
    data object PatientSignUp : AuthRoute("patient_signup")
    data object CaregiverLogin : AuthRoute("caregiver_login")
    data object CaregiverSignUp : AuthRoute("caregiver_signup")
    data object PatientShell : AuthRoute("patient_shell")
    data object CaregiverShell : AuthRoute("caregiver_shell")
    data object PatientHome : AuthRoute("patient_home")
    data object CaregiverHome : AuthRoute("caregiver_home")
    data object Chat : AuthRoute("chat")
    data object Memories : AuthRoute("memories")
    data object RecallAssistant : AuthRoute("recall_assistant")
    data object ObjectLocator : AuthRoute("object_locator")
    data object FaceInsights : AuthRoute("face_insights")
    data object Geofencing : AuthRoute("geofencing")
    data object CaregiverRules : AuthRoute("caregiver_rules")
    data object CaregiverPatients : AuthRoute("caregiver_patients")
    data object Medication : AuthRoute("medication")
    data object PeopleBook : AuthRoute("people_book")
    data object DemoMode : AuthRoute("demo_mode")
    data object Routine : AuthRoute("routine")
    data object Emergency : AuthRoute("emergency")
    data object CareCoordination : AuthRoute("care_coordination")
    data object Privacy : AuthRoute("privacy")
    data object AlertCenter : AuthRoute("alert_center")
    data object Reminders : AuthRoute("reminders")
    data object PatientConnectCaregiver : AuthRoute("patient_connect_caregiver")
    data object PatientShowCareQr : AuthRoute("patient_show_care_qr")
    data object PatientPendingCare : AuthRoute("patient_pending_care")
    data object PatientLinkedCaregivers : AuthRoute("patient_linked_caregivers")
    data object PatientBackupCode : AuthRoute("patient_backup_code")
    data object PatientCarePermissions : AuthRoute("patient_care_permissions/{relId}")
    data object CaregiverAddPatient : AuthRoute("caregiver_add_patient")
    data object CaregiverScanQr : AuthRoute("caregiver_scan_qr")
    data object CaregiverInviteCode : AuthRoute("caregiver_invite_code")
    data object CaregiverLinkedPatients : AuthRoute("caregiver_linked_patients")
    data object Flashcard : AuthRoute("flashcard")
}

private object RoleSelectionCopy {
    const val title = "RecallAI"
    const val subtitle = "A calm AI companion for memory support"
    const val selectRole = "Select your role"
}

@Composable
fun LoginScreen(
    roleLabel: String = "Patient",
    onLoginSuccess: () -> Unit,
    onSignUpClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val greeting = if (roleLabel.equals("Caregiver", ignoreCase = true)) {
        "Hello! Ready to care? 👋"
    } else {
        "Hi! Welcome back! 👋"
    }
    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        cardVisible = true
    }

    AppBackdrop(backgroundRes = R.drawable.img_login_welcome, clearScreen = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoginScreenEntrance(visible = cardVisible) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RecallLogoFloating(
                                widthDp = 130.dp,
                                modifier = Modifier.padding(top = 18.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "$roleLabel login",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = state.email,
                                onValueChange = viewModel::onEmailChange,
                                label = { Text("Email") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = state.password,
                                onValueChange = viewModel::onPasswordChange,
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (state.error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            PrimaryActionButton(
                                text = if (state.isLoading) "Wait" else "Login",
                                onClick = { viewModel.login(roleLabel.lowercase(), onLoginSuccess) },
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))

                            SecondaryActionButton(
                                text = "Sign up",
                                onClick = onSignUpClick,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            if (onBack != null) {
                                Spacer(Modifier.height(6.dp))
                                SecondaryActionButton(
                                    text = "Roles",
                                    onClick = onBack,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            CartoonHiGreeting(
                greeting = greeting,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onCaregiverSelected: () -> Unit
) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        contentVisible = true
    }

    AppBackdrop(clearScreen = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = tween(500))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(8.dp))
                    RecallLogo(widthDp = 96.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = RoleSelectionCopy.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = RoleSelectionCopy.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = RoleSelectionCopy.selectRole,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ill_patient_caregiver),
                        contentDescription = "Role illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            AnimatedVisibility(
                visible = contentVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(550)
                ) + fadeIn(tween(450))
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        PrimaryActionButton(
                            text = "Patient",
                            onClick = onPatientSelected,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Caregiver",
                            onClick = onCaregiverSelected,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun PatientHomeScreen(
    onBack: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToMemories: () -> Unit = {},
    onNavigateToRecallAssistant: () -> Unit = {},
    onNavigateToObjectLocator: () -> Unit = {},
    onNavigateToFaceInsights: () -> Unit = {},
    onNavigateToGeofencing: () -> Unit = {},
    onNavigateToMedication: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToPeopleBook: () -> Unit = {},
    onNavigateToDemoMode: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToConnectCaregiver: () -> Unit = {},
    onNavigateToFlashcard: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: PatientHomeViewModel = hiltViewModel()
) {
    if (com.example.recallai.ui.dashboard.DashboardLayout.USE_MINDCARE_STYLE) {
        com.example.recallai.ui.dashboard.MindcarePatientHomeScreen(
            onBack = onBack,
            onNavigateToChat = onNavigateToChat,
            onNavigateToMemories = onNavigateToMemories,
            onNavigateToRecallAssistant = onNavigateToRecallAssistant,
            onNavigateToObjectLocator = onNavigateToObjectLocator,
            onNavigateToFaceInsights = onNavigateToFaceInsights,
            onNavigateToGeofencing = onNavigateToGeofencing,
            onNavigateToMedication = onNavigateToMedication,
            onNavigateToReminders = onNavigateToReminders,
            onNavigateToPeopleBook = onNavigateToPeopleBook,
            onNavigateToDemoMode = onNavigateToDemoMode,
            onNavigateToRoutine = onNavigateToRoutine,
            onNavigateToEmergency = onNavigateToEmergency,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToConnectCaregiver = onNavigateToConnectCaregiver,
            onNavigateToFlashcard = onNavigateToFlashcard,
            onLogout = onLogout,
            viewModel = viewModel
        )
    } else {
        PatientHomeScreenLegacy(
            onBack = onBack,
            onNavigateToChat = onNavigateToChat,
            onNavigateToMemories = onNavigateToMemories,
            onNavigateToRecallAssistant = onNavigateToRecallAssistant,
            onNavigateToObjectLocator = onNavigateToObjectLocator,
            onNavigateToFaceInsights = onNavigateToFaceInsights,
            onNavigateToGeofencing = onNavigateToGeofencing,
            onNavigateToMedication = onNavigateToMedication,
            onNavigateToReminders = onNavigateToReminders,
            onNavigateToPeopleBook = onNavigateToPeopleBook,
            onNavigateToDemoMode = onNavigateToDemoMode,
            onNavigateToRoutine = onNavigateToRoutine,
            onNavigateToEmergency = onNavigateToEmergency,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToConnectCaregiver = onNavigateToConnectCaregiver,
            onNavigateToFlashcard = onNavigateToFlashcard,
            onLogout = onLogout,
            viewModel = viewModel
        )
    }
}

@Composable
fun CaregiverHomeScreen(
    onBack: () -> Unit = {},
    onNavigateToAlertRules: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToZones: () -> Unit = {},
    onNavigateToPatients: () -> Unit = {},
    onNavigateToCareCoordination: () -> Unit = {},
    onNavigateToAlertCenter: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToAddPatient: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: CaregiverHomeViewModel = hiltViewModel()
) {
    if (com.example.recallai.ui.dashboard.DashboardLayout.USE_MINDCARE_STYLE) {
        com.example.recallai.ui.dashboard.MindcareCaregiverHomeScreen(
            onBack = onBack,
            onNavigateToAlertRules = onNavigateToAlertRules,
            onNavigateToTimeline = onNavigateToTimeline,
            onNavigateToZones = onNavigateToZones,
            onNavigateToPatients = onNavigateToPatients,
            onNavigateToCareCoordination = onNavigateToCareCoordination,
            onNavigateToAlertCenter = onNavigateToAlertCenter,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToAddPatient = onNavigateToAddPatient,
            onLogout = onLogout,
            viewModel = viewModel
        )
    } else {
        CaregiverHomeScreenLegacy(
            onBack = onBack,
            onNavigateToAlertRules = onNavigateToAlertRules,
            onNavigateToTimeline = onNavigateToTimeline,
            onNavigateToZones = onNavigateToZones,
            onNavigateToPatients = onNavigateToPatients,
            onNavigateToCareCoordination = onNavigateToCareCoordination,
            onNavigateToAlertCenter = onNavigateToAlertCenter,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToAddPatient = onNavigateToAddPatient,
            onLogout = onLogout,
            viewModel = viewModel
        )
    }
}
