package org.medialiteracy.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Creates a platform-specific DataStore instance.
 */
expect fun createDataStore(): DataStore<Preferences>
