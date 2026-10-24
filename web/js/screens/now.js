import { scaffold, refreshButton } from '../app.js';
import { alarmInfo, alarmMinutes, clearSkyPowerKw, dayProgress, plantTitle, sunTimes, typicalDayKwh } from '../domain.js';
import { addDays, dayOfYear, epochDay, fmt, hm, localToday, nowInOffset, parseTs } from '../format.js';
import { createScene } from '../scene.js';
import { nowLayout } from '../layout.js';
import { openCustomize } from './customize.js';
import { conversionCard, equipmentCard, frequencyCard, gridCard, stringsCard, temperatureCard } from './inverter.js';
import {
  h, icon, appear, card, cardLabel, metricCard, animatedNumber, blockProgress, linearProgress, lineChart, cssColor, spinner,
} from '../ui.js';

const STATUS = {
  ONLINE: ['Online', 'var(--leaf)'],
  OFFLINE: ['Offline', 'var(--gray)'],
  WARNING: ['Em alarme', 'var(--alert)'],
  ATTENTION: ['Atenção', 'var(--sun)'],
  UNKNOWN: ['—', 'var(--gray)'],
};

let scene = null;
let sceneSnapshot = null;

/** Posição do dia, potência e nebulosidade para a cena, no horário atual da usina. */
function sceneState(s) {
  const local = nowInOffset(s.utcOffsetSeconds);
  const sun = sunTimes(dayOfYear(local.date), s.latitude, s.longitude, s.utcOffsetSeconds);
  const nominal = s.nominalPowerKw > 0 ? s.nominalPowerKw : 1;
  const expected = clearSkyPowerKw(local.minute, sun, nominal);
  const cloudiness = expected > nominal * 0.15 ? Math.min(1, Math.max(0, 1 - s.currentPowerKw / expected)) : 0;
  return { sun, dayProgress: dayProgress(local.minute, sun), powerFraction: s.currentPowerKw / nominal, cloudiness };
}

// Recalcula a posição do dia a cada minuto.
setInterval(() => {
  if (scene?.el.isConnected && sceneSnapshot) scene.update(sceneState(sceneSnapshot));
}, 60_000);

