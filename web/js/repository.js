/*
 * Repositório: sessão com renovação automática, montagem do snapshot da usina
 * (tudo em paralelo), histórico, alertas e gravações. No modo demonstração,
 * tudo vem de demo.js, sem rede.
 */
import * as api from './api.js';
import * as demo from './demo.js';
import { store } from './store.js';
import { statusFromCode, inverterFromParams, average, sum } from './domain.js';
import { addDays, daysBetween, monthsBetween, nowInOffset, num, parseTs, weekStart, yearMonth } from './format.js';

const BRASILIA_OFFSET = -3 * 3600;
const EXPIRY_MARGIN_MS = 10 * 60 * 1000;

const FIELD_LABELS = {
  output_power: 'Potência ativa',
  total_output_power: 'Potência DC',
  inverter_temperature: 'Temperatura do inversor',
  pv_voltage1: 'Tensão FV 1', pv_voltage2: 'Tensão FV 2', pv_voltage3: 'Tensão FV 3', pv_voltage4: 'Tensão FV 4',
  pv_current1: 'Corrente FV 1', pv_current2: 'Corrente FV 2', pv_current3: 'Corrente FV 3', pv_current4: 'Corrente FV 4',
  phase_voltage_r: 'Tensão da rede (A)', phase_voltage_s: 'Tensão da rede (B)', phase_voltage_t: 'Tensão da rede (C)',
  phase_current_r: 'Corrente da rede (A)', phase_current_s: 'Corrente da rede (B)', phase_current_t: 'Corrente da rede (C)',
  grid_frequency: 'Frequência da rede',
  bus_voltage: 'Tensão do barramento DC', bus_half_voltage: 'Meia tensão do barramento',
  apparent_power_value: 'Potência aparente',
  energy_today: 'Energia do dia', energy_total: 'Energia total',
  current_year_generating_capacity: 'Energia do ano',
};

// ------------------------------------------------------------------ sessão

let sessionLock = Promise.resolve();

/** Aviso para a tela de login (ex.: sessão expirada sem "manter conectado"). */
let loginNotice = null;
export function takeLoginNotice() {
  const notice = loginNotice;
  loginNotice = null;
  return notice;
}

/**
 * Entrega um token válido. Com "manter conectado", refaz o login com o SHA-1
 * guardado quando o token expira; sem isso, volta para a tela de login.
 */
function currentSession(forceRefresh) {
  const run = sessionLock.then(async () => {
    const stored = store.credentials;
    if (!stored) throw new api.NotLoggedInError();
    if (!forceRefresh && stored.token && stored.expiresAtMillis - Date.now() > EXPIRY_MARGIN_MS) {
      return { token: stored.token, secret: stored.secret, expiresAtMillis: stored.expiresAtMillis, method: stored.loginMethod };
    }
    if (!stored.passwordSha1) {
      loginNotice = 'Sua sessão expirou. Entre novamente.';
      signOut();
      throw new api.NotLoggedInError();
    }
    const fresh = await api.login(stored.username, stored.passwordSha1);
    store.credentials = { ...stored, token: fresh.token, secret: fresh.secret, expiresAtMillis: fresh.expiresAtMillis, loginMethod: fresh.method };
    return fresh;
  });
  sessionLock = run.catch(() => {});
  return run;
}

/** Executa `block` com um token válido; se for rejeitado, faz login de novo e tenta mais uma vez. */
async function withSession(block) {
  const session = await currentSession(false);
  try {
    return await block(session);
  } catch (e) {
    if (e instanceof api.ShineError && e.isAuth) return block(await currentSession(true));
    throw e;
  }
}

async function withPlant(block) {
  return withSession((session) => {
    const plantId = store.credentials?.plantId;
    if (plantId == null) throw new api.NotLoggedInError();
    return block(session, plantId);
  });
}

/**
 * Faz login com usuário e senha digitados e escolhe a primeira usina da conta.
 * `remember`: guarda o SHA-1 da senha para renovar a sessão sozinho. Para esta
 * API o SHA-1 vale tanto quanto a senha, então só com o consentimento da pessoa.
 */
