package com.opencode.shared.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSUserDefaults

class IosPreferencesStorage : PreferencesStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun observeString(key: String): Flow<String?> = flow { emit(getString(key)) }
    override suspend fun getString(key: String) = defaults.stringForKey(key)
    override suspend fun putString(key: String, value: String) = defaults.setObject(value, key)
    override suspend fun getInt(key: String): Int? = defaults.integerForKey(key).toInt()
        .takeIf { it != 0 }
    override suspend fun putInt(key: String, value: Int) =
        defaults.setInteger(value.toLong(), key)
    override suspend fun getBool(key: String): Boolean? = defaults.boolForKey(key)
    override suspend fun putBool(key: String, value: Boolean) = defaults.setBool(value, key)
    override suspend fun clear() = defaults.removePersistentDomainForName(
        platform.Foundation.NSBundle.mainBundle.bundleIdentifier ?: "opencode"
    )
}
