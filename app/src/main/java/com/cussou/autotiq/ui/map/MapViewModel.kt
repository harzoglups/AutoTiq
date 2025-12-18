package com.cussou.autotiq.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cussou.autotiq.domain.model.AppSettings
import com.cussou.autotiq.domain.model.MapLayerType
import com.cussou.autotiq.domain.model.MapPoint
import com.cussou.autotiq.domain.usecase.AddMapPointUseCase
import com.cussou.autotiq.domain.usecase.DeleteMapPointUseCase
import com.cussou.autotiq.domain.usecase.GetMapPointsUseCase
import com.cussou.autotiq.domain.usecase.GetSettingsUseCase
import com.cussou.autotiq.domain.usecase.UpdateMapPointUseCase
import com.cussou.autotiq.domain.usecase.UpdateSettingsUseCase
import com.cussou.autotiq.domain.util.Result
import com.cussou.autotiq.worker.LocationWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getMapPointsUseCase: GetMapPointsUseCase,
    private val addMapPointUseCase: AddMapPointUseCase,
    private val deleteMapPointUseCase: DeleteMapPointUseCase,
    private val updateMapPointUseCase: UpdateMapPointUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val locationWorkScheduler: LocationWorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                getMapPointsUseCase(),
                getSettingsUseCase()
            ) { points, settings ->
                MapUiState.Success(points, settings)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addPoint(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            when (val result = addMapPointUseCase(latitude, longitude)) {
                is Result.Success -> {
                    // Point added successfully, return the new point ID
                    _lastAddedPointId.value = result.data
                }
                is Result.Error -> {
                    // Handle error if needed
                }
                Result.Loading -> {}
            }
        }
    }
    
    fun addPointWithDetails(
        latitude: Double,
        longitude: Double,
        name: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        viewModelScope.launch {
            when (val result = addMapPointUseCase(latitude, longitude)) {
                is Result.Success -> {
                    // Point added, now update it with the details
                    val pointId = result.data
                    val currentState = _uiState.value
                    if (currentState is MapUiState.Success) {
                        val newPoint = currentState.points.find { it.id == pointId }
                        newPoint?.let { point ->
                            updateMapPointUseCase(
                                point.copy(
                                    name = name,
                                    startHour = startHour,
                                    startMinute = startMinute,
                                    endHour = endHour,
                                    endMinute = endMinute
                                )
                            )
                        }
                    }
                }
                is Result.Error -> {
                    // Handle error if needed
                }
                Result.Loading -> {}
            }
        }
    }
    
    private val _lastAddedPointId = MutableStateFlow<Long?>(null)
    val lastAddedPointId: StateFlow<Long?> = _lastAddedPointId.asStateFlow()
    
    fun clearLastAddedPointId() {
        _lastAddedPointId.value = null
    }
    
    // Store map position to restore when returning from other screens
    private val _savedMapPosition = MutableStateFlow<SavedMapPosition?>(null)
    val savedMapPosition: StateFlow<SavedMapPosition?> = _savedMapPosition.asStateFlow()
    
    fun saveMapPosition(latitude: Double, longitude: Double, zoom: Double) {
        _savedMapPosition.value = SavedMapPosition(latitude, longitude, zoom)
    }

    fun deletePoint(id: Long) {
        viewModelScope.launch {
            deleteMapPointUseCase(id)
        }
    }
    
    fun updatePoint(point: MapPoint) {
        viewModelScope.launch {
            when (updateMapPointUseCase(point)) {
                is Result.Success -> {
                    // Point updated successfully, flow will update automatically
                }
                is Result.Error -> {
                    // Handle error if needed
                }
                Result.Loading -> {}
            }
        }
    }

    fun toggleLocationTracking(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingsUseCase.updateLocationTrackingEnabled(enabled)
            
            if (enabled) {
                // Get current settings to know the interval
                val currentState = _uiState.value
                if (currentState is MapUiState.Success) {
                    locationWorkScheduler.scheduleLocationChecks(
                        currentState.settings.checkIntervalSeconds
                    )
                }
            } else {
                locationWorkScheduler.cancelLocationChecks()
            }
        }
    }

    fun updateMapLayer(layerType: MapLayerType) {
        viewModelScope.launch {
            updateSettingsUseCase.updateMapLayerType(layerType)
        }
    }
    
    private var searchJob: Job? = null
    
    fun searchLocation(
        query: String,
        viewbox: Viewbox? = null,
        onResults: (List<SearchResult>) -> Unit
    ) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce for 300ms (reduced from 500ms)
            try {
                val results = withContext(Dispatchers.IO) {
                    performNominatimSearch(query, viewbox)
                }
                onResults(results)
            } catch (e: Exception) {
                // Handle error silently or log it
                onResults(emptyList())
            }
        }
    }
    
    private fun performNominatimSearch(query: String, viewbox: Viewbox?): List<SearchResult> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            // Use Photon API (better partial search than Nominatim)
            var url = "https://photon.komoot.io/api/?q=$encodedQuery&limit=10&lang=fr"
            
            // Add location bias if viewbox available
            if (viewbox != null) {
                val centerLat = (viewbox.minLat + viewbox.maxLat) / 2
                val centerLon = (viewbox.minLon + viewbox.maxLon) / 2
                url += "&lat=$centerLat&lon=$centerLon"
            }
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "AutoTiq/1.0")
            connection.requestMethod = "GET"
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            // Parse GeoJSON response
            val json = org.json.JSONObject(response)
            val features = json.getJSONArray("features")
            val results = mutableListOf<SearchResult>()
            
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                
                // Build display name from properties
                val name = properties.optString("name", "")
                val city = properties.optString("city", "")
                val state = properties.optString("state", "")
                val country = properties.optString("country", "")
                
                val displayParts = mutableListOf<String>()
                if (name.isNotEmpty()) displayParts.add(name)
                if (city.isNotEmpty() && city != name) displayParts.add(city)
                if (state.isNotEmpty()) displayParts.add(state)
                if (country.isNotEmpty()) displayParts.add(country)
                
                val displayName = displayParts.joinToString(", ")
                
                // Get type and classification
                val osmValue = properties.optString("osm_value", "")
                val osmKey = properties.optString("osm_key", "")
                val type = properties.optString("type", "")
                
                results.add(
                    SearchResult(
                        displayName = displayName,
                        lat = coordinates.getDouble(1), // GeoJSON: [lon, lat]
                        lon = coordinates.getDouble(0),
                        type = type,
                        placeClass = osmKey,
                        addressType = osmValue
                    )
                )
            }
            
            // Sort by priority: cities/villages first, then other places
            val sortedResults = results.sortedBy { result ->
                when {
                    result.addressType in listOf("city", "town", "village", "municipality") -> 0
                    result.placeClass == "place" -> 0
                    result.addressType in listOf("administrative", "hamlet", "suburb") -> 1
                    result.placeClass in listOf("highway", "railway") -> 2
                    else -> 3
                }
            }
            
            return sortedResults.take(5)
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    private fun fetchNominatimResults(url: String): List<SearchResult> {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "AutoTiq/1.0")
            connection.requestMethod = "GET"
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val jsonArray = JSONArray(response)
            val results = mutableListOf<SearchResult>()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                results.add(
                    SearchResult(
                        displayName = item.getString("display_name"),
                        lat = item.getDouble("lat"),
                        lon = item.getDouble("lon"),
                        type = item.optString("type", ""),
                        placeClass = item.optString("class", ""),
                        addressType = item.optString("addresstype", "")
                    )
                )
            }
            
            results
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class SearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val type: String = "",
    val placeClass: String = "",
    val addressType: String = ""
)

data class Viewbox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class SavedMapPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(
        val points: List<MapPoint>,
        val settings: AppSettings
    ) : MapUiState
}