export function renderNow(ctx, { animate }) {
  const s = ctx.snapshot;
  const title = plantTitle(s);
  const settingsBtn = h('button', { class: 'icon-btn pressable', title: 'Ajustes', 'aria-label': 'Ajustes', onclick: ctx.openSettings }, icon('settings'));
  const customizeBtn = h('button', { class: 'icon-btn pressable', title: 'Personalizar', 'aria-label': 'Personalizar', onclick: openCustomize }, icon('tune'));

  if (!s) {
    return scaffold({
      title, animate, actions: [refreshButton(), settingsBtn],
      body: h('div', { class: 'empty' },
        h('div', { class: 'accent', style: { fontSize: '48px' } }, icon('sun')),
        ctx.error ? h('div', { class: 'body-l muted' }, ctx.error) : h('div', { class: 'body-l muted' }, 'Buscando dados da sua usina…'),
        ctx.refreshing ? spinner() : null),
    });
  }

  const tariff = ctx.tariff;
  const live = s.inverter;
  const recentAlarm = s.recentAlarms.find((a) => {
    const start = parseTs(a.startedAt);
    return start && epochDay(localToday()) - epochDay(start.date) <= 7;
  });
  // Cada card da lista personalizável (ver layout.js); null = nada a mostrar agora.
  const cards = {
    economy: () => h('div', { class: 'row2' },
      metricCard('now.savings', 'Economia hoje', 'payments', 'var(--leaf)', s.todayKwh * tariff, fmt.money, `${fmt.money(tariff)}/kWh`),
      metricCard('now.co2', 'CO₂ evitado hoje', 'eco', 'var(--leaf)', s.todayKwh * (s.co2KgPerKwh ?? 0), fmt.weight,
        s.co2KgPerKwh != null ? `${fmt.number(s.co2KgPerKwh, 2)} kg por kWh` : null)),
    curve: () => curveCard(s),
    goal: () => goalCard(s),
    payback: () => paybackCard(s, tariff, ctx.investment),
    alarm: () => (recentAlarm ? alarmCard(recentAlarm, () => ctx.go('alertas')) : null),
    periods: () => h('div', { class: 'row2' },
      metricCard('now.month', 'Este mês', 'calendar', 'var(--sky)', s.monthKwh, fmt.kwh, fmt.money(s.monthKwh * tariff)),
      metricCard('now.year', 'Este ano', 'today', 'var(--sky)', s.yearKwh, fmt.kwh, fmt.money(s.yearKwh * tariff))),
    impact: () => impactCard(s),
    conversion: () => live && conversionCard(live),
    strings: () => live && stringsCard(live),
    temperature: () => live?.temperatureC != null && temperatureCard(live),
    frequency: () => live?.gridFrequency != null && frequencyCard(live),
    grid: () => live && gridCard(live),
    equipment: () => live && equipmentCard(s, live),
  };
  const HALF = new Set(['temperature', 'frequency']);
  const shown = nowLayout().filter((x) => x.on).map((x) => [x.id, cards[x.id]() || null]).filter(([, el]) => el);
  // Cards pequenos vizinhos (temperatura, frequência) dividem a linha.
  const items = [];
  for (let k = 0; k < shown.length; k++) {
    const [id, el] = shown[k];
    if (HALF.has(id) && HALF.has(shown[k + 1]?.[0])) items.push(h('div', { class: 'row2' }, el, shown[++k][1]));
    else items.push(el);
  }

  const [label, dot] = STATUS[s.status] ?? STATUS.UNKNOWN;
  const reading = fmt.readingTime(s.lastReadingAt);
  return scaffold({
    title, animate,
    subtitle: h('span', null, h('span', { class: 'status-dot', style: { background: dot } }),
      (s.isDemo ? 'Modo demonstração' : label) + (reading ? ` · leitura das ${reading}` : '')),
    actions: [refreshButton(), customizeBtn, settingsBtn],
    body: [
      ctx.error ? h('div', { class: 'body error-text' }, ctx.error) : null,
      appear(powerCard(s), 0, animate),
      items.map((el, i) => appear(el, i + 1, animate)),
      shown.length ? null : h('div', { class: 'body muted', style: { textAlign: 'center', padding: '16px 0' } }, 'Nenhum card selecionado.'),
      h('button', { class: 'link pressable customize-link', onclick: openCustomize }, icon('tune'), ' Personalizar esta tela'),
    ],
  });
}

/** "Gerando agora": card com a cena animada em cima e os números embaixo. */
function powerCard(s) {
  scene ??= createScene();
  sceneSnapshot = s;
  const st = sceneState(s);
  scene.update(st);
  return card({ class: 'power-card' },
    h('div', { class: 'mini-scene' }, scene.el,
      h('span', { class: 'mini-sun' }, icon('sun'), ` ${hm(st.sun.sunrise)} – ${hm(st.sun.sunset)}`)),
    h('div', { class: 'power-hero' },
    h('div', { class: 'bolt' }, icon('bolt')),
    h('div', { class: 'grow', style: { paddingLeft: '14px' } },
      h('div', { class: 'title' }, 'Gerando agora'),
      h('div', { style: { display: 'flex', alignItems: 'baseline', flexWrap: 'wrap' } },
        animatedNumber('now.power', s.currentPowerKw, fmt.kw, 'display'), h('span', { class: 'headline-s' }, ' kW')),
      s.nominalPowerKw > 0
        ? h('div', { class: 'body-l muted' }, `${fmt.percent(s.currentPowerKw / s.nominalPowerKw)} de ${fmt.number(s.nominalPowerKw, 1)} kWp`)
        : null),
    h('div', { class: 'vdiv' }),
    h('div', { style: { textAlign: 'right' } },
      h('div', { class: 'body-l muted' }, 'Hoje'),
      h('div', { class: 'headline accent', style: { fontWeight: 400 } }, animatedNumber('now.today', s.todayKwh, fmt.kwh)))));
}

