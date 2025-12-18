package com.cussou.autotiq.ui.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cussou.autotiq.R
import com.cussou.autotiq.domain.model.MapLayerType
import com.cussou.autotiq.domain.model.MapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastAddedPointId by viewModel.lastAddedPointId.collectAsStateWithLifecycle()
    val savedMapPosition by viewModel.savedMapPosition.collectAsStateWithLifecycle()
    var showLayerMenu by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<MapPoint?>(null) }
    var selectedPoint by remember { mutableStateOf<MapPoint?>(null) } // For showing info bubble
    var pointToDelete by remember { mutableStateOf<MapPoint?>(null) } // For delete confirmation dialog
    
    // Force dark status bar icons (black) on map screen
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }
    
    // When a new point is added, automatically open edit dialog
    androidx.compose.runtime.LaunchedEffect(lastAddedPointId) {
        if (lastAddedPointId != null && uiState is MapUiState.Success) {
            val newPoint = (uiState as MapUiState.Success).points.find { it.id == lastAddedPointId }
            if (newPoint != null) {
                editingPoint = newPoint
                selectedPoint = null // Close info card if open
                viewModel.clearLastAddedPointId()
            }
        }
    }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    when (val state = uiState) {
        is MapUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is MapUiState.Success -> {
            var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
            var mapView by remember { mutableStateOf<MapView?>(null) }
            var searchQuery by remember { mutableStateOf("") }
            var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
            var isSearching by remember { mutableStateOf(false) }
            var searchMarker by remember { mutableStateOf<Marker?>(null) } // Temporary marker for search results
            var currentMarkerIndex by remember { mutableStateOf(0) } // For marker navigation
            
            Box(modifier = Modifier.fillMaxSize()) {
                MapContent(
                    points = state.points,
                    proximityDistanceMeters = state.settings.proximityDistanceMeters,
                    mapLayerType = state.settings.mapLayerType,
                    hasLocationPermission = hasLocationPermission,
                    savedMapPosition = savedMapPosition,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onAddPoint = { lat, lon -> viewModel.addPoint(lat, lon) },
                    onRequestDeletePoint = { point -> 
                        // Show confirmation dialog
                        pointToDelete = point
                    },
                    onMarkerClick = { point -> 
                        // Hide keyboard when clicking on marker
                        keyboardController?.hide()
                        // Toggle: if same point clicked, close info; otherwise show info
                        selectedPoint = if (selectedPoint?.id == point.id) null else point
                    },
                    onMapClick = { 
                        // Hide keyboard when clicking on map
                        keyboardController?.hide()
                        selectedPoint = null // Close info card when clicking on map
                        // Remove search marker when clicking on map
                        searchMarker?.let { marker ->
                            // Recycle bitmap to free memory
                            marker.icon?.let { drawable ->
                                if (drawable is android.graphics.drawable.BitmapDrawable) {
                                    drawable.bitmap?.recycle()
                                }
                            }
                            mapView?.overlays?.remove(marker)
                            mapView?.invalidate()
                            searchMarker = null
                        }
                    },
                    onLocationOverlayReady = { overlay -> locationOverlay = overlay },
                    onMapViewReady = { view -> mapView = view },
                    onSaveMapPosition = { lat, lon, zoom -> 
                        viewModel.saveMapPosition(lat, lon, zoom)
                    },
                    searchMarker = searchMarker,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Search bar (top center)
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { query ->
                        searchQuery = query
                        if (query.length >= 3) {
                            isSearching = true
                            // Get current map bounds for search prioritization
                            val viewbox = mapView?.boundingBox?.let { bbox ->
                                Viewbox(
                                    minLat = bbox.latSouth,
                                    maxLat = bbox.latNorth,
                                    minLon = bbox.lonWest,
                                    maxLon = bbox.lonEast
                                )
                            }
                            // Perform search with debounce
                            viewModel.searchLocation(query, viewbox) { results ->
                                // Keep previous results if new search returns nothing
                                if (results.isNotEmpty()) {
                                    searchResults = results
                                }
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                            isSearching = false
                        }
                    },
                    onClear = {
                        searchQuery = ""
                        searchResults = emptyList()
                    },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onResultClick = { result ->
                        // Hide keyboard when selecting a search result
                        keyboardController?.hide()
                        
                        mapView?.let { map ->
                            // Remove previous search marker if exists and recycle bitmap
                            searchMarker?.let { marker ->
                                marker.icon?.let { drawable ->
                                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                                        drawable.bitmap?.recycle()
                                    }
                                }
                                map.overlays.remove(marker)
                            }
                            
                            // Create new temporary marker for search result (red colored, larger and more visible)
                            val marker = Marker(map).apply {
                                position = GeoPoint(result.lat, result.lon)
                                title = result.displayName
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                
                                // Mark this as a search marker using relatedObject
                                relatedObject = "SEARCH_MARKER"
                                
                                // Create a larger red pin-style marker to differentiate from user markers
                                val size = 80 // Increased size for better visibility
                                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                
                                // Draw a pin shape (circle on top, triangle pointing down)
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.RED
                                    style = android.graphics.Paint.Style.FILL
                                    isAntiAlias = true
                                }
                                val strokePaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = 4f
                                    isAntiAlias = true
                                }
                                
                                // Draw circle (top part of pin)
                                val circleRadius = size / 4f
                                val circleCenterX = size / 2f
                                val circleCenterY = circleRadius + 5
                                canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, paint)
                                canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, strokePaint)
                                
                                // Draw triangle pointing down (bottom part of pin)
                                val path = android.graphics.Path().apply {
                                    moveTo(circleCenterX - circleRadius / 2, circleCenterY + circleRadius)
                                    lineTo(circleCenterX, size - 5f)
                                    lineTo(circleCenterX + circleRadius / 2, circleCenterY + circleRadius)
                                    close()
                                }
                                canvas.drawPath(path, paint)
                                canvas.drawPath(path, strokePaint)
                                
                                icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                            }
                            
                            map.overlays.add(marker)
                            searchMarker = marker
                            
                            android.util.Log.d("MapScreen", "Added search marker at ${result.lat}, ${result.lon}")
                            
                            map.invalidate()
                            
                            // Animate to location
                            map.controller.animateTo(GeoPoint(result.lat, result.lon))
                            map.controller.setZoom(15.0)
                        }
                        
                        searchQuery = ""
                        searchResults = emptyList()
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .fillMaxWidth(0.9f)
                )
                
                // Show info card for selected marker
                selectedPoint?.let { point ->
                    MarkerInfoCard(
                        point = point,
                        onEdit = { 
                            editingPoint = point
                        },
                        onClose = { selectedPoint = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 80.dp) // Space for horizontal button row
                            .padding(horizontal = 16.dp)
                    )
                }
                
                // Floating layer selection button (top right, below search bar)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 96.dp, end = 16.dp) // Extra top padding to avoid search bar overlap
                ) {
                    SmallFloatingActionButton(
                        onClick = { showLayerMenu = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Map layers")
                    }
                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false }
                    ) {
                        MapLayerType.entries.forEach { layerType ->
                            DropdownMenuItem(
                                text = { Text(layerType.displayName(LocalContext.current)) },
                                onClick = {
                                    viewModel.updateMapLayer(layerType)
                                    showLayerMenu = false
                                },
                                leadingIcon = if (layerType == state.settings.mapLayerType) {
                                    { Text("✓") }
                                } else null
                            )
                        }
                    }
                }
                
                // Floating buttons (bottom center, horizontal row)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigate between markers button (only show if there are markers)
                    if (state.points.isNotEmpty()) {
                        SmallFloatingActionButton(
                            onClick = {
                                // Navigate to next marker
                                currentMarkerIndex = (currentMarkerIndex + 1) % state.points.size
                                val targetPoint = state.points[currentMarkerIndex]
                                mapView?.controller?.animateTo(GeoPoint(targetPoint.latitude, targetPoint.longitude))
                                mapView?.controller?.setZoom(16.0)
                                // Show info card for the marker
                                selectedPoint = targetPoint
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${currentMarkerIndex + 1}/${state.points.size}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext, 
                                    contentDescription = "Next marker",
                                    modifier = Modifier.padding(0.dp)
                                )
                            }
                        }
                    }
                    
                    // GPS location button
                    SmallFloatingActionButton(
                        onClick = {
                            locationOverlay?.myLocation?.let { location ->
                                mapView?.controller?.animateTo(location)
                                mapView?.controller?.setZoom(17.0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
                    
                    // Settings button
                    SmallFloatingActionButton(
                        onClick = onNavigateToSettings,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
    }
    
    // Show edit dialog if a point is being edited
    editingPoint?.let { point ->
        EditMarkerDialog(
            point = point,
            onDismiss = { editingPoint = null },
            onSave = { updatedPoint ->
                viewModel.updatePoint(updatedPoint)
                editingPoint = null
                // Update selected point if it's the same one
                if (selectedPoint?.id == updatedPoint.id) {
                    selectedPoint = updatedPoint
                }
            }
        )
    }
    
    // Show delete confirmation dialog if a point is marked for deletion
    pointToDelete?.let { point ->
        DeleteConfirmationDialog(
            point = point,
            onConfirm = {
                // Close dialogs if deleting the currently selected/editing point
                if (selectedPoint?.id == point.id) {
                    selectedPoint = null
                }
                if (editingPoint?.id == point.id) {
                    editingPoint = null
                }
                viewModel.deletePoint(point.id)
                pointToDelete = null
            },
            onDismiss = { pointToDelete = null }
        )
    }
}

@Composable
private fun MarkerInfoCard(
    point: MapPoint,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (point.name.isNotEmpty()) point.name else stringResource(R.string.point_number, point.id),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val startTime = String.format("%02d:%02d", point.startHour, point.startMinute)
                    val endTime = String.format("%02d:%02d", point.endHour, point.endMinute)
                    Text(
                        text = stringResource(R.string.active_time, startTime, endTime),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                androidx.compose.material3.TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit))
                }
            }
        }
    }
}

