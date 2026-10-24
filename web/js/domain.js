import { num, parseTs } from './format.js';

// ------------------------------------------------------------------ status

/** Códigos PLANT_STATUS_* da ShineMonitor. */
export function statusFromCode(code) {
  return { 0: 'ONLINE', 1: 'OFFLINE', 4: 'WARNING', 7: 'ATTENTION' }[code] ?? 'UNKNOWN';
}

// ----------------------------------------------------------------- alarmes

/**
 * Tradução das descrições de alarme dos inversores (formato abreviado que a
 * API devolve, ex.: "NO-Grid", "UN-G-V").
 */
const ALARMS = {
  'NO-GRID': ['Sem rede elétrica',
    'A energia da concessionária caiu e o inversor se desligou por segurança. Ele volta sozinho quando a rede retorna. ' +
      'No fim da tarde também é comum: o inversor registra isso ao desligar.', 'GRID'],
  'UN-G-V': ['Tensão da rede baixa',
    'A tensão da concessionária ficou abaixo do limite permitido e o inversor parou de injetar até ela normalizar.', 'GRID'],
  'OV-G-V': ['Tensão da rede alta',
    'A tensão da rede passou do limite. Comum quando muitos sistemas injetam ao mesmo tempo no mesmo transformador; ' +
      'se acontecer sempre, vale acionar a concessionária.', 'GRID'],
  'UN-G-F': ['Frequência da rede baixa', 'A frequência da rede ficou abaixo do permitido.', 'GRID'],
  'OV-G-F': ['Frequência da rede alta', 'A frequência da rede ficou acima do permitido.', 'GRID'],
  'ISO-FLT': ['Falha de isolamento',
    'Possível fuga de corrente nos cabos ou nos painéis (umidade, cabo danificado). Se repetir, chame o instalador.', 'PANELS'],
  'GFCI-FLT': ['Corrente de fuga', 'O inversor detectou corrente de fuga para a terra.', 'PANELS'],
  'OV-DC': ['Tensão dos painéis alta', 'A tensão das strings passou do limite do inversor.', 'PANELS'],
  'OV-TEM': ['Inversor superaquecido',
    'O inversor reduziu a potência por temperatura alta. Verifique ventilação e sol direto no equipamento.', 'INVERTER'],
  'RELAY-FLT': ['Falha no relé', 'Falha interna no relé de conexão com a rede.', 'INVERTER'],
};

/** Título, explicação e tipo (GRID, PANELS, INVERTER, OTHER) de um alarme. */
export function alarmInfo(description) {
  const known = ALARMS[description.trim().toUpperCase().replaceAll(' ', '-')];
  if (known) return { title: known[0], explanation: known[1], kind: known[2] };
  return { title: description.trim() || 'Alarme', explanation: 'Código informado pelo inversor.', kind: 'OTHER' };
}

/** Duração do alarme em minutos, quando já encerrado. */
export function alarmMinutes(alarm) {
  const start = parseTs(alarm.startedAt);
  const end = parseTs(alarm.endedAt);
  if (!start || !end) return null;
  return Math.round((Date.parse(`${end.date}T00:00:00Z`) - Date.parse(`${start.date}T00:00:00Z`)) / 60000 + (end.seconds - start.seconds) / 60);
}

// ---------------------------------------------------------------- inversor

/** Monta a leitura ao vivo a partir dos pares título/valor (títulos em inglês, como vêm da API). */
export function inverterFromParams(params) {
  const byTitle = Object.fromEntries(params.map((p) => [p.title.trim().toLowerCase(), p.val?.trim() ?? null]));
  const n = (title) => num(byTitle[title]);
  const text = (title) => byTitle[title] || null;

  const strings = [];
  for (let i = 1; i <= 8; i++) {
    const v = n(`dc voltage ${i}`);
    if (v != null) strings.push({ index: i, voltage: v, current: n(`dc current ${i}`) ?? 0 });
  }
  // Inversor monofásico: só uma das três fases tem valor; pegamos a maior.
  const maxOf = (titles) => {
    const values = titles.map(n).filter((v) => v != null);
    return values.length ? Math.max(...values) : null;
  };
  return {
    readingAt: text('timestamp'),
    acPowerW: n('active power'),
    dcPowerW: n('dc output power'),
    strings,
    gridVoltage: maxOf(['ab voltage/a phase voltage', 'bc voltage/b phase voltage', 'ca voltage/c phase voltage']),
    gridCurrent: maxOf(['a phase current', 'b phase current', 'c phase current']),
    gridFrequency: n('grid frequency'),
    apparentPowerKva: n('apparent power value'),
    temperatureC: n('inverter temperature'),
    busVoltage: n('dc bus voltage'),
    firmwareHmi: text('hmi version'),
    firmwareDsp: text('dsp version'),
    gridStandard: text('national standard number'),
    todayKwh: n('today energy'),
    totalKwh: n('total energy'),
  };
}

