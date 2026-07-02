package com.example.recallai.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.recallai.ui.screens.AuthRoute

/** Reliable return to role picker from login/signup (pop or navigate if stack is empty). */
fun NavController.backToRoleSelection() {
    val popped = popBackStack(AuthRoute.RoleSelection.route, inclusive = false)
    if (!popped) {
        navigate(AuthRoute.RoleSelection.route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}

/** Reliable return to login from signup. */
fun NavController.backToLogin(route: String) {
    val popped = popBackStack(route, inclusive = false)
    if (!popped) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

/**
 * Instant bottom-tab switch: no-op when already selected; single back-stack entry per tab.
 */
fun NavController.switchShellTab(tabRoute: String, currentRoute: String?) {
    if (currentRoute == tabRoute) return
    val startId = graph.findStartDestination().id
    navigate(tabRoute) {
        popUpTo(startId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavController.popBackImmediate(): Boolean = popBackStack()
