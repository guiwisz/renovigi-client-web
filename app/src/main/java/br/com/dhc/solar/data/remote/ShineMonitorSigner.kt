package br.com.dhc.solar.data.remote

import java.security.MessageDigest

/**
 * Monta e assina as query strings da API ShineMonitor.
 *
 * - Login:      sign = SHA1(salt + SHA1(senha) + baseAction)
 * - Autenticada: sign = SHA1(salt + secret + token + baseAction)
 *
 * `baseAction` é exatamente o trecho `&action=...` enviado na URL, por isso a
 * string assinada e a enviada precisam ser idênticas, inclusive na ordem.
 */
object ShineMonitorSigner {

    fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val v = byte.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    /** O app oficial troca espaços por `+` e deixa o resto do usuário intacto. */
    fun encodeUsername(username: String): String = username.replace(" ", "+")

    /** `suffix` é o contexto do app; vazio para sessões abertas pelo login do portal web. */
    fun baseAction(action: String, params: List<Pair<String, Any>>, suffix: String): String =
        buildString {
            append("&action=").append(action)
            for ((key, value) in params) append('&').append(key).append('=').append(value)
            append(suffix)
        }

    fun baseAction(action: String, params: List<Pair<String, Any>>, profile: AppProfile): String =
        baseAction(action, params, profile.suffixContext)

    /**
     * Formas de login aceitas pelo backend. O `authSource` é o dos apps
     * Android (WatchPower/RenoClient); `auth` e `authEmail` são os do portal
     * www.shinemonitor.com (`js/loginIndex.js`), sem o sufixo de app, e o
     * `authEmail` aceita usuário ou e-mail.
     */
    enum class LoginMethod { APP_SOURCE, WEB, EMAIL }

    fun loginQuery(
        profile: AppProfile,
        username: String,
        passwordSha1: String,
        salt: String,
        method: LoginMethod = LoginMethod.APP_SOURCE,
    ): String {
        val baseAction = when (method) {
            LoginMethod.APP_SOURCE -> "&action=authSource&usr=${encodeUsername(username)}" +
                "&company-key=${profile.companyKey}${profile.suffixContext}"
            LoginMethod.WEB -> "&action=auth&usr=${encodeURIComponent(username)}&company-key=${profile.companyKey}"
            LoginMethod.EMAIL -> "&action=authEmail&usr=${encodeURIComponent(username)}&company-key=${profile.companyKey}"
        }
        val sign = sha1Hex(salt + passwordSha1 + baseAction)
        return "?sign=$sign&salt=$salt$baseAction"
    }

    /** Equivalente ao `encodeURIComponent` do JavaScript, mais o `'` → `%27` que o portal aplica. */
    fun encodeURIComponent(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.!~*()") {
                append(ch)
            } else {
                append('%').append(HEX[c ushr 4].uppercaseChar()).append(HEX[c and 0x0F].uppercaseChar())
            }
        }
    }

    fun authedQuery(token: String, secret: String, baseAction: String, salt: String): String {
        val sign = sha1Hex(salt + secret + token + baseAction)
        return "?sign=$sign&salt=$salt&token=$token$baseAction"
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
