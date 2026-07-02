package com.example.recallai.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.auth.SignUpViewModel
import com.example.recallai.ui.components.CartoonHiGreeting
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.LoginScreenEntrance
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallLogoFloating
import com.example.recallai.ui.components.SecondaryActionButton
import com.example.recallai.ui.components.SignUpGenderPills
import com.example.recallai.ui.components.SignUpStepIndicator
import com.example.recallai.ui.dashboard.MindcareGradientBackground
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    roleLabel: String = "Patient",
    onBackToLogin: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.value
    val context = LocalContext.current
    val isPatient = roleLabel.equals("Patient", ignoreCase = true)
    val totalSteps = if (isPatient) 3 else 2
    var step by remember { mutableIntStateOf(0) }
    var cardVisible by remember { mutableStateOf(false) }
    var stepError by remember { mutableStateOf<String?>(null) }

    val fusedClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            fetchSignUpLocation(context, fusedClient, viewModel)
        } else {
            viewModel.onLiveLocationDenied()
        }
    }

    BackHandler {
        if (step > 0) step -= 1 else onBackToLogin()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        cardVisible = true
    }

    val today = remember { Calendar.getInstance() }
    val datePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selected = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                viewModel.onDateOfBirthChange(selected)
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }
    }

    val stepLabels = if (isPatient) {
        listOf("Account", "Profile", "Location")
    } else {
        listOf("Account", "Profile")
    }

    val greeting = if (isPatient) {
        "Let's get you started! 🎉"
    } else {
        "Join the care team! 💜"
    }

    val subtitle = when (step) {
        0 -> "Create your secure account"
        1 -> "Tell us a little about you"
        else -> "Confirm where you are"
    }

    MindcareGradientBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "$roleLabel signup",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (step > 0) step -= 1 else onBackToLogin()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                LoginScreenEntrance(visible = cardVisible) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RecallLogoFloating(widthDp = 88.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(14.dp))
                            SignUpStepIndicator(
                                currentStep = step,
                                totalSteps = totalSteps,
                                stepLabels = stepLabels
                            )
                            Spacer(Modifier.height(16.dp))

                            AnimatedContent(
                                targetState = step,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally { it / 2 } + fadeIn(tween(350))) togetherWith
                                            (slideOutHorizontally { -it / 2 } + fadeOut(tween(300)))
                                    } else {
                                        (slideInHorizontally { -it / 2 } + fadeIn(tween(350))) togetherWith
                                            (slideOutHorizontally { it / 2 } + fadeOut(tween(300)))
                                    }
                                },
                                label = "signupStep"
                            ) { currentStep ->
                                when (currentStep) {
                                    0 -> SignUpAccountStep(
                                        name = state.name,
                                        email = state.email,
                                        password = state.password,
                                        phone = state.phone,
                                        onNameChange = viewModel::onNameChange,
                                        onEmailChange = viewModel::onEmailChange,
                                        onPasswordChange = viewModel::onPasswordChange,
                                        onPhoneChange = viewModel::onPhoneChange
                                    )
                                    1 -> SignUpProfileStep(
                                        age = state.age,
                                        dateOfBirth = state.dateOfBirth,
                                        gender = state.gender,
                                        city = state.city,
                                        location = state.location,
                                        onAgeChange = viewModel::onAgeChange,
                                        onGenderChange = viewModel::onGenderChange,
                                        onCityChange = viewModel::onCityChange,
                                        onLocationChange = viewModel::onLocationChange,
                                        onPickDate = { datePicker.show() }
                                    )
                                    else -> SignUpLocationStep(
                                        liveLat = state.liveLat,
                                        liveLng = state.liveLng,
                                        locationHint = state.locationHint,
                                        onUseLocation = {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    )
                                }
                            }

                            stepError?.let { msg ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            state.error?.let { msg ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (step > 0) {
                                    SecondaryActionButton(
                                        text = "Back",
                                        onClick = {
                                            stepError = null
                                            step -= 1
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                PrimaryActionButton(
                                    text = when {
                                        state.isLoading -> "Wait"
                                        step < totalSteps - 1 -> "Continue"
                                        else -> "Create account"
                                    },
                                    onClick = {
                                        stepError = null
                                        when {
                                            step < totalSteps - 1 -> {
                                                val err = validateSignUpStep(step, state, isPatient)
                                                if (err != null) {
                                                    stepError = err
                                                } else {
                                                    step += 1
                                                }
                                            }
                                            else -> {
                                                viewModel.signUp(
                                                    role = roleLabel.lowercase(),
                                                    onSuccess = onBackToLogin
                                                )
                                            }
                                        }
                                    },
                                    enabled = !state.isLoading,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            SecondaryActionButton(
                                text = "Already have an account? Log in",
                                onClick = onBackToLogin,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                CartoonHiGreeting(
                    greeting = greeting,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SignUpAccountStep(
    name: String,
    email: String,
    password: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone number") },
            placeholder = { Text("Include country code if needed") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun SignUpProfileStep(
    age: String,
    dateOfBirth: String,
    gender: String,
    city: String,
    location: String,
    onAgeChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onPickDate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = age,
            onValueChange = onAgeChange,
            label = { Text("Age") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = {},
            label = { Text("Birth date") },
            placeholder = { Text("DD/MM/YYYY") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true
        )
        SecondaryActionButton(text = "Pick date", onClick = onPickDate)
        Text(
            text = "Gender",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        SignUpGenderPills(selected = gender, onSelect = onGenderChange)
        OutlinedTextField(
            value = city,
            onValueChange = onCityChange,
            label = { Text("City") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Area / address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun SignUpLocationStep(
    liveLat: Double?,
    liveLng: Double?,
    locationHint: String?,
    onUseLocation: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "We use your location to keep you safe and connect you with caregivers nearby.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PrimaryActionButton(
            text = if (liveLat != null) "Location captured ✓" else "Use my location",
            onClick = onUseLocation,
            modifier = Modifier.fillMaxWidth()
        )
        if (liveLat != null && liveLng != null) {
            Text(
                text = "Lat ${"%.5f".format(liveLat)}, Lng ${"%.5f".format(liveLng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        if (!locationHint.isNullOrBlank()) {
            Text(
                text = locationHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun validateSignUpStep(
    step: Int,
    state: com.example.recallai.auth.SignUpUiState,
    isPatient: Boolean
): String? {
    return when (step) {
        0 -> when {
            state.name.isBlank() -> "Please enter your name."
            state.email.isBlank() -> "Please enter your email."
            state.password.isBlank() -> "Please enter a password."
            state.phone.isBlank() -> "Please enter your phone number."
            else -> null
        }
        1 -> when {
            state.age.isBlank() -> "Please enter your age."
            state.dateOfBirth.isBlank() -> "Please pick your birth date."
            state.gender.isBlank() -> "Please select your gender."
            state.city.isBlank() -> "Please enter your city."
            state.location.isBlank() -> "Please enter your area or address."
            else -> null
        }
        else -> if (isPatient && (state.liveLat == null || state.liveLng == null)) {
            "Please capture your location to continue."
        } else {
            null
        }
    }
}

private fun fetchSignUpLocation(
    context: android.content.Context,
    fused: FusedLocationProviderClient,
    viewModel: SignUpViewModel
) {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) {
        viewModel.onLiveLocationDenied()
        return
    }
    val token = CancellationTokenSource().token
    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                viewModel.onLiveLocationResolved(loc.latitude, loc.longitude)
            } else {
                fused.lastLocation.addOnSuccessListener { last ->
                    if (last != null) {
                        viewModel.onLiveLocationResolved(last.latitude, last.longitude)
                    } else {
                        viewModel.onLiveLocationFailed("Could not read GPS yet. Try again in a few seconds.")
                    }
                }
            }
        }
        .addOnFailureListener {
            viewModel.onLiveLocationFailed(it.message ?: "Location failed")
        }
}
