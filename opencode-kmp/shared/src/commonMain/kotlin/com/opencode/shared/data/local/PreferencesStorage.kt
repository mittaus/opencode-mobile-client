package com.opencode.shared.data.local

import kotlinx.coroutines.flow.Flow

interface PreferencesStorage {
    fun observeString(key: String): Flow<String?>
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun getInt(key: String): Int?
    suspend fun putInt(key: String, value: Int)
    suspend fun getBool(key: String): Boolean?
    suspend fun putBool(key: String, value: Boolean)
    suspend fun clear()
}
