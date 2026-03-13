package com.github.makewheels.video2022.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

    @Volatile var cachedToken: String? = null
        private set
    @Volatile var cachedClientId: String? = null
        private set
    @Volatile var cachedSessionId: String? = null
        private set
    @Volatile var cachedUserPhone: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val prefs = context.dataStore.data.first()
            cachedToken = prefs[TOKEN_KEY]
            cachedClientId = prefs[CLIENT_ID_KEY]
            cachedSessionId = prefs[SESSION_ID_KEY]
            cachedUserPhone = prefs[USER_PHONE_KEY]
        }

        context.dataStore.data.onEach { prefs ->
            cachedToken = prefs[TOKEN_KEY]
            cachedClientId = prefs[CLIENT_ID_KEY]
            cachedSessionId = prefs[SESSION_ID_KEY]
            cachedUserPhone = prefs[USER_PHONE_KEY]
        }.launchIn(scope)
    }

    fun getTokenSync(): String? = cachedToken

    suspend fun setToken(token: String) {
        cachedToken = token
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        cachedToken = null
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    fun getClientIdSync(): String? = cachedClientId

    suspend fun setClientId(clientId: String) {
        cachedClientId = clientId
        context.dataStore.edit { it[CLIENT_ID_KEY] = clientId }
    }

    fun getSessionIdSync(): String? = cachedSessionId

    suspend fun setSessionId(sessionId: String) {
        cachedSessionId = sessionId
        context.dataStore.edit { it[SESSION_ID_KEY] = sessionId }
    }

    suspend fun setUserPhone(phone: String) {
        cachedUserPhone = phone
        context.dataStore.edit { it[USER_PHONE_KEY] = phone }
    }

    suspend fun getUserPhone(): String? = cachedUserPhone

    fun isLoggedIn(): Boolean = cachedToken != null
}