function curveCard(s) {
  const peak = s.todayCurve.length ? Math.max(...s.todayCurve.map((p) => p.kw)) : null;
  return card(null,
    h('div', { class: 'card-head' },
      cardLabel('Curva do dia', 'chart', 'var(--sun)'),
      peak != null ? h('div', { class: 'label muted' }, `pico ${fmt.kw(peak)} kW`) : null),
    h('div', { class: 'chart-wrap' }, lineChart({
      key: 'now.curve',
      series: [{ points: s.todayCurve.map((p) => [p.minuteOfDay, p.kw]), color: cssColor('primary'), label: 'Potência', filled: true }],
      format: (v) => `${fmt.kw(v)} kW`,
      liveEnd: true,
    })));
}

function goalCard(s) {
  const typical = typicalDayKwh(s) ?? (s.nominalPowerKw != null ? s.nominalPowerKw * 4.5 : null);
  if (typical == null) return null;
  const fraction = s.todayKwh / typical;
  return card(null,
    h('div', { class: 'card-head' },
      cardLabel('Comparado a um dia típico', 'sun', 'var(--sun)'),
      h('div', { class: 'title' }, fmt.percent(fraction))),
    blockProgress(fraction),
    h('div', { class: 'body muted' }, `${fmt.kwh(s.todayKwh)} de ${fmt.kwh(typical)} — média dos últimos 28 dias`));
}

function paybackCard(s, tariff, investment) {
  if (investment <= 0) return null;
  const earned = s.proceedsTotal ?? s.totalKwh * tariff;
  const fraction = Math.min(1, Math.max(0, earned / investment));
  const remaining = investment - earned;
  // Ritmo recente: média diária do mês corrente.
  const dailySavings = (typicalDayKwh(s) ?? s.monthKwh / Math.max(1, new Date().getDate())) * tariff;
  let footer = 'O sistema já se pagou! Tudo daqui pra frente é economia.';
  if (remaining > 0) {
    footer = `Faltam ${fmt.money(remaining)}`;
    if (dailySavings > 0) footer += ` · previsão: ${fmt.monthYear(addDays(localToday(), Math.floor(remaining / dailySavings)))}`;
  }
  return card(null,
    cardLabel('Retorno do investimento', 'savings', 'var(--leaf)'),
    h('div', { style: { display: 'flex', alignItems: 'baseline', flexWrap: 'wrap', gap: '8px', margin: '12px 0 6px' } },
      animatedNumber('now.payback', fraction * 100, (v) => `${fmt.number(v, 0)}%`, 'display'),
      h('span', { class: 'body muted' }, `${fmt.money(earned)} de ${fmt.money(investment)}`)),
    linearProgress(fraction, 'var(--leaf)'),
    h('div', { class: 'body muted', style: { marginTop: '10px' } }, footer));
}

function alarmCard(alarm, onOpen) {
  const start = parseTs(alarm.startedAt);
  const minutes = alarmMinutes(alarm);
  return card({ onclick: onOpen },
    cardLabel(alarm.active ? 'Alerta ativo' : 'Último alerta', 'warning', alarm.active ? 'var(--alert)' : 'var(--sun)'),
    h('div', { class: 'title', style: { marginTop: '10px' } }, alarmInfo(alarm.rawDescription).title),
    h('div', { class: 'body muted' },
      (start ? `${fmt.dayMonth(start.date)} às ${hm(start.minute)}` : '') + (minutes != null ? ` · durou ${fmt.duration(minutes)}` : '')));
}

function impactCard(s) {
  if (s.co2TotalKg == null) return null;
  const impact = (value, label) => h('div', null, h('div', { class: 'title' }, value), h('div', { class: 'body muted' }, label));
  return card(null,
    cardLabel('Impacto desde a instalação', 'eco', 'var(--leaf)'),
    h('div', { class: 'row3', style: { marginTop: '12px', gap: '8px' } },
      impact(fmt.kwh(s.totalKwh), 'gerados'),
      impact(fmt.weight(s.co2TotalKg), 'de CO₂ evitados'),
      // Uma árvore absorve ~22 kg de CO₂ por ano.
      impact(fmt.number(s.co2TotalKg / 22, 0), 'árvores por um ano')));
}
