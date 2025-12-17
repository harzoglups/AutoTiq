package com.cussou.autotiq.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cussou.autotiq.ui.map.MapScreen
import com.cussou.autotiq.ui.onboarding.OnboardingScreen
import com.cussou.autotiq.ui.settings.SettingsScreen
import com.cussou.autotiq.ui.splash.SplashScreen

private const val PREFS_NAME = "autotiq_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current
    val onboardingCompleted = remember { isOnboardingCompleted(context) }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToNext = {
                    if (onboardingCompleted) {
                        navController.navigate(Screen.Map.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    markOnboardingCompleted(context)
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Map.route) {
            MapScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun isOnboardingCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
}

private fun markOnboardingCompleted(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
}
