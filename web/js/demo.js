/*
 * Usina fictícia para o modo demonstração.
 *
 * Os dados são determinísticos por dia (o mesmo dia gera sempre os mesmos
 * números) e coerentes entre si: a curva segue o sol, os totais são a soma
 * dos dias e a leitura do inversor bate com a potência atual.
 */
import { sunTimes, clearSkyPowerKw, sum, average } from './domain.js';
import {
  addDays, browserOffsetSeconds, dayOfYear, daysBetween, daysInMonth, epochDay, nowInOffset, pad, plusMonths, weekStart, yearMonth,
} from './format.js';

export const PLANT_NAME = 'Usina demonstração';
export const DEFAULT_TARIFF = 0.95;
const NOMINAL_KW = 6.6;
const LAT = -15.79;
const INSTALLED = '2023-03-14';

/** Fuso do navegador, com o sol posicionado para meio-dia local (coerente em qualquer lugar). */
const OFFSET = browserOffsetSeconds();
const LON = (OFFSET / 3600) * 15;

const now = () => nowInOffset(OFFSET);

/** Gerador pseudoaleatório com semente (mulberry32). */
function random(seed) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    between: (from, to) => from + (to - from) * next(),
    int: (from, until) => from + Math.floor(next() * (until - from)),
  };
}

/** Energia de um dia inteiro: sazonal (mais no verão) com variação de nuvens. */
export function dayKwh(date) {
  if (date < INSTALLED) return 0;
  const seasonal = 30 + 4.5 * Math.cos((2 * Math.PI * (dayOfYear(date) - 15)) / 365);
  const r = random(epochDay(date));
  // O dia de hoje é sempre bonito (a demo serve para apresentar o app); dias passados variam.
  const isToday = date === now().date;
  const weather = !isToday && r.next() < 0.18 ? r.between(0.35, 0.7) : r.between(0.9, 1.04);
  return seasonal * weather;
}

/** Curva do dia (amostras de 5 min) até `untilMinute`, com quedas de nuvem determinísticas. */
export function curve(date, untilMinute = 1440) {
  const sun = sunTimes(dayOfYear(date), LAT, LON, OFFSET);
  const clear = [];
  for (let m = Math.floor(Math.trunc(sun.sunrise) / 5) * 5; m <= Math.trunc(sun.sunset); m += 5) {
    clear.push([m, clearSkyPowerKw(m, sun, NOMINAL_KW)]);
  }
  const clearKwh = (sum(clear.map((c) => c[1])) * 5) / 60;
  const scale = clearKwh > 0 ? Math.min(dayKwh(date) / clearKwh, 1.05) : 0;
  const r = random(epochDay(date) * 31);
  // Poucas passagens de nuvem, cada uma com profundidade própria e entrada/saída suaves.
  const clouds = Array.from({ length: r.int(1, 4) }, () =>
    [sun.solarNoon + r.between(-200, 200), r.between(8, 18), r.between(0.2, 0.45)]);
  return clear.filter(([m]) => m <= untilMinute).map(([m, kw]) => {
    const dip = Math.min(0.7, sum(clouds.map(([center, halfWidth, depth]) => {
      const d = (m - center) / halfWidth;
      return Math.abs(d) >= 1 ? 0 : (depth * (1 + Math.cos(Math.PI * d))) / 2;
    })));
    return { minuteOfDay: m, kw: Math.max(0, kw * scale * (1 - dip)) };
  });
}

function monthTotal(month) {
  const today = now().date;
  const [y, m] = month.split('-').map(Number);
  let total = 0;
  for (let d = 1; d <= daysInMonth(y, m); d++) {
    const date = `${month}-${pad(d)}`;
    if (date <= today) total += dayKwh(date);
  }
  return total;
}

export function snapshot(tariff, name) {
  const { date: today, minute } = now();
  const reading = Math.floor(minute / 5) * 5;
  const todayCurve = curve(today, reading);
  const todayKwh = (sum(todayCurve.map((p) => p.kw)) * 5) / 60;
  const last = todayCurve.at(-1);
  const currentKw = last && last.minuteOfDay >= reading - 5 ? last.kw : 0;
  const dayOfMonth = +today.slice(8, 10);
  const monthKwh = sum(daysBetween(`${yearMonth(today)}-01`, addDays(today, -1)).map(dayKwh)) + todayKwh;
  const yearKwh = sum(daysBetween(`${today.slice(0, 4)}-01-01`, addDays(today, -1)).map(dayKwh)) + todayKwh;
  const totalKwh = sum(daysBetween(INSTALLED, addDays(today, -1)).map(dayKwh)) + todayKwh;
  const start = weekStart(today);
  const last28 = Array.from({ length: 28 }, (_, i) => dayKwh(addDays(today, -(i + 1))));
  const weeks = [1, 2, 3, 4].map((w) => sum([0, 1, 2, 3, 4, 5, 6].map((i) => dayKwh(addDays(start, -7 * w + i)))));
  const months = Array.from({ length: 12 }, (_, i) => monthTotal(yearMonth(plusMonths(`${yearMonth(today)}-01`, -(i + 1)))));
  const readingAt = `${today} ${pad(Math.floor(reading / 60))}:${pad(reading % 60)}:12`;

  return {
    plantId: 0,
    plantName: name ?? PLANT_NAME,
    status: 'ONLINE',
    nominalPowerKw: NOMINAL_KW,
    currentPowerKw: currentKw,
    todayKwh,
    monthKwh,
    yearKwh,
    totalKwh,
    todayCurve,
    co2KgPerKwh: 0.997,
    updatedAtMillis: Date.now(),
    tariffPerKwh: tariff,
    proceedsTotal: totalKwh * tariff,
    co2TotalKg: totalKwh * 0.997,
    coalTotalKg: totalKwh * 0.4,
    installDate: `${INSTALLED} 10:00:00`,
    latitude: LAT,
    longitude: LON,
    utcOffsetSeconds: OFFSET,
    lastReadingAt: readingAt,
    inverter: inverter(currentKw, readingAt, todayKwh, totalKwh),
    collector: { firmware: '3.94.2.6', intervalSeconds: 300, online: true },
    runningMinutes: 9 * 24 * 60 + 410,
    recentAlarms: alarms(today).slice(0, 3),
    monthDays: Array.from({ length: dayOfMonth }, (_, i) => {
      const d = i + 1;
      return { label: pad(d), kwh: d === dayOfMonth ? todayKwh : dayKwh(`${yearMonth(today)}-${pad(d)}`) };
    }),
    averageDayKwh: average(last28),
    weekKwh: sum(daysBetween(start, addDays(today, -1)).map(dayKwh)) + todayKwh,
    typicalWeekKwh: average(weeks),
    typicalMonthKwh: average(months.filter((m) => m > 0)),
    isDemo: true,
  };
}

