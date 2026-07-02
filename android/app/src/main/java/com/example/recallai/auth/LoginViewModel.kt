package com.example.recallai.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.AuthRepository
import com.example.recallai.data.CareRepository
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.fcm.FcmRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val careRepository: CareRepository,
    private val memoryRepository: MemoryRepository,
    private val careToolkitRepository: CareToolkitRepository
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onEmailChange(value: String) {
        uiState = uiState.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun login(role: String, onSuccess: () -> Unit) {
        val email = uiState.email.trim()
        val password = uiState.password.trim()
        if (email.isEmpty() || password.isEmpty() || uiState.isLoading) return

        uiState = uiState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                repo.login(email, password, role = role)
                runCatching { FcmRegistrar.registerIfLoggedIn(careRepository) }
                if (role.equals("caregiver", ignoreCase = true)) {
                    runCatching { careRepository.ensureDefaultPatientSelected() }
                }
                launch { memoryRepository.syncFromServer() }
                launch { careToolkitRepository.syncAllCloudToolkit() }
                uiState = uiState.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                repo.logout()
                val raw = e.message.orEmpty()
                val friendly = if (
                    raw.contains("different role", ignoreCase = true) ||
                    raw.contains("belongs to a different role", ignoreCase = true)
                ) {
                    "This account is registered for a different role. Choose the correct login type."
                } else if (
                    raw.contains("401") ||
                    raw.contains("invalid", ignoreCase = true) ||
                    raw.contains("unauthorized", ignoreCase = true) ||
                    raw.contains("password", ignoreCase = true) ||
                    raw.contains("email", ignoreCase = true) ||
                    raw.contains("credentials", ignoreCase = true)
                ) {
                    "Wrong password or email. Try again."
                } else {
                    "Login failed. Please try again."
                }
                uiState = uiState.copy(
                    isLoading = false,
                    error = friendly
                )
            }
        }
    }
}