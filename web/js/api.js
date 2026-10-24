/*
 * Cliente de baixo nível da API ShineMonitor (mesmo backend do RenoClient).
 * As requisições saem para `api/`, que o nginx encaminha para
 * https://android.shinemonitor.com/public/ (o navegador não pode chamar direto: CORS).
 *
 * - Login:       sign = SHA1(salt + SHA1(senha) + baseAction)
 * - Autenticada: sign = SHA1(salt + secret + token + baseAction)
 *
 * `baseAction` é exatamente o trecho `&action=...` enviado na URL, por isso a
 * string assinada e a enviada precisam ser idênticas, inclusive na ordem.
 */

const BASE_URL = 'api/';

/** Chave de fabricante compartilhada pelos apps WatchPower/RenoClient. */
const COMPANY_KEY = 'bnrl_frRFjEz8Mkn';

/** Sufixo que o RenoClient anexa a toda requisição (e que entra na assinatura). */
const APP_SUFFIX = '&i18n=pt_BR&lang=pt_BR&source=1&_app_client_=android' +
  '&_app_id_=com.eybond.renoclient&_app_version_=1.3.2.0';

const CURRENT_DATA_KEYS = 'ENERGY_TODAY,ENERGY_MONTH,ENERGY_YEAR,ENERGY_TOTAL,ENERGY_PROCEEDS,' +
  'ENERGY_CO2,ENERGY_COAL,ENERGY_SO2,CURRENT_POWER';

/**
 * Formas de login aceitas pelo backend. `APP_SOURCE` é a dos apps Android;
 * `WEB` e `EMAIL` são as do portal, sem o sufixo de app (`EMAIL` aceita e-mail).
 */
export const LoginMethod = Object.freeze({ APP_SOURCE: 'APP_SOURCE', WEB: 'WEB', EMAIL: 'EMAIL' });

// ------------------------------------------------------------------ erros

export const ERR_NO_RECORD = 0x000C;
export const ERR_NOT_FOUND_USR = 0x0105;
const ERR_NONE_OPERATION = 0x010F;

/** Erros que significam "faça login de novo". */
export const AUTH_ERRORS = new Set([0x0004, 0x0007, 0x000A, 0x000F, 0x0010, 0x0019, 0x0105, 0x010E]);

/** A API respondeu com `err != 0`. */
export class ShineError extends Error {
  constructor(code, description) {
    super(`ShineMonitor err=0x${code.toString(16).padStart(4, '0').toUpperCase()} (${description})`);
    this.code = code;
    this.description = description;
  }
  get isAuth() { return AUTH_ERRORS.has(this.code); }
  get isNoRecord() { return this.code === ERR_NO_RECORD; }
}

/** Falha de rede ou resposta inválida. */
export class NetworkError extends Error {}

export class NotLoggedInError extends Error {
  constructor() { super('Nenhuma conta configurada'); }
}

// ------------------------------------------------------------------ SHA-1

/** SHA-1 em hexa (implementação própria: `crypto.subtle` só existe em HTTPS/localhost). */
export function sha1Hex(text) {
  const bytes = new TextEncoder().encode(text);
  const length = ((bytes.length + 9 + 63) >> 6) << 6;
  const buf = new Uint8Array(length);
  buf.set(bytes);
  buf[bytes.length] = 0x80;
  const view = new DataView(buf.buffer);
  const bits = bytes.length * 8;
  view.setUint32(length - 8, Math.floor(bits / 2 ** 32));
  view.setUint32(length - 4, bits >>> 0);
  const h = [0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0];
  const w = new Uint32Array(80);
  for (let off = 0; off < length; off += 64) {
    for (let i = 0; i < 16; i++) w[i] = view.getUint32(off + i * 4);
    for (let i = 16; i < 80; i++) {
      const x = w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16];
      w[i] = (x << 1) | (x >>> 31);
    }
    let [a, b, c, d, e] = h;
    for (let i = 0; i < 80; i++) {
      let f, k;
      if (i < 20) { f = (b & c) | (~b & d); k = 0x5A827999; }
      else if (i < 40) { f = b ^ c ^ d; k = 0x6ED9EBA1; }
      else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
      else { f = b ^ c ^ d; k = 0xCA62C1D6; }
      const t = (((a << 5) | (a >>> 27)) + f + e + k + w[i]) >>> 0;
      e = d; d = c; c = ((b << 30) | (b >>> 2)) >>> 0; b = a; a = t;
    }
    h[0] = (h[0] + a) >>> 0; h[1] = (h[1] + b) >>> 0; h[2] = (h[2] + c) >>> 0;
    h[3] = (h[3] + d) >>> 0; h[4] = (h[4] + e) >>> 0;
  }
  return h.map((v) => v.toString(16).padStart(8, '0')).join('');
}