function inverter(acKw, readingAt, todayKwh, totalKwh) {
  const fraction = acKw / NOMINAL_KW;
  const dcW = acKw > 0.01 ? (acKw * 1000) / 0.976 : 0;
  const v1 = dcW > 0 ? 292 + 14 * fraction : 4.1;
  const v2 = dcW > 0 ? 305 + 12 * fraction : 3.8;
  return {
    readingAt,
    acPowerW: acKw * 1000,
    dcPowerW: dcW,
    strings: [
      { index: 1, voltage: v1, current: dcW > 0 ? (dcW * 0.48) / v1 : 0 },
      { index: 2, voltage: v2, current: dcW > 0 ? (dcW * 0.52) / v2 : 0 },
      { index: 3, voltage: 1.2, current: 0 },
      { index: 4, voltage: 0.9, current: 0 },
    ],
    gridVoltage: 221.4 + 4 * fraction,
    gridCurrent: (acKw * 1000) / 222,
    gridFrequency: 60.01,
    apparentPowerKva: acKw * 1.002,
    temperatureC: 31 + 34 * fraction,
    busVoltage: 352,
    firmwareHmi: '009F',
    firmwareDsp: '0026',
    gridStandard: 'BRAZIL-H',
    todayKwh,
    totalKwh,
  };
}

export function alarms(today) {
  return [[3, 'NO-Grid', 22], [11, 'UN-G-V', 14], [26, 'NO-Grid', 6], [47, 'OV-G-V', 9]].map(([daysAgo, code, minutes]) => {
    const day = addDays(today, -daysAgo);
    const end = 16 * 60 + 40 + minutes;
    return {
      id: `demo-${daysAgo}`,
      code: code === 'NO-Grid' ? '0x00001015' : '0x00001011',
      rawDescription: code,
      startedAt: `${day} 16:40:00`,
      endedAt: `${day} ${pad(Math.floor(end / 60))}:${pad(end % 60)}:00`,
      active: false,
    };
  });
}

export function perDay(month) {
  const today = now().date;
  const [y, m] = month.split('-').map(Number);
  return Array.from({ length: daysInMonth(y, m) }, (_, i) => {
    const date = `${month}-${pad(i + 1)}`;
    return { label: pad(i + 1), kwh: date > today ? 0 : dayKwh(date) };
  });
}

export const perMonth = (year) =>
  Array.from({ length: 12 }, (_, i) => ({ label: pad(i + 1), kwh: monthTotal(`${year}-${pad(i + 1)}`) }));

export function perYear() {
  const out = [];
  for (let y = +INSTALLED.slice(0, 4); y <= +now().date.slice(0, 4); y++) {
    out.push({ label: String(y), kwh: sum(perMonth(y).map((p) => p.kwh)) });
  }
  return out;
}

export const byDay = (from, to) => Object.fromEntries(daysBetween(from, to).map((d) => [d, dayKwh(d)]));

export const chartFields = [
  { id: 'output_power', label: 'Potência ativa', unit: 'kW' },
  { id: 'inverter_temperature', label: 'Temperatura do inversor', unit: '°C' },
  { id: 'pv_voltage1', label: 'Tensão FV 1', unit: 'V' },
  { id: 'pv_voltage2', label: 'Tensão FV 2', unit: 'V' },
  { id: 'phase_voltage_t', label: 'Tensão da rede (C)', unit: 'V' },
  { id: 'grid_frequency', label: 'Frequência da rede', unit: 'Hz' },
];

/** Série de um parâmetro, derivada da curva do dia. */
export function series(fieldId, date) {
  const n = now();
  const until = date === n.date ? n.minute : 1440;
  return curve(date, until).filter((s) => s.kw > 0.005).map((s) => {
    const f = s.kw / NOMINAL_KW;
    const m = s.minuteOfDay;
    const value = {
      inverter_temperature: 31 + 34 * f,
      pv_voltage1: 292 + 14 * f + 6 * Math.sin(m / 40),
      pv_voltage2: 305 + 12 * f + 5 * Math.sin(m / 37),
      phase_voltage_t: 221.4 + 4 * f + 1.5 * Math.sin(m / 23),
      grid_frequency: 60 + 0.02 * Math.sin(m / 11),
    }[fieldId] ?? s.kw;
    return { minuteOfDay: m, value };
  });
}