export async function signIn(username, password, remember = false) {
  // O portal oficial também apara espaços da senha antes do SHA-1.
  const passwordSha1 = api.sha1Hex(password.trim());
  const session = await api.login(username.trim(), passwordSha1);
  const plants = await api.plants(session);
  store.credentials = {
    username: username.trim(),
    passwordSha1: remember ? passwordSha1 : null,
    plantId: plants[0]?.pid ?? null,
    token: session.token,
    secret: session.secret,
    expiresAtMillis: session.expiresAtMillis,
    loginMethod: session.method,
  };
}

/** Apaga o SHA-1 guardado: a sessão atual continua até o token expirar. */
export function forgetPassword() {
  const stored = store.credentials;
  if (stored) store.credentials = { ...stored, passwordSha1: null };
}

export function signOut() {
  store.setDemo(false);
  store.credentials = null;
  store.snapshot = null;
  device = null;
  fields = null;
}

/** Entra no modo demonstração (dados fictícios, sem conta). */
export function startDemo() {
  store.setDemo(true);
  return refresh();
}

// ---------------------------------------------------------------- snapshot

let device = null;
let devicePromise = null;
let fields = null;

/** O inversor da usina (descoberto uma vez por sessão do app). */
function getDevice(session) {
  if (device) return Promise.resolve(device);
  devicePromise ??= optional(async () => (await api.devices(session))[0] ?? null)
    .then((d) => { device = d; return d; })
    .finally(() => { devicePromise = null; });
  return devicePromise;
}

/** Erros de dados opcionais não derrubam o painel inteiro. */
async function optional(block) {
  try {
    return await block();
  } catch (e) {
    if (e instanceof api.ShineError && !e.isAuth) return null;
    throw e;
  }
}

async function orEmpty(block) {
  try {
    return await block();
  } catch (e) {
    if (e instanceof api.ShineError && e.isNoRecord) return [];
    throw e;
  }
}

/** Busca tudo em paralelo, grava no cache e devolve o snapshot novo. */
export async function refresh() {
  const snapshot = store.demo
    ? demo.snapshot(store.demoTariff ?? demo.DEFAULT_TARIFF, store.demoName)
    : await refreshFromServer();
  store.snapshot = snapshot;
  return snapshot;
}

