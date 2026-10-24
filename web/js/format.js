/*
 * Datas e formatação. Datas de calendário são strings ISO "yyyy-MM-dd"
 * (equivalente ao LocalDate do app Android); meses são "yyyy-MM".
 */

// ------------------------------------------------------------------ datas

const DAY_MS = 86_400_000;

export const epochDay = (iso) => Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10)) / DAY_MS;
export const fromEpochDay = (n) => new Date(n * DAY_MS).toISOString().slice(0, 10);
export const addDays = (iso, n) => fromEpochDay(epochDay(iso) + n);

/** Hoje no fuso do navegador. */
export function localToday() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Data e hora "de parede" num fuso com deslocamento fixo (segundos). */
export function nowInOffset(offsetSeconds, millis = Date.now()) {
  const d = new Date(millis + offsetSeconds * 1000);
  return {
    date: d.toISOString().slice(0, 10),
    minute: d.getUTCHours() * 60 + d.getUTCMinutes() + d.getUTCSeconds() / 60,
  };
}

/** Deslocamento do fuso do navegador, em segundos (ex.: -10800 em Brasília). */
export const browserOffsetSeconds = () => -new Date().getTimezoneOffset() * 60;

export const dayOfWeek = (iso) => new Date(epochDay(iso) * DAY_MS).getUTCDay(); // 0 = domingo

/** Semana de domingo a sábado, como nos calendários brasileiros. */
export const weekStart = (iso) => addDays(iso, -dayOfWeek(iso));

export const yearMonth = (iso) => iso.slice(0, 7);

export function plusMonths(iso, n) {
  let y = +iso.slice(0, 4);
  let m = +iso.slice(5, 7) - 1 + n;
  y += Math.floor(m / 12);
  m = ((m % 12) + 12) % 12;
  const day = Math.min(+iso.slice(8, 10), daysInMonth(y, m + 1));
  return `${y}-${pad(m + 1)}-${pad(day)}`;
}

export const daysInMonth = (year, month) => new Date(Date.UTC(year, month, 0)).getUTCDate();

export const dayOfYear = (iso) => epochDay(iso) - epochDay(`${iso.slice(0, 4)}-01-01`) + 1;

/** Meses "yyyy-MM" de `from` até `to` (inclusive). */
export function monthsBetween(fromIso, toIso) {
  const out = [];
  for (let d = `${yearMonth(fromIso)}-01`; yearMonth(d) <= yearMonth(toIso); d = plusMonths(d, 1)) out.push(yearMonth(d));
  return out;
}

export function daysBetween(fromIso, toIso) {
  const out = [];
  for (let n = epochDay(fromIso), end = epochDay(toIso); n <= end; n++) out.push(fromEpochDay(n));
  return out;
}

const TS = /^(\d{4}-\d{2}-\d{2}) (\d{2}):(\d{2}):(\d{2})$/;

/** "yyyy-MM-dd HH:mm:ss" → { date, minute, hour, min } ou null. */
export function parseTs(ts) {
  const m = TS.exec(ts ?? '');
  if (!m) return null;
  return { date: m[1], hour: +m[2], min: +m[3], minute: +m[2] * 60 + +m[3], seconds: +m[2] * 3600 + +m[3] * 60 + +m[4] };
}

export const pad = (n) => String(n).padStart(2, '0');

/** Minutos desde 00:00 → "HH:mm". */
export const hm = (minutes) => `${pad(Math.floor(minutes / 60))}:${pad(Math.floor(minutes % 60))}`;

// ----------------------------------------------------------------- números

const decimals = new Map();
function decimal(digits) {
  if (!decimals.has(digits)) {
    decimals.set(digits, new Intl.NumberFormat('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits }));
  }
  return decimals.get(digits);
}
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export const fmt = {
  kw: (v) => decimal(2).format(v),

  /** Energia com casas decimais que diminuem conforme o número cresce. */
  kwh(v) {
    if (v >= 10_000) return `${decimal(1).format(v / 1000)} MWh`;
    if (v >= 100) return `${decimal(0).format(v)} kWh`;
    return `${decimal(1).format(v)} kWh`;
  },

  money: (v) => currency.format(v),
  number: (v, digits = 1) => decimal(digits).format(v),
  percent: (fraction) => `${decimal(0).format(fraction * 100)}%`,
  weight: (kg) => (kg >= 1000 ? `${decimal(1).format(kg / 1000)} t` : `${decimal(0).format(kg)} kg`),

  /** "2026-10-14 13:09:53" → "13:09" */
  readingTime: (ts) => (ts && ts.length >= 16 ? ts.slice(11, 16) : null),

  duration(minutes) {
    if (minutes < 1) return 'menos de 1 min';
    if (minutes < 60) return `${minutes} min`;
    if (minutes < 1440) return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
    return `${Math.floor(minutes / 1440)} d ${Math.floor(minutes / 60) % 24} h`;
  },

  days(minutes) {
    const d = Math.floor(minutes / 1440);
    return d >= 1 ? `${d} ${d === 1 ? 'dia' : 'dias'}` : `${Math.floor(minutes / 60)} h`;
  },

  /** "2026-09-12" → "12 de set." */
  dayMonth: (iso) => `${+iso.slice(8, 10)} de ${MONTHS_SHORT[+iso.slice(5, 7) - 1]}.`,

  /** "2026-09" ou "2026-09-12" → "Setembro de 2026" */
  monthYear: (iso) => `${MONTHS[+iso.slice(5, 7) - 1]} de ${iso.slice(0, 4)}`,

  fullDate: (iso) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`,

  /** "dom 12" para rótulos de barra da semana. */
  weekdayDay: (iso) => `${WEEKDAYS[dayOfWeek(iso)]} ${+iso.slice(8, 10)}`,

  /** 9 → "set" */
  monthShort: (month) => MONTHS_SHORT[month - 1],
};

const MONTHS = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
const MONTHS_SHORT = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const WEEKDAYS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

/** Aceita "0,93", "0.93" e "R$ 0,93". */
export function parseDecimal(text) {
  const clean = text.replace('R$', '').replaceAll('.', text.includes(',') ? '' : '.').replace(',', '.').trim();
  if (clean === '') return null;
  const n = Number(clean);
  return Number.isFinite(n) ? n : null;
}

/** Número que pode vir como string ("29.0001"), vazio ou ausente. */
export function num(value) {
  if (value == null) return null;
  const s = String(value).trim();
  if (s === '') return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}