// ------------------------------------------------------------- assinatura

/** `encodeURIComponent` mais o `'` → `%27` que o portal aplica. */
export const encodeComponent = (value) => encodeURIComponent(value).replace(/'/g, '%27');

/** O app oficial troca espaços por `+` e deixa o resto do usuário intacto. */
const encodeUsername = (username) => username.replaceAll(' ', '+');

export function baseAction(action, params, suffix) {
  return `&action=${action}` + params.map(([k, v]) => `&${k}=${v}`).join('') + suffix;
}

export function loginQuery(username, passwordSha1, salt, method = LoginMethod.APP_SOURCE) {
  const action = {
    [LoginMethod.APP_SOURCE]: `&action=authSource&usr=${encodeUsername(username)}&company-key=${COMPANY_KEY}${APP_SUFFIX}`,
    [LoginMethod.WEB]: `&action=auth&usr=${encodeComponent(username)}&company-key=${COMPANY_KEY}`,
    [LoginMethod.EMAIL]: `&action=authEmail&usr=${encodeComponent(username)}&company-key=${COMPANY_KEY}`,
  }[method];
  return `?sign=${sha1Hex(salt + passwordSha1 + action)}&salt=${salt}${action}`;
}

export function authedQuery(token, secret, action, salt) {
  return `?sign=${sha1Hex(salt + secret + token + action)}&salt=${salt}&token=${token}${action}`;
}

/** Contas acessadas pelo login do portal só enxergam a usina sem o sufixo do app. */
export const suffixFor = (session) => (session.method === LoginMethod.APP_SOURCE ? APP_SUFFIX : '');

// ------------------------------------------------------------------ HTTP

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * GET com repetição em falhas de rede passageiras (inclusive 502/504 do proxy).
 * Escritas (editPlant) passam `retries = 0`.
 */
async function fetchEnvelope(query, retries = 2) {
  let failure;
  for (let attempt = 0; attempt <= retries; attempt++) {
    let response;
    try {
      response = await fetch(BASE_URL + query, { cache: 'no-store', credentials: 'omit' });
    } catch (e) {
      failure = new NetworkError(e.message);
    }
    if (response) {
      if (response.ok) {
        try { return await response.json(); } catch { throw new NetworkError('Resposta inválida'); }
      }
      failure = new NetworkError(`HTTP ${response.status}`);
      if (response.status !== 502 && response.status !== 504) throw failure;
    }
    if (attempt < retries) await sleep(500 * (attempt + 1));
  }
  throw failure;
}

async function get(query) {
  const envelope = await fetchEnvelope(query);
  if (envelope.err !== 0) throw new ShineError(envelope.err, envelope.desc ?? '');
  if (envelope.dat == null) throw new NetworkError("Resposta sem 'dat'");
  return envelope.dat;
}

const salt = () => String(Date.now());

function authed(session, action, ...params) {
  const base = baseAction(action, params, suffixFor(session));
  return get(authedQuery(session.token, session.secret, base, salt()));
}

const deviceParams = (d) => [['pn', d.pn], ['devcode', d.devcode], ['devaddr', d.devaddr], ['sn', encodeComponent(d.sn)]];

// --------------------------------------------------------------- endpoints

/** Última forma de login que funcionou; tentada primeiro nas próximas vezes. */
let preferredLogin = null;

/**
 * Tenta as formas de login em ordem. Só passa para a próxima quando o
 * servidor diz "usuário não encontrado"; senha errada encerra na hora.
 */
export async function login(username, passwordSha1) {
  const methods = [preferredLogin, ...Object.values(LoginMethod).filter((m) => m !== preferredLogin)].filter(Boolean);
  let notFound = null;
  for (const method of methods) {
    let dat;
    try {
      dat = await get(loginQuery(username, passwordSha1, salt(), method));
    } catch (e) {
      if (!(e instanceof ShineError) || e.code !== ERR_NOT_FOUND_USR) throw e;
      notFound = e;
      continue;
    }
    preferredLogin = method;
    return { token: dat.token, secret: dat.secret, expiresAtMillis: Date.now() + dat.expire * 1000, method };
  }
  throw notFound ?? new Error('Nenhuma forma de login disponível');
}

export const plants = async (s) => (await authed(s, 'queryPlants', ['page', 0], ['pagesize', 50])).plant ?? [];

export const plantInfo = (s, plantId) => authed(s, 'queryPlantInfo', ['plantid', plantId]);

/** Curva do dia, uma amostra a cada 5 minutos (kW). */
export const powerOneDay = async (s, plantId, day) =>
  (await authed(s, 'queryPlantActiveOuputPowerOneDay', ['plantid', plantId], ['date', day])).outputPower ?? [];

/** Energia por dia de um mês ("yyyy-MM"). */
export const energyPerDay = async (s, plantId, month) =>
  (await authed(s, 'queryPlantEnergyMonthPerDay', ['plantid', plantId], ['date', month])).perday ?? [];

export const energyPerMonth = async (s, plantId, year) =>
  (await authed(s, 'queryPlantEnergyYearPerMonth', ['plantid', plantId], ['date', year])).permonth ?? [];

export const energyPerYear = async (s, plantId) =>
  (await authed(s, 'queryPlantEnergyTotalPerYear', ['plantid', plantId])).peryear ?? [];

/**
 * Totais da usina num único pedido: ENERGY_TODAY/MONTH/YEAR/TOTAL (kWh),
 * CURRENT_POWER (kW), ENERGY_PROCEEDS ("R$24715.68"), ENERGY_CO2/COAL (kg).
 */
export async function plantCurrentData(s, plantId) {
  const list = await authed(s, 'queryPlantCurrentData', ['plantid', plantId], ['par', CURRENT_DATA_KEYS]);
  return Object.fromEntries(list.map((kv) => [kv.key, kv.val]));
}

export const devices = async (s) => (await authed(s, 'queryDevices', ['page', 0], ['pagesize', 50])).device ?? [];

/** Os ~30 parâmetros ao vivo do inversor (títulos em inglês, valores em string). */
export const deviceLastData = (s, d) => authed(s, 'queryDeviceLastData', ...deviceParams(d));

export const deviceWarnings = async (s, d, from, to, page = 0, pageSize = 50) =>
  (await authed(s, 'queryDeviceWarning', ...deviceParams(d),
    ['sdate', `${from}%2000:00:00`], ['edate', `${to}%2023:59:59`], ['page', page], ['pagesize', pageSize])).warning ?? [];

export const collector = async (s, pn) => (await authed(s, 'queryCollectorInfo', ['pn', pn])).collector?.[0] ?? null;

/** Minutos de operação contínua sem falhas. */
export const runningMinutes = async (s, plantId) =>
  (await authed(s, 'queryPlantRunningNormalTime', ['plantid', plantId])).minutes ?? 0;

/** Parâmetros do inversor que têm histórico (id, nome em inglês, unidade). */
export const chartFields = (s, plantId, d) =>
  authed(s, 'queryPlantDeviceChartsFieldsByType', ['plantid', plantId], ['devtype', 512], ['devcode', d.devcode], ['type', 1]);

/** Série de um parâmetro num dia (uma amostra a cada 5 min, 288 por dia). */
export const chartData = async (s, d, fields, day) =>
  (await authed(s, 'queryDeviceChartFieldsDat', ...deviceParams(d), ['field', fields.join(',')], ['precision', 5],
    ['sdate', `${day}%2000:00:00`], ['edate', `${day}%2023:59:59`])).date ?? [];

/** Mesmo parâmetro em vários dias, das 03h às 20h, para comparação. */
export const compareDays = (s, d, field, days) =>
  authed(s, 'queryPlantSameDeviceParManyDay', ['stime', '03:00:00'], ['etime', '20:00:00'], ['chart_field', field],
    ['pn', d.pn], ['devcode', d.devcode], ['sn', encodeComponent(d.sn)], ['dates', days.join(',')]);

/**
 * Altera dados da usina no servidor (edição parcial: só os campos enviados
 * mudam). Escrita: não passa pela repetição automática.
 */
export async function editPlant(s, plantId, fields) {
  const params = [['plantid', plantId], ...fields.map(([k, v]) => [k, encodeComponent(v)])];
  const query = authedQuery(s.token, s.secret, baseAction('editPlant', params, suffixFor(s)), salt());
  const envelope = await fetchEnvelope(query, 0);
  // 0x010F = "nada mudou" (valor igual ao atual): não é erro para nós.
  if (envelope.err !== 0 && envelope.err !== ERR_NONE_OPERATION) throw new ShineError(envelope.err, envelope.desc ?? '');
}
