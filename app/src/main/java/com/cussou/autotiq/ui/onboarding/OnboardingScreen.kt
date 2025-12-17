package com.cussou.autotiq.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cussou.autotiq.R
import com.cussou.autotiq.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    
    // Permission states
    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }
    var hasBackgroundPermission by remember {
        mutableStateOf(checkBackgroundLocationPermission(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    
    // Location permission launcher
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
        
        if (hasLocationPermission) {
            // Move to background permission step
            currentStep = OnboardingStep.BACKGROUND_LOCATION
        }
    }
    
    // Background location permission launcher
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBackgroundPermission = isGranted
        if (isGranted) {
            // Move to notification permission step if Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentStep = OnboardingStep.NOTIFICATIONS
            } else {
                currentStep = OnboardingStep.COMPLETE
            }
        } else {
            // Continue even if background permission denied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentStep = OnboardingStep.NOTIFICATIONS
            } else {
                currentStep = OnboardingStep.COMPLETE
            }
        }
    }
    
    // Notification permission launcher (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        currentStep = OnboardingStep.COMPLETE
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            OnboardingStep.WELCOME -> {
                WelcomeStep(
                    onNext = {
                        if (hasLocationPermission) {
                            currentStep = OnboardingStep.BACKGROUND_LOCATION
                        } else {
                            currentStep = OnboardingStep.LOCATION
                        }
                    }
                )
            }
            OnboardingStep.LOCATION -> {
                LocationPermissionStep(
                    onRequestPermission = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onSkip = {
                        currentStep = OnboardingStep.BACKGROUND_LOCATION
                    }
                )
            }
            OnboardingStep.BACKGROUND_LOCATION -> {
                BackgroundLocationPermissionStep(
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    onSkip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            currentStep = OnboardingStep.NOTIFICATIONS
                        } else {
                            currentStep = OnboardingStep.COMPLETE
                        }
                    }
                )
            }
            OnboardingStep.NOTIFICATIONS -> {
                NotificationPermissionStep(
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSkip = {
                        currentStep = OnboardingStep.COMPLETE
                    }
                )
            }
            OnboardingStep.COMPLETE -> {
                CompleteStep(
                    onFinish = onComplete,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.welcome_to_autotiq),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.get_started))
        }
    }
}

@Composable
private fun LocationPermissionStep(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    PermissionStepCard(
        icon = Icons.Default.LocationOn,
        title = stringResource(R.string.onboarding_location_title),
        description = stringResource(R.string.onboarding_location_description),
        onRequestPermission = onRequestPermission,
        onSkip = onSkip
    )
}

@Composable
private fun BackgroundLocationPermissionStep(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    PermissionStepCard(
        icon = Icons.Default.LocationOn,
        title = stringResource(R.string.onboarding_background_location_title),
        description = stringResource(R.string.onboarding_background_location_description),
        onRequestPermission = onRequestPermission,
        onSkip = onSkip,
        important = true
    )
}

@Composable
private fun NotificationPermissionStep(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    PermissionStepCard(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.onboarding_notifications_title),
        description = stringResource(R.string.onboarding_notifications_description),
        onRequestPermission = onRequestPermission,
        onSkip = onSkip
    )
}

@Composable
private fun CompleteStep(
    onFinish: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    // Enable background tracking when reaching this step
    LaunchedEffect(Unit) {
        settingsViewModel.updateLocationTracking(true)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_using_app))
        }
    }
}

@Composable
private fun PermissionStepCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit,
    important: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (important) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_allow))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_later))
            }
        }
    }
}

private enum class OnboardingStep {
    WELCOME,
    LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
    COMPLETE
}

private fun checkLocationPermission(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    
    return hasFine || hasCoarse
}

private fun checkBackgroundLocationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Not needed on older Android versions
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Not needed on older Android versions
    }
}
