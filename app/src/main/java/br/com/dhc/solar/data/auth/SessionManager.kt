package br.com.dhc.solar.data.auth

import br.com.dhc.solar.data.remote.ShineMonitorApi
import br.com.dhc.solar.data.remote.ShineMonitorAuthException
import br.com.dhc.solar.data.remote.ShineMonitorSigner
import br.com.dhc.solar.data.remote.Session
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotLoggedInException : IllegalStateException("Nenhuma conta configurada")

/**
 * Entrega um token válido para quem chama a API, refazendo o login com as
 * credenciais guardadas quando o token expira ou é rejeitado.
 */
class SessionManager(
    private val api: ShineMonitorApi,
    private val store: CredentialStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** Faz login com usuário e senha digitados e escolhe a primeira usina da conta. */
    suspend fun signIn(username: String, password: String) {
        // O portal oficial também apara espaços da senha antes do SHA-1.
        val passwordSha1 = ShineMonitorSigner.sha1Hex(password.trim())
        val session = api.login(username.trim(), passwordSha1)
        val plantId = api.plants(session).firstOrNull()?.pid
        store.write(
            CredentialStore.Stored(
                username = username.trim(),
                passwordSha1 = passwordSha1,
                plantId = plantId,
                token = session.token,
                secret = session.secret,
                expiresAtMillis = session.expiresAtMillis,
                loginMethod = session.method,
            ),
        )
    }

    suspend fun signOut() = store.clear()

    suspend fun plantId(): Long? = store.read()?.plantId

    /** Executa [block] com um token válido; se for rejeitado, faz login de novo e tenta mais uma vez. */
    suspend fun <T> withSession(block: suspend (Session) -> T): T {
        val session = currentSession(forceRefresh = false)
        return try {
            block(session)
        } catch (e: ShineMonitorAuthException) {
            block(currentSession(forceRefresh = true))
        }
    }

    private suspend fun currentSession(forceRefresh: Boolean): Session = mutex.withLock {
        val stored = store.read() ?: throw NotLoggedInException()
        val cached = stored.session
        if (!forceRefresh && cached != null && cached.expiresAtMillis - clock() > EXPIRY_MARGIN_MS) {
            return cached
        }
        val fresh = api.login(stored.username, stored.passwordSha1)
        store.write(
            stored.copy(token = fresh.token, secret = fresh.secret, expiresAtMillis = fresh.expiresAtMillis, loginMethod = fresh.method),
        )
        fresh
    }

    private companion object {
        const val EXPIRY_MARGIN_MS = 10 * 60 * 1000L
    }
}
