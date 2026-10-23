package br.com.dhc.solar.ui.format

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

    private fun decimal(digits: Int) = NumberFormat.getNumberInstance(ptBR).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }

    private val currency = NumberFormat.getCurrencyInstance(ptBR)
    private val time = DateTimeFormatter.ofPattern("HH:mm", ptBR)

    fun kw(value: Double): String = decimal(2).format(value)

    /** Energia com casas decimais que diminuem conforme o número cresce. */
    fun kwh(value: Double): String = when {
        value >= 10_000 -> "${decimal(1).format(value / 1000)} MWh"
        value >= 100 -> "${decimal(0).format(value)} kWh"
        else -> "${decimal(1).format(value)} kWh"
    }

    fun money(value: Double): String = currency.format(value)

    fun clock(epochMillis: Long): String =
        time.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** "2026-10-14 13:09:53" → "13:09" */
    fun readingTime(timestamp: String?): String? = timestamp?.takeIf { it.length >= 16 }?.substring(11, 16)

    fun number(value: Double, digits: Int = 1): String = decimal(digits).format(value)

    fun percent(fraction: Double): String = "${decimal(0).format(fraction * 100)}%"

    fun weight(kg: Double): String = if (kg >= 1000) "${decimal(1).format(kg / 1000)} t" else "${decimal(0).format(kg)} kg"

    fun duration(d: java.time.Duration): String {
        val minutes = d.toMinutes()
        return when {
            minutes < 1 -> "menos de 1 min"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60} h ${minutes % 60} min"
            else -> "${minutes / (24 * 60)} d ${(minutes / 60) % 24} h"
        }
    }

    fun days(minutes: Long): String {
        val d = minutes / (24 * 60)
        return if (d >= 1) "$d ${if (d == 1L) "dia" else "dias"}" else "${minutes / 60} h"
    }

    private val dayMonth = DateTimeFormatter.ofPattern("d 'de' MMM", ptBR)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)
    private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR)

    fun dayMonth(date: java.time.temporal.TemporalAccessor): String = dayMonth.format(date)
    fun monthYear(date: java.time.temporal.TemporalAccessor): String = monthYear.format(date).replaceFirstChar { it.uppercase() }
    fun fullDate(date: java.time.temporal.TemporalAccessor): String = fullDate.format(date)

    /** "dom 12" para rótulos de barra da semana. */
    fun weekdayDay(date: java.time.LocalDate): String =
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, ptBR).trimEnd('.').lowercase() + " " + date.dayOfMonth

    /** "09" → "set" para rótulos de barra por mês. */
    fun monthShort(month: Int): String =
        java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, ptBR).trimEnd('.')
}
