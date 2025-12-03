package com.fairlaunch.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fairlaunch.domain.model.MapPoint

@Composable
fun EditMarkerDialog(
    point: MapPoint,
    onDismiss: () -> Unit,
    onSave: (MapPoint) -> Unit
) {
    var name by remember { mutableStateOf(point.name) }
    var startHour by remember { mutableStateOf(point.startHour.toString()) }
    var endHour by remember { mutableStateOf(point.endHour.toString()) }
    var showError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Marker") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Active Hours",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { 
                            if (it.length <= 2) startHour = it.filter { char -> char.isDigit() }
                            showError = false
                        },
                        label = { Text("Start") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("h") },
                        isError = showError
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("-")
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { 
                            if (it.length <= 2) endHour = it.filter { char -> char.isDigit() }
                            showError = false
                        },
                        label = { Text("End") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("h") },
                        isError = showError
                    )
                }
                
                if (showError) {
                    Text(
                        "Hours must be between 0-23",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Text(
                    "Fairtiq will only launch when entering the zone between these hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startHour.toIntOrNull() ?: -1
                    val end = endHour.toIntOrNull() ?: -1
                    
                    if (start in 0..23 && end in 0..23) {
                        onSave(
                            point.copy(
                                name = name.trim().ifEmpty { "Point #${point.id}" },
                                startHour = start,
                                endHour = end
                            )
                        )
                    } else {
                        showError = true
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
