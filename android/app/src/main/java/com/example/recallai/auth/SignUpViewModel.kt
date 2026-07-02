package com.example.recallai.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val age: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val city: String = "",
    val location: String = "",
    /** Captured after location permission + GPS read (patient signup). */
    val liveLat: Double? = null,
    val liveLng: Double? = null,
    val locationHint: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    var uiState = androidx.compose.runtime.mutableStateOf(SignUpUiState())
        private set

    fun onNameChange(value: String) {
        uiState.value = uiState.value.copy(name = value, error = null)
    }

    fun onEmailChange(value: String) {
        uiState.value = uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState.value = uiState.value.copy(password = value, error = null)
    }

    fun onPhoneChange(value: String) {
        uiState.value = uiState.value.copy(phone = value, error = null)
    }

    fun onAgeChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(3)
        uiState.value = uiState.value.copy(age = filtered, error = null)
    }

    fun onDateOfBirthChange(value: String) {
        uiState.value = uiState.value.copy(dateOfBirth = value, error = null)
    }

    fun onCityChange(value: String) {
        uiState.value = uiState.value.copy(city = value, error = null)
    }

    fun onGenderChange(value: String) {
        uiState.value = uiState.value.copy(gender = value, error = null)
    }

    fun onLocationChange(value: String) {
        uiState.value = uiState.value.copy(location = value, error = null)
    }

    fun onLiveLocationResolved(latitude: Double, longitude: Double) {
        uiState.value = uiState.value.copy(
            liveLat = latitude,
            liveLng = longitude,
            locationHint = "Live location captured for your profile.",
            error = null
        )
    }

    fun onLiveLocationFailed(message: String) {
        uiState.value = uiState.value.copy(locationHint = message)
    }

    fun onLiveLocationDenied() {
        uiState.value = uiState.value.copy(
            locationHint = "Location permission is required to register as a patient."
        )
    }

    fun signUp(role: String, onSuccess: () -> Unit) {
        val normalizedRole = role.trim().lowercase()
        val name = uiState.value.name.trim()
        val email = uiState.value.email.trim()
        val password = uiState.value.password.trim()
        val phone = uiState.value.phone.trim()
        val age = uiState.value.age.trim()
        val dateOfBirth = uiState.value.dateOfBirth.trim()
        val gender = uiState.value.gender.trim()
        val city = uiState.value.city.trim()
        val location = uiState.value.location.trim()

        if (
            name.isEmpty() || email.isEmpty() || password.isEmpty() ||
            phone.isEmpty() ||
            age.isEmpty() || dateOfBirth.isEmpty() || gender.isEmpty() || city.isEmpty() || location.isEmpty() ||
            uiState.value.isLoading
        ) {
            uiState.value = uiState.value.copy(error = "Please fill all required fields.")
            return
        }

        if (normalizedRole == "patient") {
            val lat = uiState.value.liveLat
            val lng = uiState.value.liveLng
            if (lat == null || lng == null) {
                uiState.value = uiState.value.copy(
                    error = "Allow location access and capture your position to continue."
                )
                return
            }
        }

        val ageInt = age.toIntOrNull()
        if (ageInt == null || ageInt !in 1..120) {
            uiState.value = uiState.value.copy(error = "Age must be between 1 and 120.")
            return
        }
        if (!isValidDob(dateOfBirth)) {
            uiState.value = uiState.value.copy(error = "Date of birth must be valid (DD/MM/YYYY).")
            return
        }

        uiState.value = uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val lat = uiState.value.liveLat
                val lng = uiState.value.liveLng
                repo.register(
                    name = name,
                    email = email,
                    password = password,
                    phone = phone,
                    age = ageInt,
                    dateOfBirth = dateOfBirth,
                    gender = gender,
                    city = city,
                    location = location,
                    role = normalizedRole,
                    liveLat = if (normalizedRole == "patient") lat else null,
                    liveLng = if (normalizedRole == "patient") lng else null
                )
                uiState.value = uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Sign up failed"
                )
            }
        }
    }

    private fun isValidDob(value: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                isLenient = false
            }
            val parsed = formatter.parse(value) ?: return false
            !parsed.after(Date())
        } catch (_: Exception) {
            false
        }
    }
}
