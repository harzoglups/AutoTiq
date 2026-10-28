package com.cussou.autotiq.ui.eventlog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cussou.autotiq.R
import com.cussou.autotiq.domain.model.EventLogEntry
import com.cussou.autotiq.domain.model.EventType
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: EventLogViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    val shareTitle = stringResource(R.string.event_log_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_log)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = events.isNotEmpty(),
                        onClick = {
                            val text = events.joinToString("\n") { entry ->
                                "${dateFormat.format(Date(entry.timestamp))} [${entry.type.name}] ${entry.message}"
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = shareTitle)
                    }
                    IconButton(
                        enabled = events.isNotEmpty(),
                        onClick = viewModel::clear
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.event_log_clear))
                    }
                }
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.event_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.event_log_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(events, key = { it.id }) { entry ->
                    EventRow(entry = entry, time = dateFormat.format(Date(entry.timestamp)))
                }
            }
        }
    }
}

@Composable
private fun EventRow(entry: EventLogEntry, time: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$time  ·  ${entry.type.name}",
                style = MaterialTheme.typography.labelSmall,
                color = entry.type.color()
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EventType.color(): Color = when (this) {
    EventType.ERROR -> MaterialTheme.colorScheme.error
    EventType.ALERT_SENT -> MaterialTheme.colorScheme.primary
    EventType.ALERT_SKIPPED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
