package com.opencode.shared.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*

class AndroidPreferencesStorage(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStorage {

    override fun observeString(key: String): Flow<String?> =
        dataStore.data.map { it[stringPreferencesKey(key)] }

    override suspend fun getString(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun getInt(key: String): Int? =
        dataStore.data.first()[intPreferencesKey(key)]

    override suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    override suspend fun getBool(key: String): Boolean? =
        dataStore.data.first()[booleanPreferencesKey(key)]

    override suspend fun putBool(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun clear() { dataStore.edit { it.clear() } }
}