@Composable
private fun MapContent(
    points: List<MapPoint>,
    proximityDistanceMeters: Int,
    mapLayerType: MapLayerType,
    hasLocationPermission: Boolean,
    savedMapPosition: SavedMapPosition?,
    onRequestPermission: () -> Unit,
    onAddPoint: (Double, Double) -> Unit,
    onRequestDeletePoint: (MapPoint) -> Unit,
    onMarkerClick: (MapPoint) -> Unit,
    onMapClick: () -> Unit,
    onLocationOverlayReady: (MyLocationNewOverlay) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onSaveMapPosition: (Double, Double, Double) -> Unit,
    searchMarker: Marker? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    
    // Use rememberUpdatedState to ensure callbacks always use latest values
    val currentOnMarkerClick by androidx.compose.runtime.rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by androidx.compose.runtime.rememberUpdatedState(onMapClick)
    val currentOnAddPoint by androidx.compose.runtime.rememberUpdatedState(onAddPoint)
    val currentOnRequestDeletePoint by androidx.compose.runtime.rememberUpdatedState(onRequestDeletePoint)
    val currentPoints by androidx.compose.runtime.rememberUpdatedState(points)

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose {
            // Save current map position before cleanup
            mapView?.let { map ->
                val center = map.mapCenter
                val zoom = map.zoomLevelDouble
                onSaveMapPosition(center.latitude, center.longitude, zoom)
            }
            
            // Cleanup map resources to prevent memory leaks
            mapView?.let { map ->
                // Stop location overlay to prevent GPS from running in background
                map.overlays.filterIsInstance<MyLocationNewOverlay>().forEach { overlay ->
                    overlay.disableMyLocation()
                    overlay.disableFollowLocation()
                }
                
                // Recycle marker bitmaps to free memory
                map.overlays.filterIsInstance<Marker>().forEach { marker ->
                    marker.icon?.let { drawable ->
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            drawable.bitmap?.recycle()
                        }
                    }
                }
                
                // Clear all overlays
                map.overlays.clear()
                
                // CRITICAL: Force OSMDroid to clear tile cache from RAM
                // This is essential to free Graphics memory when leaving map screen
                map.tileProvider?.clearTileCache()
                map.invalidate()
                
                // Detach map view
                map.onDetach()
            }
            mapView = null
        }
    }

    if (!hasLocationPermission) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.permission_location_required),
                    style = MaterialTheme.typography.titleMedium
                )
                androidx.compose.material3.Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
        }
    } else {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapView = this
                    
                    // Notify that map view is ready
                    onMapViewReady(this)
                    
                    setTileSource(mapLayerType.toTileSource())
                    setMultiTouchControls(true)
                    
                    // Disable built-in zoom buttons to avoid overlap with custom floating buttons
                    setBuiltInZoomControls(false)
                    
                    // Optimize memory usage
                    // Limit how many tiles are kept in memory (default is way too high)
                    isTilesScaledToDpi = true
                    setUseDataConnection(true)
                    
                    // Restore saved position or set default position (e.g., Zurich, Switzerland)
                    if (savedMapPosition != null) {
                        // Restore previous map position
                        controller.setZoom(savedMapPosition.zoom)
                        controller.setCenter(GeoPoint(savedMapPosition.latitude, savedMapPosition.longitude))
                    } else {
                        // First time: set default position
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(47.3769, 8.5417))
                    }

                    // Enable location overlay
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    locationOverlay.enableMyLocation()
                    // Don't enable follow location - let user control the map manually
                    overlays.add(locationOverlay)
                    
                    // Notify that location overlay is ready
                    onLocationOverlayReady(locationOverlay)
                    
                    // Center on user location only on first launch (when no saved position)
                    if (savedMapPosition == null) {
                        // Try to get last known location from FusedLocationClient for instant centering
                        try {
                            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    // Use cached location immediately for fast startup
                                    post {
                                        controller.animateTo(GeoPoint(location.latitude, location.longitude))
                                        controller.setZoom(15.0)
                                    }
                                } else {
                                    // No cached location, wait for GPS fix (slower fallback)
                                    locationOverlay.runOnFirstFix {
                                        post {
                                            controller.animateTo(locationOverlay.myLocation)
                                            controller.setZoom(15.0)
                                        }
                                    }
                                }
                            }.addOnFailureListener {
                                // Fallback to waiting for GPS fix
                                locationOverlay.runOnFirstFix {
                                    post {
                                        controller.animateTo(locationOverlay.myLocation)
                                        controller.setZoom(15.0)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback to waiting for GPS fix
                            locationOverlay.runOnFirstFix {
                                post {
                                    controller.animateTo(locationOverlay.myLocation)
                                    controller.setZoom(15.0)
                                }
                            }
                        }
                    }

                    // Variables to track long press on map and markers
                    var touchStartTime = 0L
                    var touchStartX = 0f
                    var touchStartY = 0f
                    var touchedMarkerId: Long? = null
                    val longPressThreshold = 500L // milliseconds
                    val movementThreshold = 20f // pixels

                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchStartTime = System.currentTimeMillis()
                                touchStartX = event.x
                                touchStartY = event.y
                                
                                // Check if touch is on a marker
                                touchedMarkerId = null
                                val projection = this.projection
                                
                                // Find closest marker within touch range
                                overlays.filterIsInstance<Marker>().forEach { marker ->
                                    val markerScreenPoint = projection.toPixels(marker.position, null)
                                    val dx = event.x - markerScreenPoint.x
                                    val dy = event.y - markerScreenPoint.y
                                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                                    
                                    if (distance < 100) { // 100 pixels touch radius
                                        // Get the point ID from marker's relatedObject
                                        touchedMarkerId = marker.relatedObject as? Long
                                    }
                                }
                                false
                            }
                            MotionEvent.ACTION_UP -> {
                                val duration = System.currentTimeMillis() - touchStartTime
                                val dx = Math.abs(event.x - touchStartX)
                                val dy = Math.abs(event.y - touchStartY)
                                
                                if (dx < movementThreshold && dy < movementThreshold) {
                                    if (touchedMarkerId != null) {
                                        // Touch on marker
                                        if (duration >= longPressThreshold) {
                                            // Long press on marker - show delete confirmation
                                            val point = currentPoints.find { it.id == touchedMarkerId }
                                            point?.let { currentOnRequestDeletePoint(it) }
                                         } else {
                                            // Short click on marker - show info card
                                            val point = currentPoints.find { it.id == touchedMarkerId }
                                            point?.let { currentOnMarkerClick(it) }
                                        }
                                        true // Consume the event to prevent map interaction
                                    } else if (duration >= longPressThreshold) {
                                        // Long press on map - add point
                                        val projection = this.projection
                                        val geoPoint = projection.fromPixels(
                                            event.x.toInt(),
                                            event.y.toInt()
                                        ) as GeoPoint
                                        currentOnAddPoint(geoPoint.latitude, geoPoint.longitude)
                                        true
                                    } else {
                                        // Short click on map (not on marker) - close info card
                                        currentOnMapClick()
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                }
            },
            update = { map ->
                // Update tile source when layer changes
                if (map.tileProvider.tileSource != mapLayerType.toTileSource()) {
                    map.setTileSource(mapLayerType.toTileSource())
                }
                
                // Remove old markers and circles (but keep search marker and location overlay)
                // Note: We don't recycle bitmaps here because they will be recreated immediately
                // Recycling only happens in onDispose when leaving the screen entirely
                val overlaysToRemove = map.overlays.filter { overlay ->
                    when {
                        overlay is MyLocationNewOverlay -> false // Keep location overlay
                        overlay is Marker && overlay.relatedObject == "SEARCH_MARKER" -> false // Keep search marker
                        overlay is Marker || overlay is Polygon -> true // Remove user markers and circles
                        else -> false
                    }
                }
                
                map.overlays.removeAll(overlaysToRemove.toSet())
                
                // Add circles and markers for each point
                points.forEach { mapPoint ->
                    // Create circle overlay for proximity distance
                    val circlePoints = Polygon.pointsAsCircle(
                        GeoPoint(mapPoint.latitude, mapPoint.longitude),
                        proximityDistanceMeters.toDouble()
                    )
                    val circle = Polygon(map)
                    circle.points = circlePoints
                    circle.fillPaint.color = 0x20FF0000.toInt() // Semi-transparent red
                    circle.outlinePaint.color = 0x80FF0000.toInt() // Semi-transparent red border
                    circle.outlinePaint.strokeWidth = 2f
                    map.overlays.add(circle)
                    
                    // Create marker with custom blue pin icon and label
                    val marker = Marker(map).apply {
                        position = GeoPoint(mapPoint.latitude, mapPoint.longitude)
                        val markerName = if (mapPoint.name.isNotEmpty()) mapPoint.name else context.getString(R.string.point_number, mapPoint.id)
                        title = markerName
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        // Disable default info window since we have custom Compose card
                        infoWindow = null
                        // Store the point ID in the marker's related object for our touch handler
                        relatedObject = mapPoint.id
                        
                        // Measure text dimensions first
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLUE
                            textSize = 32f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val textBounds = android.graphics.Rect()
                        textPaint.getTextBounds(markerName, 0, markerName.length, textBounds)
                        val textWidth = textBounds.width() + 16 // Add padding
                        val textHeight = textBounds.height() + 8
                        
                        // Create bitmap with space for both pin and text
                        val pinSize = 80
                        val totalWidth = maxOf(pinSize, textWidth)
                        val totalHeight = pinSize + textHeight + 10 // 10px gap between text and pin
                        val bitmap = android.graphics.Bitmap.createBitmap(totalWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        
                        // Draw text background (rounded rectangle with white fill)
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val bgRect = android.graphics.RectF(
                            (totalWidth - textWidth) / 2f,
                            0f,
                            (totalWidth + textWidth) / 2f,
                            textHeight.toFloat()
                        )
                        canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
                        
                        // Draw text border (blue)
                        val borderPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLUE
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2f
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(bgRect, 8f, 8f, borderPaint)
                        
                        // Draw text (blue) - centered in the background rectangle
                        val textX = totalWidth / 2f - textBounds.width() / 2f
                        // Use baseline for proper vertical centering (account for descenders)
                        val textY = textHeight / 2f - textBounds.exactCenterY()
                        canvas.drawText(markerName, textX, textY, textPaint)
                        
                        // Draw pin below text
                        val pinOffsetX = (totalWidth - pinSize) / 2f
                        val pinOffsetY = textHeight + 10f
                        
                        val pinPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLUE
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val strokePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 4f
                            isAntiAlias = true
                        }
                        
                        // Draw circle (top part of pin)
                        val circleRadius = pinSize / 4f
                        val circleCenterX = pinOffsetX + pinSize / 2f
                        val circleCenterY = pinOffsetY + circleRadius + 5
                        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, pinPaint)
                        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, strokePaint)
                        
                        // Draw triangle pointing down (bottom part of pin)
                        val path = android.graphics.Path().apply {
                            moveTo(circleCenterX - circleRadius / 2, circleCenterY + circleRadius)
                            lineTo(circleCenterX, pinOffsetY + pinSize - 5f)
                            lineTo(circleCenterX + circleRadius / 2, circleCenterY + circleRadius)
                            close()
                        }
                        canvas.drawPath(path, pinPaint)
                        canvas.drawPath(path, strokePaint)
                        
                        icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                        
                        // Set anchor point to bottom center (tip of the pin)
                        setAnchor(0.5f, 1.0f)
                    }
                    
                    map.overlays.add(marker)
                }
                
                // Re-add search marker on top if it exists
                searchMarker?.let { search ->
                    // Remove it first if it's already in the list
                    map.overlays.remove(search)
                    // Add it at the end so it's on top
                    map.overlays.add(search)
                }
                
                map.invalidate()
            }
        )
    }
}

// Helper extension functions
private fun MapLayerType.toTileSource() = when (this) {
    MapLayerType.STREET -> TileSourceFactory.MAPNIK
    MapLayerType.TOPO -> TileSourceFactory.OpenTopo
}

private fun MapLayerType.displayName(context: android.content.Context) = when (this) {
    MapLayerType.STREET -> context.getString(R.string.map_layer_street)
    MapLayerType.TOPO -> context.getString(R.string.map_layer_topo)
}

// Search bar composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    searchResults: List<SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_location)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        
        // Search results dropdown
        if (searchResults.isNotEmpty() || isSearching) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(searchResults) { result ->
                            Text(
                                text = result.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(result) }
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    point: MapPoint,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_marker)) },
        text = { 
            Column {
                Text(stringResource(R.string.delete_marker_message))
                if (point.name.isNotEmpty()) {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
