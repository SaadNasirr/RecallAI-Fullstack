package com.example.recallai.data.remote

import android.os.Build
import com.example.recallai.BuildConfig
import com.example.recallai.data.AuthManager
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi

object ApiClient {

    private const val AUTH_HEADER = "Authorization"
    @Volatile
    private var currentBaseUrl: String? = null
    @Volatile
    private var currentApi: RecallAiApi? = null

    private fun resolvedBaseUrl(): String {
        when {
            isProbablyEmulator() -> return BuildConfig.EMULATOR_BASE_URL
            !BuildConfig.USE_DYNAMIC_BASE_URL && BuildConfig.BASE_URL.isNotBlank() -> return BuildConfig.BASE_URL
            else -> return BuildConfig.DEVICE_BASE_URL
        }
    }

    private fun isProbablyEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT ||
            Build.HARDWARE.contains("ranchu")
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = AuthManager.token
        val newRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .addHeader(AUTH_HEADER, "Bearer $token")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 32
            }
        )
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder()
        .add(EmbeddedUserRefJsonAdapterFactory())
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: RecallAiApi
        get() {
            val baseUrl = resolvedBaseUrl()
            val cachedApi = currentApi
            if (cachedApi != null && currentBaseUrl == baseUrl) return cachedApi

            return synchronized(this) {
                if (currentApi != null && currentBaseUrl == baseUrl) {
                    currentApi!!
                } else {
                    val retrofit = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(okHttpClient)
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                    val newApi = retrofit.create(RecallAiApi::class.java)
                    currentBaseUrl = baseUrl
                    currentApi = newApi
                    newApi
                }
            }
        }
}