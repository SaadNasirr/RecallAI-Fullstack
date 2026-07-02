package com.example.recallai

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.recallai.memories.MemoryOpenCoordinator
import com.example.recallai.memories.MyMemoriesScreen
import com.example.recallai.memories.openMemoryFromTimeline
import com.example.recallai.recallassistant.RecallAssistantScreen
import com.example.recallai.objectlocator.ObjectLocatorScreen
import com.example.recallai.geofence.CreateGeofenceScreen
import com.example.recallai.geofence.GeofenceAlertHistoryScreen
import com.example.recallai.geofence.GeofenceZonesScreen
import com.example.recallai.geofence.PatientGeofenceScreen
import com.example.recallai.face.FaceInsightsScreen
import com.example.recallai.caregiver.CaregiverRulesScreen
import com.example.recallai.demo.DemoModeScreen
import com.example.recallai.emergency.EmergencyAssistScreen
import com.example.recallai.medication.MedicationSchedulerScreen
import com.example.recallai.people.PeopleMemoryBookScreen
import com.example.recallai.privacy.PrivacyControlScreen
import com.example.recallai.flashcard.FlashcardScreen
import com.example.recallai.routine.RoutinePlannerScreen
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.recallai.care.pairing.CaregiverAddPatientScreen
import com.example.recallai.care.pairing.CaregiverInviteCodeScreen
import com.example.recallai.care.pairing.CaregiverLinkedPatientsScreen
import com.example.recallai.care.pairing.CaregiverQrScannerScreen
import com.example.recallai.care.pairing.PatientBackupCodeScreen
import com.example.recallai.care.pairing.PatientCaregiverPermissionsScreen
import com.example.recallai.care.pairing.PatientConnectCaregiverScreen
import com.example.recallai.care.pairing.PatientLinkedCaregiversScreen
import com.example.recallai.care.pairing.PatientPendingApprovalScreen
import com.example.recallai.care.pairing.PatientQrDisplayScreen
import com.example.recallai.care.AlertCenterScreen
import com.example.recallai.care.CareAlertNotifier
import com.example.recallai.care.CareCoordinationScreen
import com.example.recallai.ui.screens.SignUpScreen
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.recallai.chat.ChatScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.recallai.navigation.backToLogin
import com.example.recallai.navigation.backToRoleSelection
import com.example.recallai.navigation.switchShellTab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Home
import com.example.recallai.ui.theme.RecallAITheme
import com.example.recallai.ui.screens.AuthRoute
import com.example.recallai.ui.dashboard.DashboardLayout
import com.example.recallai.ui.dashboard.MindcareFloatingNavBar
import com.example.recallai.ui.dashboard.MindcareNavTab
import com.example.recallai.ui.screens.CaregiverHomeScreen
import com.example.recallai.ui.screens.LoginScreen
import com.example.recallai.ui.screens.PatientHomeScreen
import com.example.recallai.ui.screens.RoleSelectionScreen
import com.example.recallai.ui.screens.SplashScreen
import com.example.recallai.data.AuthManager
import com.example.recallai.data.AuthRepository
import com.example.recallai.data.CareRepository
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import dagger.hilt.android.AndroidEntryPoint
import com.example.recallai.ui.screens.CaregiverPatientsScreen
import com.example.recallai.medication.MedicationEscalationScheduler
import com.example.recallai.reminders.RemindersScreen
import androidx.lifecycle.lifecycleScope
import com.example.recallai.di.CareRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.recallai.data.GeofenceRepository
import com.example.recallai.geofence.GeofenceForegroundSync
import com.example.recallai.notifications.RequestNotificationPermissionOnEnter
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var geofenceRepository: GeofenceRepository
    @Inject lateinit var careRepository: CareRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var memoryOpenCoordinator: MemoryOpenCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MedicationEscalationScheduler.schedule(this)
        setContent {
            RecallAITheme {
                RecallAINavHost(
                    authRepository = authRepository,
                    memoryOpenCoordinator = memoryOpenCoordinator
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GeofenceForegroundSync.runIfPatient(this, geofenceRepository, lifecycleScope, careRepository)
    }
}


@Composable
fun RecallAINavHost(
    authRepository: AuthRepository,
    memoryOpenCoordinator: MemoryOpenCoordinator
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthRoute.Splash.route
    ) {
        composable(AuthRoute.Splash.route) {
            SplashScreen(
                onFinished = { route ->
                    navController.navigate(route) {
                        popUpTo(AuthRoute.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AuthRoute.PatientLogin.route) {
            LoginScreen(
                roleLabel = "Patient",
                onLoginSuccess = {
                    navController.navigate(AuthRoute.PatientShell.route) {
                        popUpTo(AuthRoute.PatientLogin.route) { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate(AuthRoute.PatientSignUp.route) },
                onBack = { navController.backToRoleSelection() }
            )
        }

        composable(AuthRoute.PatientSignUp.route) {
            SignUpScreen(
                roleLabel = "Patient",
                onBackToLogin = { navController.backToLogin(AuthRoute.PatientLogin.route) }
            )
        }

        composable(AuthRoute.CaregiverLogin.route) {
            LoginScreen(
                roleLabel = "Caregiver",
                onLoginSuccess = {
                    navController.navigate(AuthRoute.CaregiverShell.route) {
                        popUpTo(AuthRoute.CaregiverLogin.route) { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate(AuthRoute.CaregiverSignUp.route) },
                onBack = { navController.backToRoleSelection() }
            )
        }

        composable(AuthRoute.CaregiverSignUp.route) {
            SignUpScreen(
                roleLabel = "Caregiver",
                onBackToLogin = { navController.backToLogin(AuthRoute.CaregiverLogin.route) }
            )
        }

        composable(AuthRoute.RoleSelection.route) {
            RoleSelectionScreen(
                onPatientSelected = {
                    navController.navigate(AuthRoute.PatientLogin.route) {
                        launchSingleTop = true
                    }
                },
                onCaregiverSelected = {
                    navController.navigate(AuthRoute.CaregiverLogin.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AuthRoute.PatientShell.route) {
            PatientShell(
                memoryOpenCoordinator = memoryOpenCoordinator,
                onExitRole = {
                    navController.navigate(AuthRoute.RoleSelection.route) {
                        popUpTo(AuthRoute.PatientShell.route) { inclusive = true }
                    }
                },
                onLogout = {
                    authRepository.logout()
                    navController.navigate(AuthRoute.PatientLogin.route) {
                        popUpTo(AuthRoute.PatientShell.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AuthRoute.CaregiverShell.route) {
            CaregiverShell(
                memoryOpenCoordinator = memoryOpenCoordinator,
                onExitRole = {
                    navController.navigate(AuthRoute.RoleSelection.route) {
                        popUpTo(AuthRoute.CaregiverShell.route) { inclusive = true }
                    }
                },
                onLogout = {
                    authRepository.logout()
                    navController.navigate(AuthRoute.CaregiverLogin.route) {
                        popUpTo(AuthRoute.CaregiverShell.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun PatientShell(
    memoryOpenCoordinator: MemoryOpenCoordinator,
    onExitRole: () -> Unit,
    onLogout: () -> Unit
) {
    RequestNotificationPermissionOnEnter()
    val navController = rememberNavController()
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val isDark = isSystemInDarkTheme()
    val barContainer = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val itemIndicator = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val legacyTabs = listOf(
        AuthRoute.PatientHome.route to Pair(Icons.Filled.Home, "Home"),
        AuthRoute.Chat.route to Pair(Icons.AutoMirrored.Filled.Chat, "Chat"),
        AuthRoute.FaceInsights.route to Pair(Icons.Filled.Face, "Face"),
        AuthRoute.Memories.route to Pair(Icons.Filled.Memory, "Memo"),
        AuthRoute.RecallAssistant.route to Pair(Icons.Filled.Psychology, "Recall"),
        AuthRoute.Reminders.route to Pair(Icons.Filled.Notifications, "Remind")
    )
    val mindcareTabs = listOf(
        MindcareNavTab(AuthRoute.PatientHome.route, Icons.Filled.Home, "Home"),
        MindcareNavTab(AuthRoute.Chat.route, Icons.AutoMirrored.Filled.Chat, "Chat"),
        MindcareNavTab(AuthRoute.Memories.route, Icons.Filled.Memory, "Memo"),
        MindcareNavTab(AuthRoute.Reminders.route, Icons.Filled.Notifications, "Remind")
    )
    Scaffold(
        bottomBar = {
            if (DashboardLayout.USE_MINDCARE_STYLE) {
                MindcareFloatingNavBar(
                    tabs = mindcareTabs,
                    selectedRoute = route,
                    onTabSelected = { tabRoute -> navController.switchShellTab(tabRoute, route) }
                )
            } else {
                NavigationBar(
                    containerColor = barContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(size = 20.dp))
                ) {
                    legacyTabs.forEach { (tabRoute, meta) ->
                        NavigationBarItem(
                            selected = route == tabRoute,
                            onClick = { navController.switchShellTab(tabRoute, route) },
                            icon = {
                                Icon(
                                    meta.first,
                                    contentDescription = meta.second,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = meta.second,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    fontSize = 11.sp,
                                    fontWeight = if (route == tabRoute) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = itemIndicator,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AuthRoute.PatientHome.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AuthRoute.PatientHome.route) {
                PatientHomeScreen(
                    onBack = onExitRole,
                    onNavigateToChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateToMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateToRecallAssistant = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onNavigateToObjectLocator = { navController.navigate(AuthRoute.ObjectLocator.route) },
                    onNavigateToFaceInsights = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateToGeofencing = { navController.navigate(AuthRoute.Geofencing.route) },
                    onNavigateToMedication = { navController.navigate(AuthRoute.Medication.route) },
                    onNavigateToReminders = { navController.navigate(AuthRoute.Reminders.route) },
                    onNavigateToPeopleBook = { navController.navigate(AuthRoute.PeopleBook.route) },
                    onNavigateToDemoMode = { navController.navigate(AuthRoute.DemoMode.route) },
                    onNavigateToRoutine = { navController.navigate(AuthRoute.Routine.route) },
                    onNavigateToEmergency = { navController.navigate(AuthRoute.Emergency.route) },
                    onNavigateToPrivacy = { navController.navigate(AuthRoute.Privacy.route) },
                    onNavigateToConnectCaregiver = { navController.navigate(AuthRoute.PatientConnectCaregiver.route) },
                    onNavigateToFlashcard = { navController.navigate(AuthRoute.Flashcard.route) },
                    onLogout = onLogout
                )
            }
            composable(AuthRoute.Chat.route) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onNavigateWhereAmI = { navController.navigate(AuthRoute.Geofencing.route) },
                    onNavigateObjectLocator = { navController.navigate(AuthRoute.ObjectLocator.route) }
                )
            }
            composable(AuthRoute.Reminders.route) {
                RemindersScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AuthRoute.Memories.route) {
                MyMemoriesScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onMemoryClick = { memory ->
                        navController.openMemoryFromTimeline(memory, memoryOpenCoordinator, isCaregiver = false)
                    }
                )
            }
            composable(AuthRoute.RecallAssistant.route) {
                RecallAssistantScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.ObjectLocator.route) {
                ObjectLocatorScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.FaceInsights.route) {
                FaceInsightsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onNavigateToPeopleBook = { navController.navigate(AuthRoute.PeopleBook.route) }
                )
            }
            composable(AuthRoute.Geofencing.route) {
                PatientGeofenceScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AuthRoute.Medication.route) {
                MedicationSchedulerScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.PeopleBook.route) {
                PeopleMemoryBookScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.DemoMode.route) {
                DemoModeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Routine.route) {
                RoutinePlannerScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Emergency.route) {
                EmergencyAssistScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Privacy.route) {
                PrivacyControlScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToPatientDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Flashcard.route) {
                FlashcardScreen(onBack = { navController.popBackStack() })
            }
            composable(AuthRoute.PatientConnectCaregiver.route) {
                PatientConnectCaregiverScreen(
                    onBack = { navController.popBackStack() },
                    onShowQr = { navController.navigate(AuthRoute.PatientShowCareQr.route) },
                    onPending = { navController.navigate(AuthRoute.PatientPendingCare.route) },
                    onLinked = { navController.navigate(AuthRoute.PatientLinkedCaregivers.route) },
                    onBackupCode = { navController.navigate(AuthRoute.PatientBackupCode.route) }
                )
            }
            composable(AuthRoute.PatientShowCareQr.route) {
                PatientQrDisplayScreen(onBack = { navController.popBackStack() })
            }
            composable(AuthRoute.PatientPendingCare.route) {
                PatientPendingApprovalScreen(onBack = { navController.popBackStack() })
            }
            composable(AuthRoute.PatientLinkedCaregivers.route) {
                PatientLinkedCaregiversScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { id ->
                        navController.navigate("patient_care_permissions/$id")
                    }
                )
            }
            composable(AuthRoute.PatientBackupCode.route) {
                PatientBackupCodeScreen(onBack = { navController.popBackStack() })
            }
            composable(
                AuthRoute.PatientCarePermissions.route,
                arguments = listOf(navArgument("relId") { type = NavType.StringType })
            ) { entry ->
                val relId = entry.arguments?.getString("relId") ?: return@composable
                PatientCaregiverPermissionsScreen(
                    relationshipId = relId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun CaregiverShell(
    memoryOpenCoordinator: MemoryOpenCoordinator,
    onExitRole: () -> Unit,
    onLogout: () -> Unit
) {
    RequestNotificationPermissionOnEnter()
    val navController = rememberNavController()
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val appCtx = LocalContext.current.applicationContext
    val careEntry = remember(appCtx) {
        EntryPointAccessors.fromApplication(appCtx, CareRepositoryEntryPoint::class.java).careRepository()
    }
    var unreadAlerts by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            unreadAlerts = runCatching { careEntry.alertsUnreadCount() }.getOrDefault(0)
            runCatching {
                val inbox = careEntry.alertsInbox()
                CareAlertNotifier.notifyNewAlerts(appCtx, inbox)
            }
            delay(20_000)
        }
    }
    LaunchedEffect(route) {
        unreadAlerts = runCatching { careEntry.alertsUnreadCount() }.getOrDefault(0)
        runCatching {
            val inbox = careEntry.alertsInbox()
            CareAlertNotifier.notifyNewAlerts(appCtx, inbox)
        }
    }
    val isDark = isSystemInDarkTheme()
    val barContainer = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val itemIndicator = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val legacyTabs = listOf(
        AuthRoute.CaregiverHome.route to Pair(Icons.Filled.Home, "Home"),
        AuthRoute.CaregiverPatients.route to Pair(Icons.Filled.Visibility, "Watch"),
        AuthRoute.AlertCenter.route to Pair(Icons.Filled.Notifications, "Alerts"),
        AuthRoute.CaregiverRules.route to Pair(Icons.Filled.AutoAwesome, "Rules"),
        AuthRoute.Memories.route to Pair(Icons.Filled.Memory, "Memo"),
        AuthRoute.Geofencing.route to Pair(Icons.Filled.LocationOn, "Zones")
    )
    val mindcareTabs = listOf(
        MindcareNavTab(AuthRoute.CaregiverHome.route, Icons.Filled.Home, "Home"),
        MindcareNavTab(AuthRoute.AlertCenter.route, Icons.Filled.Notifications, "Alerts", unreadAlerts),
        MindcareNavTab(AuthRoute.CaregiverPatients.route, Icons.Filled.Visibility, "Watch"),
        MindcareNavTab(AuthRoute.Geofencing.route, Icons.Filled.LocationOn, "Zones")
    )
    Scaffold(
        bottomBar = {
            if (DashboardLayout.USE_MINDCARE_STYLE) {
                MindcareFloatingNavBar(
                    tabs = mindcareTabs,
                    selectedRoute = route,
                    onTabSelected = { tabRoute -> navController.switchShellTab(tabRoute, route) }
                )
            } else {
                NavigationBar(
                    containerColor = barContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(size = 20.dp))
                ) {
                    legacyTabs.forEach { (tabRoute, meta) ->
                        NavigationBarItem(
                            selected = route == tabRoute,
                            onClick = { navController.switchShellTab(tabRoute, route) },
                            icon = {
                                if (tabRoute == AuthRoute.AlertCenter.route && unreadAlerts > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge { Text("${unreadAlerts.coerceAtMost(99)}") }
                                        }
                                    ) {
                                        Icon(
                                            meta.first,
                                            contentDescription = meta.second,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        meta.first,
                                        contentDescription = meta.second,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = meta.second,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    fontSize = 11.sp,
                                    fontWeight = if (route == tabRoute) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = itemIndicator,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AuthRoute.CaregiverHome.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AuthRoute.CaregiverHome.route) {
                CaregiverHomeScreen(
                    onBack = onExitRole,
                    onNavigateToAlertRules = { navController.navigate(AuthRoute.CaregiverRules.route) },
                    onNavigateToTimeline = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateToZones = { navController.navigate(AuthRoute.Geofencing.route) },
                    onNavigateToPatients = { navController.navigate(AuthRoute.CaregiverPatients.route) },
                    onNavigateToCareCoordination = { navController.navigate(AuthRoute.CareCoordination.route) },
                    onNavigateToAlertCenter = { navController.navigate(AuthRoute.AlertCenter.route) },
                    onNavigateToPrivacy = { navController.navigate(AuthRoute.Privacy.route) },
                    onNavigateToAddPatient = { navController.navigate(AuthRoute.CaregiverAddPatient.route) },
                    onLogout = onLogout
                )
            }
            composable(AuthRoute.CaregiverPatients.route) {
                CaregiverPatientsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToTimeline = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateToZones = { navController.navigate(AuthRoute.Geofencing.route) },
                    onNavigateToCareCoordination = { navController.navigate(AuthRoute.CareCoordination.route) },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onNavigateToAddPatient = { navController.navigate(AuthRoute.CaregiverAddPatient.route) }
                )
            }
            composable(AuthRoute.CaregiverRules.route) { CaregiverRulesScreen(onBack = { navController.popBackStack() }) }
            composable(AuthRoute.Memories.route) {
                MyMemoriesScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onMemoryClick = { memory ->
                        navController.openMemoryFromTimeline(memory, memoryOpenCoordinator, isCaregiver = true)
                    }
                )
            }
            composable(AuthRoute.Geofencing.route) {
                GeofenceZonesScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateCreate = { pid ->
                        navController.navigate("geofence_create/$pid")
                    },
                    onNavigateAlerts = { pid ->
                        navController.navigate("geofence_alerts/$pid")
                    }
                )
            }
            composable(
                route = "geofence_create/{patientId}",
                arguments = listOf(navArgument("patientId") { type = NavType.StringType })
            ) {
                CreateGeofenceScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = "geofence_alerts/{patientId}",
                arguments = listOf(navArgument("patientId") { type = NavType.StringType })
            ) {
                GeofenceAlertHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(AuthRoute.CareCoordination.route) {
                CareCoordinationScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToWatchlist = { navController.navigate(AuthRoute.CaregiverPatients.route) },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.AlertCenter.route) {
                AlertCenterScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToZones = { navController.navigate(AuthRoute.Geofencing.route) },
                    onNavigateToEmergency = { navController.navigate(AuthRoute.Emergency.route) },
                    onNavigateToCareCoordination = { navController.navigate(AuthRoute.CareCoordination.route) },
                    onNavigateToTimeline = { navController.navigate(AuthRoute.Memories.route) }
                )
            }
            composable(AuthRoute.Medication.route) {
                MedicationSchedulerScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Emergency.route) {
                EmergencyAssistScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Chat.route) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.FaceInsights.route) {
                FaceInsightsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) },
                    onNavigateToPeopleBook = { navController.navigate(AuthRoute.PeopleBook.route) }
                )
            }
            composable(AuthRoute.RecallAssistant.route) {
                RecallAssistantScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.Privacy.route) {
                PrivacyControlScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateHome = { navController.navigateToCaregiverDashboard() },
                    onNavigateChat = { navController.navigate(AuthRoute.Chat.route) },
                    onNavigateFace = { navController.navigate(AuthRoute.FaceInsights.route) },
                    onNavigateMemories = { navController.navigate(AuthRoute.Memories.route) },
                    onNavigateRecall = { navController.navigate(AuthRoute.RecallAssistant.route) }
                )
            }
            composable(AuthRoute.CaregiverAddPatient.route) {
                CaregiverAddPatientScreen(
                    onBack = { navController.popBackStack() },
                    onScan = { navController.navigate(AuthRoute.CaregiverScanQr.route) },
                    onCode = { navController.navigate(AuthRoute.CaregiverInviteCode.route) },
                    onPatients = { navController.navigate(AuthRoute.CaregiverLinkedPatients.route) }
                )
            }
            composable(AuthRoute.CaregiverScanQr.route) {
                CaregiverQrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(AuthRoute.CaregiverInviteCode.route) {
                CaregiverInviteCodeScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(AuthRoute.CaregiverLinkedPatients.route) {
                CaregiverLinkedPatientsScreen(
                    onBack = { navController.popBackStack() },
                    onPatientChosen = {
                        navController.navigateToCaregiverDashboard()
                    },
                    onAddPatient = { navController.navigate(AuthRoute.CaregiverAddPatient.route) }
                )
            }
        }
    }
}

private fun NavController.navigateToPatientDashboard() {
    navigate(AuthRoute.PatientHome.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.navigateToCaregiverDashboard() {
    navigate(AuthRoute.CaregiverHome.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

