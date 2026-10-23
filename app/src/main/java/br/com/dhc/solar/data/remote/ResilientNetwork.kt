package br.com.dhc.solar.data.remote

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS que tolera falhas intermitentes (comuns com DNS privado como o AdGuard):
 * tenta de novo e, se ainda falhar, usa os últimos endereços que funcionaram.
 */
class ResilientDns(private val delegate: Dns = Dns.SYSTEM, private val attempts: Int = 3) : Dns {
    private val lastKnown = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        var failure: UnknownHostException? = null
        repeat(attempts) { attempt ->
            try {
                return delegate.lookup(hostname).also { lastKnown[hostname] = it }
            } catch (e: UnknownHostException) {
                failure = e
                if (attempt < attempts - 1) Thread.sleep(300L * (attempt + 1))
            }
        }
        return lastKnown[hostname] ?: throw failure!!
    }
}

/** Repete GETs em falhas de rede passageiras. Escritas (editPlant) não passam por aqui. */
class RetryInterceptor(private val retries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var failure: IOException? = null
        for (attempt in 0..retries) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (request.header(NO_RETRY) != null || chain.call().isCanceled()) throw e
                failure = e
                if (attempt < retries) Thread.sleep(500L * (attempt + 1))
            }
        }
        throw failure!!
    }

    companion object {
        /** Header marcador para requisições que não podem ser repetidas. */
        const val NO_RETRY = "X-Solar-No-Retry"
    }
}
