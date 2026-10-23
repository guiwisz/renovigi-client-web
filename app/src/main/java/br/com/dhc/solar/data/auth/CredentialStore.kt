package br.com.dhc.solar.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.dhc.solar.data.remote.Session
import br.com.dhc.solar.data.remote.ShineMonitorSigner.LoginMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Guarda usuário, SHA-1 da senha (nunca a senha em si) e o token atual,
 * cifrados com [KeystoreCipher]. O SHA-1 basta para refazer o login
 * quando o token expira, já que é ele que entra na assinatura.
 */
class CredentialStore(context: Context, private val cipher: KeystoreCipher = KeystoreCipher()) {

    @Serializable
    data class Stored(
        val username: String,
        val passwordSha1: String,
        val plantId: Long? = null,
        val token: String? = null,
        val secret: String? = null,
        val expiresAtMillis: Long = 0,
        val loginMethod: LoginMethod = LoginMethod.APP_SOURCE,
    ) {
        val session: Session?
            get() = if (token != null && secret != null) Session(token, secret, expiresAtMillis, loginMethod) else null
    }

    private val dataStore = context.applicationContext.credentialsDataStore

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { it[KEY] != null }

    suspend fun read(): Stored? = dataStore.data.first()[KEY]?.let { encoded ->
        runCatching { Json.decodeFromString(Stored.serializer(), cipher.decrypt(encoded)) }.getOrNull()
    }

    suspend fun write(stored: Stored) {
        val encoded = cipher.encrypt(Json.encodeToString(Stored.serializer(), stored))
        dataStore.edit { it[KEY] = encoded }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("credentials")
    }
}

private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")
