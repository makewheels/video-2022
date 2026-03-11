package com.github.makewheels.video2022.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val CLIENT_ID_KEY = stringPreferencesKey("clientId")
        private val SESSION_ID_KEY = stringPreferencesKey("sessionId")
        private val USER_PHONE_KEY = stringPreferencesKey("userPhone")
    }

    fun getTokenSync(): String? = runBlocking {
        context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun setToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    fun getClientIdSync(): String? = runBlocking {
        context.dataStore.data.map { it[CLIENT_ID_KEY] }.first()
    }

    suspend fun setClientId(clientId: String) {
        context.dataStore.edit { it[CLIENT_ID_KEY] = clientId }
    }

    fun getSessionIdSync(): String? = runBlocking {
        context.dataStore.data.map { it[SESSION_ID_KEY] }.first()
    }

    suspend fun setSessionId(sessionId: String) {
        context.dataStore.edit { it[SESSION_ID_KEY] = sessionId }
    }

    suspend fun setUserPhone(phone: String) {
        context.dataStore.edit { it[USER_PHONE_KEY] = phone }
    }

    suspend fun getUserPhone(): String? {
        return context.dataStore.data.map { it[USER_PHONE_KEY] }.first()
    }

    fun isLoggedIn(): Boolean = getTokenSync() != null
}
