package com.cussou.autotiq.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cussou.autotiq.BuildConfig
import com.cussou.autotiq.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val importExportEvent by viewModel.importExportEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }
    
    // Launcher for background location permission (Android 10+)
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onBackgroundLocationPermissionResult(isGranted)
    }
    
    // Launcher for exporting zones
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.performExport(it) }
    }
    
    // Launcher for importing zones
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.performImport(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.location_tracking),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Permission status card for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (permissionStatus) {
                    PermissionStatus.NOT_GRANTED -> {
                        SettingCard {
                            Column {
                                Text(
                                    text = stringResource(R.string.permission_background_location_required),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.permission_location_rationale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = { 
                                        showBackgroundLocationDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.permission_background_location_button))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    PermissionStatus.FOREGROUND_ONLY -> {
                        SettingCard {
                            Column {
                                Text(
                                    text = stringResource(R.string.permission_foreground_only),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.permission_background_location_rationale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = { 
                                        showBackgroundLocationDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.permission_allow_always))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    PermissionStatus.GRANTED -> {
                        // Permission granted, no need to show warning
                    }
                }
            }

            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_background_tracking),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.enable_background_tracking_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isLocationTrackingEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // Check if we have background permission before enabling
                                if (permissionStatus != PermissionStatus.GRANTED) {
                                    showBackgroundLocationDialog = true
                                } else {
                                    viewModel.updateLocationTracking(true)
                                }
                            } else {
                                viewModel.updateLocationTracking(enabled)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.active_days),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.active_days_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val weekdayNames = listOf(
                        1 to R.string.monday_short,
                        2 to R.string.tuesday_short,
                        3 to R.string.wednesday_short,
                        4 to R.string.thursday_short,
                        5 to R.string.friday_short,
                        6 to R.string.saturday_short,
                        7 to R.string.sunday_short
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekdayNames.forEach { (dayNumber, dayNameRes) ->
                            WeekdayButton(
                                text = stringResource(dayNameRes),
                                isSelected = settings.activeWeekdays.contains(dayNumber),
                                onClick = {
                                    val newWeekdays = if (settings.activeWeekdays.contains(dayNumber)) {
                                        settings.activeWeekdays - dayNumber
                                    } else {
                                        settings.activeWeekdays + dayNumber
                                    }
                                    viewModel.updateActiveWeekdays(newWeekdays)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.detection_parameters),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingCard {
                Column {
                    var intervalText by remember(settings.checkIntervalSeconds) {
                        mutableStateOf<String>(settings.checkIntervalSeconds.toString())
                    }

                    Text(
                        text = stringResource(R.string.check_interval),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.check_interval_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { newValue: String ->
                            intervalText = newValue
                            newValue.toIntOrNull()?.let { seconds: Int ->
                                if (seconds > 0) {
                                    viewModel.updateCheckInterval(seconds)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    var distanceText by remember(settings.proximityDistanceMeters) {
                        mutableStateOf<String>(settings.proximityDistanceMeters.toString())
                    }

                    Text(
                        text = stringResource(R.string.proximity_distance),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.proximity_distance_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { newValue: String ->
                            distanceText = newValue
                            newValue.toIntOrNull()?.let { meters: Int ->
                                if (meters > 0) {
                                    viewModel.updateProximityDistance(meters)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.meters)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    var vibrationText by remember(settings.vibrationCount) {
                        mutableStateOf<String>(settings.vibrationCount.toString())
                    }

                    Text(
                        text = stringResource(R.string.vibration_count),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.vibration_count_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = vibrationText,
                        onValueChange = { newValue: String ->
                            vibrationText = newValue
                            newValue.toIntOrNull()?.let { count: Int ->
                                if (count > 0) {
                                    viewModel.updateVibrationCount(count)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.vibrations)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    Text(
                        text = "Test Vibration",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Test if vibration works with alarm attributes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            // Test vibration
                            try {
                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                                    vibratorManager?.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                                }
                                
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    // minSdk 26+ guarantees VibrationEffect availability
                                    val audioAttributes = AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                    
                                    val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500, 200, 500)
                                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
                                    val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                                    
                                    // Suppress deprecation warning - needed for background vibration
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(effect, audioAttributes)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Error testing vibration", e)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Vibration Now")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.backup_restore),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.export_zones),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Export all zones to a JSON file for backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedButton(
                        onClick = { 
                            exportLauncher.launch("autotiq_zones_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_zones))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.import_zones),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Import zones from a JSON file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedButton(
                        onClick = { 
                            importLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.import_zones))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.info),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.app_version),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.how_it_works),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.how_it_works_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard {
                Column {
                    Text(
                        text = stringResource(R.string.map_data),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.map_data_osm_attribution),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
    
    // Handle import/export events
    importExportEvent?.let { event ->
        when (event) {
            is ImportExportEvent.ShowToast -> {
                // Show toast message
                android.widget.Toast.makeText(
                    context,
                    if (event.args.isEmpty()) {
                        stringResource(event.messageResId)
                    } else {
                        stringResource(event.messageResId, *event.args)
                    },
                    android.widget.Toast.LENGTH_LONG
                ).show()
                viewModel.clearEvent()
            }
            is ImportExportEvent.ShowImportDialog -> {
                // Not implemented yet - will be added in next iteration
                viewModel.clearEvent()
            }
        }
    }
    
    // Background location permission explanation dialog
    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            title = { 
                Text(stringResource(R.string.permission_background_location_required)) 
            },
            text = { 
                Text(stringResource(R.string.permission_background_location_rationale))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundLocationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Try to request background location permission
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                ) {
                    Text(stringResource(R.string.permission_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showBackgroundLocationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.permission_later))
                }
            }
        )
    }
}

@Composable
private fun SettingCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun WeekdayButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