function refreshFromServer() {
  return withPlant(async (session, plantId) => {
    const plant = await api.plantInfo(session, plantId);
    const offset = plant.address?.timezone ?? BRASILIA_OFFSET;
    const today = nowInOffset(offset).date;
    const dev = await getDevice(session);
    const start = weekStart(today);

    const [values, curve, live, coll, running, alarms, month, days, months] = await Promise.all([
      api.plantCurrentData(session, plantId),
      orEmpty(() => api.powerOneDay(session, plantId, today)),
      dev ? optional(async () => inverterFromParams(await api.deviceLastData(session, dev))) : null,
      dev ? optional(() => api.collector(session, dev.pn)) : null,
      optional(() => api.runningMinutes(session, plantId)),
      dev ? optional(async () => (await api.deviceWarnings(session, dev, addDays(today, -30), today, 0, 10)).map(toAlarm)) : null,
      optional(() => api.energyPerDay(session, plantId, yearMonth(today))),
      optional(() => energyByDayFrom(session, plantId, addDays(start, -28), today)),
      optional(async () => {
        const year = +today.slice(0, 4);
        const lists = await Promise.all([year - 1, year].map((y) => api.energyPerMonth(session, plantId, y)));
        return lists.flat().map((p) => [parseTs(p.ts)?.date.slice(0, 7), num(p.val) ?? 0]).filter(([ym]) => ym);
      }),
    ]);

    const lastReading = live?.readingAt ?? null;
    const todayKwh = num(values.ENERGY_TODAY) ?? 0;
    const dayMap = days ?? {};
    const closedDays = Object.entries(dayMap)
      .filter(([d, kwh]) => d < today && d >= addDays(today, -28) && kwh > 0).map(([, kwh]) => kwh);
    const fullWeeks = [1, 2, 3, 4]
      .map((w) => sum([0, 1, 2, 3, 4, 5, 6].map((i) => dayMap[addDays(start, -7 * w + i)] ?? 0)))
      .filter((v) => v > 0);
    const closedMonths = (months ?? [])
      .filter(([ym, kwh]) => ym < yearMonth(today) && kwh > 0)
      .sort((a, b) => a[0].localeCompare(b[0])).slice(-12).map(([, kwh]) => kwh);

    return {
      plantId,
      plantName: plant.name ?? '',
      status: statusFromCode(plant.status ?? 0),
      nominalPowerKw: num(plant.nominalPower),
      currentPowerKw: num(values.CURRENT_POWER) ?? 0,
      todayKwh,
      monthKwh: num(values.ENERGY_MONTH) ?? 0,
      yearKwh: num(values.ENERGY_YEAR) ?? 0,
      totalKwh: num(values.ENERGY_TOTAL) ?? 0,
      todayCurve: trimFuture(curve.map(toSample).filter(Boolean), lastReading),
      co2KgPerKwh: num(plant.profit?.co2),
      updatedAtMillis: Date.now(),
      tariffPerKwh: num(plant.profit?.unitProfit),
      proceedsTotal: money(values.ENERGY_PROCEEDS),
      co2TotalKg: num(values.ENERGY_CO2),
      coalTotalKg: num(values.ENERGY_COAL),
      installDate: plant.install ?? null,
      latitude: num(plant.address?.lat),
      longitude: num(plant.address?.lon),
      utcOffsetSeconds: offset,
      lastReadingAt: lastReading,
      inverter: live,
      collector: coll ? { firmware: coll.fireware ?? null, intervalSeconds: coll.datFetch ?? null, online: (coll.status ?? 0) === 0 } : null,
      runningMinutes: running,
      recentAlarms: alarms ?? [],
      monthDays: (month ?? []).filter((p) => p.ts.slice(0, 10) <= today).map((p) => toPoint(p, 'day')),
      averageDayKwh: average(closedDays),
      // O total de hoje vem do servidor em tempo real; o "por dia" pode estar atrasado.
      weekKwh: days == null ? null : sum(daysBetween(start, addDays(today, -1)).map((d) => dayMap[d] ?? 0)) + todayKwh,
      typicalWeekKwh: average(fullWeeks),
      typicalMonthKwh: average(closedMonths),
      isDemo: false,
    };
  });
}

// --------------------------------------------------------------- histórico

/** Energia por dia de um mês ("yyyy-MM"), rótulos "dd". */
export const dailyHistory = (month) => (store.demo ? Promise.resolve(demo.perDay(month)) : withPlant(async (s, plantId) =>
  (await api.energyPerDay(s, plantId, month)).map((p) => toPoint(p, 'day'))));

/** Energia por dia num intervalo qualquer (busca cada mês envolvido uma vez). */
export const energyByDay = (from, to) => (store.demo ? Promise.resolve(demo.byDay(from, to)) : withPlant((s, plantId) =>
  energyByDayFrom(s, plantId, from, to)));

async function energyByDayFrom(session, plantId, from, to) {
  const lists = await Promise.all(monthsBetween(from, to).map((m) => api.energyPerDay(session, plantId, m)));
  const out = {};
  for (const point of lists.flat()) {
    const day = parseTs(point.ts)?.date;
    if (day && day >= from && day <= to) out[day] = num(point.val) ?? 0;
  }
  return out;
}

export const monthlyHistory = (year) => (store.demo ? Promise.resolve(demo.perMonth(year)) : withPlant(async (s, plantId) =>
  (await api.energyPerMonth(s, plantId, year)).map((p) => toPoint(p, 'month'))));

export const yearlyHistory = () => (store.demo ? Promise.resolve(demo.perYear()) : withPlant(async (s, plantId) =>
  (await api.energyPerYear(s, plantId)).map((p) => toPoint(p, 'year'))));

/** Parâmetros do inversor com histórico, com nomes em português. */
export async function chartFields() {
  if (store.demo) return demo.chartFields;
  if (fields) return fields;
  return withPlant(async (s, plantId) => {
    const dev = await getDevice(s);
    if (!dev) return [];
    fields = (await api.chartFields(s, plantId, dev))
      .map((f) => ({ id: f.optional, label: FIELD_LABELS[f.optional] ?? f.name ?? '', unit: f.uint ?? '' }));
    return fields;
  });
}