export const stringPowerW = (s) => s.voltage * s.current;

/** Entradas sem painéis ligados ficam com tensão residual (poucos volts). */
export const stringInUse = (s) => s.voltage >= 50;

/** Eficiência da conversão DC → AC, quando há potência suficiente para ser significativa. */
export function efficiency(live) {
  if (live.acPowerW == null || live.dcPowerW == null || live.dcPowerW <= 100) return null;
  return Math.min(1, Math.max(0, live.acPowerW / live.dcPowerW));
}

// ------------------------------------------------------------------- sol

/**
 * Nascer/pôr do sol pela aproximação da NOAA (erro de ~1 min), em minutos
 * desde 00:00 no fuso da usina. Sem coordenadas, assume 06:00–18:00.
 */
export function sunTimes(dayOfYear, lat, lon, utcOffsetSeconds) {
  if (lat == null || lon == null) return { sunrise: 360, solarNoon: 720, sunset: 1080 };
  const g = (2 * Math.PI / 365) * (dayOfYear - 1);
  const eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) -
    0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
  const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) +
    0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
  const latRad = (lat * Math.PI) / 180;
  const cosHa = Math.min(1, Math.max(-1,
    Math.cos((90.833 * Math.PI) / 180) / (Math.cos(latRad) * Math.cos(decl)) - Math.tan(latRad) * Math.tan(decl)));
  const haDeg = (Math.acos(cosHa) * 180) / Math.PI;
  const noon = 720 - 4 * lon - eqTime + utcOffsetSeconds / 60;
  return { sunrise: noon - 4 * haDeg, solarNoon: noon, sunset: noon + 4 * haDeg };
}

/**
 * Progresso da animação do ciclo do dia (0..1): 0.25 = nascer, 0.5 =
 * meio-dia solar, 0.75 = pôr do sol, com a noite dividida igualmente.
 */
export function dayProgress(m, sun) {
  let p;
  if (m < sun.sunrise) p = 0.25 * m / sun.sunrise;
  else if (m < sun.solarNoon) p = 0.25 + 0.25 * (m - sun.sunrise) / (sun.solarNoon - sun.sunrise);
  else if (m < sun.sunset) p = 0.5 + 0.25 * (m - sun.solarNoon) / (sun.sunset - sun.solarNoon);
  else p = 0.75 + 0.25 * (m - sun.sunset) / (1440 - sun.sunset);
  return Math.min(1, Math.max(0, p));
}

/** Potência esperada com céu limpo (kW), para estimar nebulosidade. */
export function clearSkyPowerKw(m, sun, nominalKw) {
  if (m <= sun.sunrise || m >= sun.sunset) return 0;
  const x = (m - sun.sunrise) / (sun.sunset - sun.sunrise);
  return nominalKw * 0.82 * Math.pow(Math.sin(Math.PI * x), 1.3);
}

// ---------------------------------------------------------------- snapshot

export const plantTitle = (s) => (s?.plantName && s.plantName.trim() && s.plantName !== 'PLANT' ? s.plantName : 'Minha usina');

/** Dia típico: média dos últimos 28 dias; sem ela, a dos dias já fechados do mês. */
export function typicalDayKwh(s) {
  if (s.averageDayKwh != null) return s.averageDayKwh;
  const closed = s.monthDays.slice(0, -1).map((p) => p.kwh).filter((v) => v > 0);
  return closed.length ? closed.reduce((a, b) => a + b, 0) / closed.length : null;
}

export const sum = (values) => values.reduce((a, b) => a + b, 0);
export const average = (values) => (values.length ? sum(values) / values.length : null);
