package com.cussou.autotiq.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance for the "settings" file.
 *
 * DataStore throws if several instances are active for the same file in one process, so every
 * component (repository, BroadcastReceivers) must go through this accessor instead of declaring
 * its own `preferencesDataStore` delegate.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