/** Série de um parâmetro num dia, sem os zeros de "sem leitura" (noite/futuro). */
export async function parameterSeries(fieldId, day) {
  if (store.demo) return demo.series(fieldId, day);
  return withSession(async (s) => {
    const dev = await getDevice(s);
    if (!dev) return [];
    const series = await api.chartData(s, dev, [fieldId], day);
    return cleanSeries(series[0]?.paramter ?? []);
  });
}

/** O mesmo parâmetro em vários dias (para sobrepor no gráfico). */
export async function compareDays(fieldId, days) {
  if (store.demo) return Object.fromEntries(days.map((d) => [d, demo.series(fieldId, d)]));
  return withSession(async (s) => {
    const dev = await getDevice(s);
    if (!dev) return {};
    const list = await api.compareDays(s, dev, fieldId, days);
    return Object.fromEntries(list.map((c) => [c.date, cleanSeries(c.dat ?? [])]));
  });
}

export async function alarms(from, to, page = 0) {
  if (store.demo) return demo.alarms(to);
  return withSession(async (s) => {
    const dev = await getDevice(s);
    if (!dev) return [];
    try {
      return (await api.deviceWarnings(s, dev, from, to, page, 50)).map(toAlarm);
    } catch (e) {
      if (e instanceof api.ShineError && e.isNoRecord) return [];
      throw e;
    }
  });
}

// ---------------------------------------------------------------- escrita

/** Grava a tarifa (R$/kWh) na usina, no servidor, e atualiza o snapshot. */
export async function setTariff(value) {
  if (store.demo) store.demoTariff = value;
  else await withPlant((s, plantId) => api.editPlant(s, plantId, [['profit.unitProfit', value.toFixed(4)]]));
  await refresh();
}

export async function setPlantName(name) {
  if (store.demo) store.demoName = name.trim();
  else await withPlant((s, plantId) => api.editPlant(s, plantId, [['name', name.trim()]]));
  await refresh();
}

// ---------------------------------------------------------------- internos

function toAlarm(w) {
  return {
    id: w.id,
    code: w.code ?? '',
    rawDescription: w.desc ?? '',
    startedAt: w.gts ?? '',
    endedAt: w.cts?.trim() ? w.cts : null,
    active: w.status === true,
  };
}

function toSample(point) {
  const t = parseTs(point.ts);
  return t ? { minuteOfDay: t.minute, kw: num(point.val) ?? 0 } : null;
}

function toPoint(point, kind) {
  const t = parseTs(point.ts);
  const label = !t ? point.ts : kind === 'day' ? t.date.slice(8, 10) : kind === 'month' ? t.date.slice(5, 7) : t.date.slice(0, 4);
  return { label, kwh: num(point.val) ?? 0 };
}

/** A curva do dia vem com os horários futuros zerados: cortamos na última leitura. */
function trimFuture(curve, lastReading) {
  let limit = parseTs(lastReading)?.minute;
  if (limit == null) {
    const lastPositive = curve.findLastIndex((s) => s.kw > 0);
    if (lastPositive < 0) return curve;
    limit = curve[lastPositive].minuteOfDay;
  }
  return curve.filter((s) => s.minuteOfDay <= limit);
}

function cleanSeries(points) {
  const parsed = points.map((kv) => {
    const t = parseTs(kv.key);
    const value = num(kv.val);
    return t && value != null ? { minuteOfDay: t.minute, value } : null;
  }).filter(Boolean);
  // Zeros antes da primeira e depois da última leitura real = inversor desligado/sem dado.
  const first = parsed.findIndex((p) => p.value !== 0);
  const last = parsed.findLastIndex((p) => p.value !== 0);
  return first < 0 ? [] : parsed.slice(first, last + 1);
}

/** "R$24715.68" → 24715.68 */
function money(value) {
  if (value == null) return null;
  return num(String(value).replace(/[^0-9.-]/g, ''));
}
