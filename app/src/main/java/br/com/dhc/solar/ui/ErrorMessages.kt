package br.com.dhc.solar.ui

import android.util.Log
import br.com.dhc.solar.data.remote.ShineMonitorAuthException
import br.com.dhc.solar.data.remote.ShineMonitorException
import java.io.IOException

fun Throwable.toUserMessage(): String {
    Log.w("Solar", "Falha exibida ao usuário", this)
    return when (this) {
        is ShineMonitorAuthException -> "Usuário ou senha incorretos"
        is ShineMonitorException -> "O servidor recusou a solicitação ($description)"
        is IOException -> "Sem conexão com o servidor de monitoramento"
        else -> "Algo deu errado. Tente novamente."
    }
}
