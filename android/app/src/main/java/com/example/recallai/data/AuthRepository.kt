package com.example.recallai.data

import android.content.Context
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.LoginRequest
import com.example.recallai.data.remote.LoginResponse
import com.example.recallai.data.remote.RegisterRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val memoryRepository: MemoryRepository
) {

    private val api get() = ApiClient.api
    private val tokenStore get() = TokenStore(appContext)

    val isLoggedIn: Boolean
        get() = !AuthManager.token.isNullOrBlank()

    suspend fun login(email: String, password: String, role: String): LoginResponse = withContext(Dispatchers.IO) {
        val normalizedRole = role.trim().lowercase()
        val res = api.login(LoginRequest(email = email.trim(), password = password, role = normalizedRole))
        AuthManager.token = res.token
        AuthManager.userId = res.user?._id
        AuthManager.userName = res.user?.name
        AuthManager.userGender = res.user?.gender
        AuthManager.userRole = res.user?.role ?: normalizedRole
        tokenStore.token = res.token
        tokenStore.userId = res.user?._id
        tokenStore.role = AuthManager.userRole
        RecallAiPreferences.mergeProfileAfterServerAuth(appContext)
        res
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
        age: Int? = null,
        dateOfBirth: String? = null,
        city: String? = null,
        location: String? = null,
        gender: String? = null,
        role: String,
        liveLat: Double? = null,
        liveLng: Double? = null
    ) = withContext(Dispatchers.IO) {
        val normalizedRole = role.trim().lowercase()
        val response = api.register(
            RegisterRequest(
                name = name.trim(),
                email = email.trim(),
                password = password,
                phone = phone.trim(),
                age = age,
                dateOfBirth = dateOfBirth?.trim()?.takeIf { it.isNotBlank() },
                city = city?.trim()?.takeIf { it.isNotBlank() },
                location = location?.trim()?.takeIf { it.isNotBlank() },
                gender = gender?.trim()?.takeIf { it.isNotBlank() }?.lowercase(),
                role = normalizedRole,
                liveLat = liveLat,
                liveLng = liveLng
            )
        )
        AuthManager.userId = response.user?._id
        AuthManager.userName = response.user?.name ?: name.trim()
        AuthManager.userGender = response.user?.gender ?: gender?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        AuthManager.userRole = response.user?.role ?: normalizedRole
        RecallAiPreferences.mergeProfileAfterServerAuth(appContext)
        response
    }

    fun logout() {
        AuthManager.token = null
        AuthManager.userId = null
        AuthManager.userName = null
        AuthManager.userGender = null
        AuthManager.userRole = null
        AuthManager.avatarLocalPath = null
        AuthManager.bio = null
        tokenStore.clear()
        runBlocking { memoryRepository.clearAllMemories() }
    }
}