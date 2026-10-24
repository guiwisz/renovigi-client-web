import * as repo from '../repository.js';
import { scaffold } from '../app.js';
import { store } from '../store.js';
import { errorMessage } from '../errors.js';
import { alarmInfo, alarmMinutes } from '../domain.js';
import { fmt, hm, localToday, parseTs, plusMonths } from '../format.js';
import { h, icon, appear, card, sectionTitle, spinner } from '../ui.js';

const KIND = {
  GRID: ['plug', 'var(--sky)'],
  PANELS: ['panel', 'var(--sun)'],
  INVERTER: ['cpu', 'var(--alert)'],
  OTHER: ['warning', 'var(--sun)'],
};

const state = { loading: true, alarms: [], error: null };
const expanded = new Set();
let startedFor = null;
let redraw = () => {};

async function load() {
  state.loading = true;
  state.error = null;
  redraw();
  try {
    const today = localToday();
    const alarms = await repo.alarms(plusMonths(today, -12), today);
    Object.assign(state, { loading: false, alarms: alarms.sort((a, b) => b.startedAt.localeCompare(a.startedAt)) });
  } catch (e) {
    Object.assign(state, { loading: false, alarms: [], error: errorMessage(e) });
  }
  redraw();
}

export function renderAlerts(ctx, { animate }) {
  redraw = ctx.redraw;
  const account = store.demo ? 'demo' : store.credentials?.username;
  if (startedFor !== account) {
    startedFor = account;
    load();
  }

  const reload = h('button', { class: 'icon-btn pressable', title: 'Atualizar', 'aria-label': 'Atualizar', onclick: load, disabled: state.loading },
    icon('refresh', state.loading ? 'spin' : ''));
  const body = [];
  if (state.error) body.push(h('div', { class: 'body error-text' }, state.error));
  if (!state.loading && !state.alarms.length && !state.error) {
    body.push(appear(h('div', { class: 'empty' },
      h('div', { style: { fontSize: '52px', color: 'var(--leaf)' } }, icon('check')),
      h('div', { class: 'title' }, 'Nenhum alerta no último ano'),
      h('div', { class: 'body-l muted' }, 'Seu sistema está funcionando sem interrupções.')), 0, animate));
  }
  if (state.loading && !state.alarms.length) body.push(h('div', { class: 'empty' }, spinner()));

  // Agrupados por mês, do mais recente para o mais antigo.
  const groups = new Map();
  for (const alarm of state.alarms) {
    const month = parseTs(alarm.startedAt)?.date.slice(0, 7) ?? '';
    if (!groups.has(month)) groups.set(month, []);
    groups.get(month).push(alarm);
  }
  let index = 0;
  for (const [month, alarms] of groups) {
    body.push(appear(sectionTitle(month ? fmt.monthYear(month) : 'Sem data'), index++, animate));
    for (const alarm of alarms) body.push(appear(alarmRow(alarm), index++, animate));
  }

  return scaffold({
    title: 'Alertas',
    subtitle: state.loading ? null : `${state.alarms.length} nos últimos 12 meses`,
    animate, actions: [reload], body,
  });
}

function alarmRow(alarm) {
  const info = alarmInfo(alarm.rawDescription);
  const [iconName, color] = KIND[info.kind];
  const start = parseTs(alarm.startedAt);
  const minutes = alarmMinutes(alarm);
  const details = h('div', { class: `collapsible ${expanded.has(alarm.id) ? 'open' : ''}` },
    h('div', null,
      h('div', { class: 'body', style: { paddingTop: '12px' } }, info.explanation),
      h('div', { class: 'label muted', style: { marginTop: '8px' } }, `Código ${alarm.code} · ${alarm.rawDescription}`)));
  return card({
    onclick: () => {
      if (expanded.has(alarm.id)) expanded.delete(alarm.id); else expanded.add(alarm.id);
      details.classList.toggle('open', expanded.has(alarm.id));
    },
  },
  h('div', { class: 'hstack', style: { gap: '12px' } },
    h('div', {
      style: {
        width: '38px', height: '38px', borderRadius: '12px', display: 'grid', placeItems: 'center', flex: 'none',
        fontSize: '20px', color, background: `color-mix(in srgb, ${color} 15%, transparent)`,
      },
    }, icon(iconName)),
    h('div', { class: 'grow' },
      h('div', { class: 'title' }, info.title),
      h('div', { class: 'body muted' },
        (start ? `${fmt.dayMonth(start.date)}, ${hm(start.minute)}` : '') + (minutes != null ? ` · ${fmt.duration(minutes)}` : ''))),
    alarm.active
      ? h('span', { class: 'pill', style: { background: 'var(--alert)' } }, 'Ativo')
      : h('span', { style: { width: '8px', height: '8px', borderRadius: '50%', background: 'var(--outline)', flex: 'none' } })),
  details);
}
