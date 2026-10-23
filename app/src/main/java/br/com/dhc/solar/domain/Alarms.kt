package br.com.dhc.solar.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class Alarm(
    val id: String,
    val code: String,
    val rawDescription: String,
    val startedAt: String,
    val endedAt: String? = null,
    val active: Boolean = false,
) {
    val info: AlarmInfo get() = AlarmCatalog.lookup(rawDescription)

    fun start(): LocalDateTime? = parse(startedAt)
    fun end(): LocalDateTime? = endedAt?.let(::parse)

    val duration: Duration?
        get() {
            val start = start() ?: return null
            return Duration.between(start, end() ?: return null)
        }

    private fun parse(value: String) = runCatching { LocalDateTime.parse(value, TS) }.getOrNull()

    private companion object {
        val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

enum class AlarmKind { GRID, PANELS, INVERTER, OTHER }

/** Título e explicação em português para um alarme. */
data class AlarmInfo(val title: String, val explanation: String, val kind: AlarmKind)

/**
 * Tradução das descrições de alarme dos inversores (formato abreviado que a
 * API devolve, ex.: "NO-Grid", "UN-G-V").
 */
object AlarmCatalog {
    private val known = mapOf(
        "NO-GRID" to AlarmInfo(
            "Sem rede elétrica",
            "A energia da concessionária caiu e o inversor se desligou por segurança. Ele volta sozinho quando a rede retorna. " +
                "No fim da tarde também é comum: o inversor registra isso ao desligar.",
            AlarmKind.GRID,
        ),
        "UN-G-V" to AlarmInfo(
            "Tensão da rede baixa",
            "A tensão da concessionária ficou abaixo do limite permitido e o inversor parou de injetar até ela normalizar.",
            AlarmKind.GRID,
        ),
        "OV-G-V" to AlarmInfo(
            "Tensão da rede alta",
            "A tensão da rede passou do limite. Comum quando muitos sistemas injetam ao mesmo tempo no mesmo transformador; " +
                "se acontecer sempre, vale acionar a concessionária.",
            AlarmKind.GRID,
        ),
        "UN-G-F" to AlarmInfo("Frequência da rede baixa", "A frequência da rede ficou abaixo do permitido.", AlarmKind.GRID),
        "OV-G-F" to AlarmInfo("Frequência da rede alta", "A frequência da rede ficou acima do permitido.", AlarmKind.GRID),
        "ISO-FLT" to AlarmInfo(
            "Falha de isolamento",
            "Possível fuga de corrente nos cabos ou nos painéis (umidade, cabo danificado). Se repetir, chame o instalador.",
            AlarmKind.PANELS,
        ),
        "GFCI-FLT" to AlarmInfo("Corrente de fuga", "O inversor detectou corrente de fuga para a terra.", AlarmKind.PANELS),
        "OV-DC" to AlarmInfo("Tensão dos painéis alta", "A tensão das strings passou do limite do inversor.", AlarmKind.PANELS),
        "OV-TEM" to AlarmInfo(
            "Inversor superaquecido",
            "O inversor reduziu a potência por temperatura alta. Verifique ventilação e sol direto no equipamento.",
            AlarmKind.INVERTER,
        ),
        "RELAY-FLT" to AlarmInfo("Falha no relé", "Falha interna no relé de conexão com a rede.", AlarmKind.INVERTER),
    )

    fun lookup(description: String): AlarmInfo {
        val key = description.trim().uppercase().replace(' ', '-')
        return known[key] ?: AlarmInfo(description.ifBlank { "Alarme" }, "Código informado pelo inversor.", AlarmKind.OTHER)
    }
}
