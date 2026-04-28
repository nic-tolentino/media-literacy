package org.medialiteracy.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

private lateinit var dataStoreInstance: DataStore<Preferences>

fun initDataStore(producePath: () -> String) {
    dataStoreInstance = PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )
}

actual fun createDataStore(): DataStore<Preferences> {
    if (!::dataStoreInstance.isInitialized) {
        throw IllegalStateException("DataStore not initialized. Call initDataStore first.")
    }
    return dataStoreInstance
}
