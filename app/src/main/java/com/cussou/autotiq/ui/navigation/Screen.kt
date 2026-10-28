package com.cussou.autotiq.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object EventLog : Screen("event_log")
}
