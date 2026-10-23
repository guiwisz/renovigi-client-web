package br.com.dhc.solar.data.remote

/** A API respondeu com `err != 0`. Códigos em `spec/error_codes.yaml` do shinemonitor-api. */
open class ShineMonitorException(val code: Int, val description: String) :
    Exception("ShineMonitor err=0x%04X (%s)".format(code, description)) {

    /** Sem dados para o período pedido (ex.: curva de hoje antes do nascer do sol). */
    val isNoRecord: Boolean get() = code == ERR_NO_RECORD

    companion object {
        const val ERR_NO_RECORD = 0x000C
        const val ERR_NOT_FOUND_USR = 0x0105

        /** Erros que significam "faça login de novo". */
        val AUTH_ERRORS = setOf(
            0x0004, // assinatura inválida
            0x0007, // parâmetros faltando (comum quando a assinatura não bate)
            0x000A, // autenticação falhou
            0x000F, // chave de fabricante não encontrada
            0x0010, // senha incorreta
            0x0019, // conta congelada
            0x0105, // usuário não encontrado
            0x010E, // token expirado/inválido
        )

        fun from(code: Int, description: String): ShineMonitorException =
            if (code in AUTH_ERRORS) ShineMonitorAuthException(code, description)
            else ShineMonitorException(code, description)
    }
}

class ShineMonitorAuthException(code: Int, description: String) : ShineMonitorException(code, description)
