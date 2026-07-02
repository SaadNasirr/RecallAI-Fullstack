package com.example.recallai.ui.screens

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.recallai.R
import com.example.recallai.data.AuthManager
import com.example.recallai.data.RecallAiPreferences
import com.example.recallai.data.TokenStore
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.di.CareRepositoryEntryPoint
import com.example.recallai.di.CareToolkitRepositoryEntryPoint
import com.example.recallai.di.MemoryRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val RecallAiPurple = Color(0xFF6B4EFF)

@Composable
fun SplashScreen(
    onFinished: (String) -> Unit
) {
    val view = LocalView.current
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (prevLight != null) {
                controller.isAppearanceLightStatusBars = prevLight
            }
        }
    }

    val bgLight = Color(0xFFF0EFFE)
    val bgBottom = Color(0xFFE4DFFE)
    val primary = Color(0xFF7B6CF5)
    val pink = Color(0xFFFF6B9D)
    val textMuted = Color(0xFF8A80C4)
    val trackColor = Color(0xFFD4CFF0)

    var progressTarget by remember { mutableFloatStateOf(0f) }
    var contentAlpha by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Starting up…") }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
        label = "splashProgress"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = contentAlpha,
        animationSpec = tween(durationMillis = 700),
        label = "splashAlpha"
    )

    val shimmerTransition = rememberInfiniteTransition(label = "splashShimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    LaunchedEffect(Unit) {
        contentAlpha = 1f
        delay(200)
        progressTarget = 0.18f
        statusText = "Preparing RecallAI…"
        delay(500)
        progressTarget = 0.48f
        statusText = "Checking your account…"
        val startRoute = withContext(Dispatchers.IO) { resolveStartupDestination(appContext) }
        progressTarget = 0.82f
        statusText = "Almost ready…"
        delay(400)
        progressTarget = 1f
        statusText = "Welcome back"
        delay(350)
        onFinished(startRoute)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(bgLight, bgBottom)))
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-70).dp)
                .background(
                    Brush.radialGradient(colors = listOf(pink.copy(alpha = 0.12f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-90).dp, y = 120.dp)
                .background(
                    Brush.radialGradient(colors = listOf(primary.copy(alpha = 0.12f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .alpha(animatedAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.recallai_logo),
                contentDescription = "RecallAI logo",
                modifier = Modifier
                    .width(240.dp)
                    .wrapContentHeight(),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "RecallAI",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = RecallAiPurple,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Helping you remember what really matters",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                .alpha(animatedAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = textMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(colors = listOf(primary, pink)))
                )
                if (animatedProgress in 0.02f..0.98f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.45f),
                                        Color.Transparent
                                    ),
                                    startX = shimmerOffset * 800f,
                                    endX = (shimmerOffset + 0.35f) * 800f
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${(animatedProgress * 100).toInt().coerceIn(0, 100)}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = primary
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/** Restore JWT from disk and validate with GET /auth/me so API calls include Authorization. */
private suspend fun resolveStartupDestination(context: android.content.Context): String {
    val store = TokenStore(context)
    val t = store.token
    if (t.isNullOrBlank()) return AuthRoute.RoleSelection.route
    AuthManager.token = t
    AuthManager.userId = store.userId
    AuthManager.userRole = store.role
    return try {
        val me = ApiClient.api.getMe()
        AuthManager.userId = me.user?._id ?: store.userId
        AuthManager.userName = me.user?.name
        AuthManager.userGender = me.user?.gender
        me.user?.role?.trim()?.takeIf { it.isNotBlank() }?.let {
            AuthManager.userRole = it
        }
        RecallAiPreferences.mergeProfileAfterServerAuth(context)
        runCatching {
            val careEntry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                CareRepositoryEntryPoint::class.java
            )
            if (me.user?.role?.trim()?.equals("caregiver", ignoreCase = true) == true) {
                careEntry.careRepository().ensureDefaultPatientSelected()
            }
        }
        runCatching {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                MemoryRepositoryEntryPoint::class.java
            )
            entry.memoryRepository().syncFromServer()
        }
        runCatching {
            val careEntry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                CareToolkitRepositoryEntryPoint::class.java
            )
            careEntry.careToolkitRepository().syncAllCloudToolkit()
        }
        when (me.user?.role?.trim()?.lowercase()) {
            "caregiver" -> AuthRoute.CaregiverShell.route
            else -> AuthRoute.PatientShell.route
        }
    } catch (_: Exception) {
        AuthManager.token = null
        AuthManager.userId = null
        AuthManager.userName = null
        AuthManager.userGender = null
        AuthManager.userRole = null
        store.clear()
        AuthRoute.RoleSelection.route
    }
}
