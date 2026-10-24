import * as repo from '../repository.js';
import { scaffold } from '../app.js';
import { store } from '../store.js';
import { errorMessage } from '../errors.js';
import { addDays, epochDay, fmt, localToday, plusMonths, weekStart, yearMonth } from '../format.js';
import { average, sum } from '../domain.js';
import { h, icon, appear, card, cardLabel, segmented, lineChart, barChart, cssColor, spinner } from '../ui.js';

const PERIODS = ['Dia', 'Semana', 'Mês', 'Ano', 'Total'];
const [DAY, WEEK, MONTH, YEAR, TOTAL] = [0, 1, 2, 3, 4];

/** Parâmetros mostrados como atalho. */
const FEATURED_FIELDS = ['output_power', 'inverter_temperature', 'pv_voltage1', 'pv_voltage2', 'phase_voltage_t', 'grid_frequency'];

/** Estado da aba (sobrevive à troca de abas, como o ViewModel do app Android). */
const state = {
  period: DAY,
  date: localToday(),
  loading: true,
  error: null,
  fields: [],
  fieldId: 'output_power',
  compare: false,
  series: [],
  compareSeries: [],
  bars: [],
  previousWeekKwh: null,
  currentWeekComparableKwh: null,
};
/** Conta para a qual o estado foi carregado (trocar de conta recomeça do zero). */
let startedFor = null;
let loadId = 0;
let redraw = () => {};

function canGoForward() {
  const today = localToday();
  switch (state.period) {
    case DAY: return state.date < today;
    case WEEK: return weekStart(state.date) < weekStart(today);
    case MONTH: return yearMonth(state.date) < yearMonth(today);
    case YEAR: return state.date.slice(0, 4) < today.slice(0, 4);
    default: return false;
  }
}

function title() {
  const today = localToday();
  switch (state.period) {
    case DAY: return state.date === today ? 'Hoje' : fmt.dayMonth(state.date);
    case WEEK: {
      const start = weekStart(state.date);
      return start === weekStart(today) ? 'Esta semana' : `${fmt.dayMonth(start)} – ${fmt.dayMonth(addDays(start, 6))}`;
    }
    case MONTH: return fmt.monthYear(state.date);
    case YEAR: return state.date.slice(0, 4);
    default: return 'Desde a instalação';
  }
}

function shift(step) {
  const next = [
    () => addDays(state.date, step),
    () => addDays(state.date, 7 * step),
    () => plusMonths(state.date, step),
    () => plusMonths(state.date, 12 * step),
    () => state.date,
  ][state.period]();
  const today = localToday();
  state.date = next > today ? today : next;
  load();
}

async function load() {
  const id = ++loadId;
  state.loading = true;
  state.error = null;
  redraw();
  const s = { ...state };
  const today = localToday();
  try {
    let patch;
    if (s.period === DAY) {
      const series = await repo.parameterSeries(s.fieldId, s.date);
      const yesterday = addDays(s.date, -1);
      const previous = s.compare ? (await repo.compareDays(s.fieldId, [yesterday]))[yesterday] ?? [] : [];
      patch = { series, compareSeries: previous };
    } else if (s.period === WEEK) {
      const start = weekStart(s.date);
      const days = await repo.energyByDay(addDays(start, -7), addDays(start, 6));
      const bars = [0, 1, 2, 3, 4, 5, 6].map((i) => {
        const day = addDays(start, i);
        return { label: fmt.weekdayDay(day), kwh: day > today ? 0 : days[day] ?? 0 };
      });
      // Compara só os dias já decorridos (semana em andamento) com os mesmos dias da anterior.
      const elapsed = start === weekStart(today) ? epochDay(today) - epochDay(start) : 6;
      const range = Array.from({ length: elapsed + 1 }, (_, i) => i);
      const current = sum(range.map((i) => days[addDays(start, i)] ?? 0));
      const previous = sum(range.map((i) => days[addDays(start, i - 7)] ?? 0));
      patch = { bars, currentWeekComparableKwh: current, previousWeekKwh: previous > 0 ? previous : null };
    } else if (s.period === MONTH) {
      const month = yearMonth(s.date);
      const days = await repo.dailyHistory(month);
      // Dias futuros vêm zerados: mostramos só até hoje.
      patch = { bars: month === yearMonth(today) ? days.slice(0, +today.slice(8, 10)) : days };
    } else if (s.period === YEAR) {
      const year = +s.date.slice(0, 4);
      const months = (await repo.monthlyHistory(year)).map((p, i) => ({ ...p, label: fmt.monthShort(i + 1) }));
      patch = { bars: year === +today.slice(0, 4) ? months.slice(0, +today.slice(5, 7)) : months };
    } else {
      patch = { bars: await repo.yearlyHistory() };
    }
    if (id !== loadId) return;
    Object.assign(state, patch, { loading: false });
  } catch (e) {
    if (id !== loadId) return;
    Object.assign(state, { loading: false, error: errorMessage(e) });
  }
  redraw();
}

export function renderHistory(ctx, { animate }) {
  redraw = ctx.redraw;
  const account = store.demo ? 'demo' : store.credentials?.username;
  if (startedFor !== account) {
    startedFor = account;
    Object.assign(state, { period: DAY, date: localToday(), fields: [], fieldId: 'output_power', compare: false, series: [], compareSeries: [], bars: [] });
    repo.chartFields().then((fields) => { state.fields = fields; redraw(); }).catch(() => {});
    load();
  }

  const body = [
    wide(appear(segmented(PERIODS, state.period, (i) => { state.period = i; state.date = localToday(); load(); }), 0, animate)),
    state.period !== TOTAL ? appear(h('div', { class: 'hstack wide' },
      h('button', { class: 'icon-btn pressable', 'aria-label': 'Anterior', onclick: () => shift(-1) }, icon('left')),
      h('div', { class: 'title grow center' }, title()),
      h('button', { class: 'icon-btn pressable', 'aria-label': 'Próximo', disabled: !canGoForward(), onclick: () => shift(1) }, icon('right'))),
    1, animate) : null,
    ...(state.period === DAY ? dayContent(animate) : barsContent(ctx.tariff, animate)),
    state.error ? h('div', { class: 'body error-text wide' }, state.error) : null,
  ];
  return scaffold({ title: 'Histórico', animate, body });
}

/** Ocupa a largura toda também no layout de colunas da tela grande. */
const wide = (el) => { el.classList.add('wide'); return el; };

function dayContent(animate) {
  const field = state.fields.find((f) => f.id === state.fieldId);
  const unit = field?.unit || 'kW';
  const digits = unit === 'kW' || unit === 'Hz' ? 2 : 1;
  let featured = FEATURED_FIELDS.map((id) => state.fields.find((f) => f.id === id)).filter(Boolean);
  if (!featured.length) featured = [{ id: 'output_power', label: 'Potência ativa', unit: 'kW' }];
  const chip = (label, on, onclick) => h('button', { class: `chip pressable ${on ? 'on' : ''}`, onclick }, label);
  const values = state.series.map((p) => p.value);
  const fmtValue = (v) => `${fmt.number(v, digits)} ${unit}`;

  const series = [];
  if (state.compareSeries.length) {
    series.push({ points: state.compareSeries.map((p) => [p.minuteOfDay, p.value]), color: cssColor('on-surface-variant'),
      label: 'Ontem', filled: false, dashed: true });
  }
  series.push({ points: state.series.map((p) => [p.minuteOfDay, p.value]), color: cssColor('primary'),
    label: state.compare ? 'Dia' : field?.label ?? '', filled: state.fieldId === 'output_power' });

  const stat = (label, value) => card(null, h('div', { class: 'label-s muted' }, label), h('div', { class: 'title', style: { marginTop: '6px' } }, value));

  return [
    appear(h('div', { class: 'chips wide' },
      featured.map((f) => chip(f.label, f.id === state.fieldId, () => { state.fieldId = f.id; load(); })),
      chip('Comparar com ontem', state.compare, () => { state.compare = !state.compare; load(); })), 2, animate),
    appear(card({ class: 'wide' },
      h('div', { class: 'card-head' },
        cardLabel(field?.label ?? 'Potência ativa', 'chart', 'var(--primary)'),
        values.length ? h('div', { class: 'label muted' }, `máx ${fmtValue(Math.max(...values))}`) : null),
      h('div', { class: 'chart-wrap' },
        lineChart({
          key: 'history.day',
          series, format: fmtValue, height: 230,
          minY: state.fieldId === 'output_power' || unit === 'A' ? 0 : null,
          liveEnd: state.date === localToday() && !state.compareSeries.length,
        }),
        h('div', { class: 'overlay' }, state.loading ? spinner()
          : !state.series.length ? h('div', { class: 'body muted' }, 'Sem leituras neste dia') : null))), 3, animate),
    values.length ? appear(h('div', { class: 'row3 wide' },
      stat('Mínimo', fmtValue(Math.min(...values))),
      stat('Média', fmtValue(average(values))),
      stat('Máximo', fmtValue(Math.max(...values)))), 4, animate) : null,
  ];
}

function barsContent(tariff, animate) {
  const bars = state.bars;
  const total = sum(bars.map((b) => b.kwh));
  const best = bars.reduce((a, b) => (a == null || b.kwh > a.kwh ? b : a), null);
  const nonZero = bars.filter((b) => b.kwh > 0);
  const unitLabel = state.period === WEEK || state.period === MONTH ? 'dia' : state.period === YEAR ? 'mês' : 'ano';
  const stat = (label, value, caption) => card(null,
    h('div', { class: 'label-s muted' }, label),
    h('div', { class: 'title', style: { marginTop: '6px' } }, value),
    caption ? h('div', { class: 'body ok-text' }, caption) : null);

  const out = [
    appear(card({ class: 'wide' },
      h('div', { class: 'card-head' },
        cardLabel(`Energia por ${unitLabel}`, 'bars', 'var(--primary)'),
        h('div', { class: 'label muted' }, '— média')),
      h('div', { class: 'chart-wrap' },
        state.loading ? h('div', { style: { height: '230px' } }) : barChart({ key: `history.${state.period}.${state.date}`, points: bars, format: fmt.kwh, height: 230, labelEvery: bars.length > 16 ? 5 : 1 }),
        state.loading ? h('div', { class: 'overlay' }, spinner()) : null),
      h('div', { class: 'label muted center', style: { marginTop: '6px' } }, 'Clique numa barra para ver o valor')), 2, animate),
  ];
  if (!state.loading && bars.length) {
    out.push(appear(h('div', { class: 'row2' },
      stat('Total', fmt.kwh(total), fmt.money(total * tariff)),
      stat(`Média por ${unitLabel}`, nonZero.length ? fmt.kwh(average(nonZero.map((b) => b.kwh))) : '—')), 3, animate));
    if (best && best.kwh > 0) {
      const name = { [WEEK]: 'Melhor dia', [MONTH]: 'Melhor dia', [YEAR]: 'Melhor mês' }[state.period] ?? 'Melhor ano';
      out.push(appear(stat(`${name}: ${best.label}`, fmt.kwh(best.kwh), fmt.money(best.kwh * tariff)), 4, animate));
    }
    if (state.period === WEEK) out.push(appear(weekComparison(), 5, animate));
  }
  return out;
}

/** "+12% em relação à semana anterior" (mesmos dias da semana). */
function weekComparison() {
  const previous = state.previousWeekKwh;
  const current = state.currentWeekComparableKwh;
  if (previous == null || current == null) return null;
  const change = (current - previous) / previous;
  const up = change >= 0;
  return card(null,
    h('div', { class: 'label-s muted' }, 'Em relação à semana anterior'),
    h('div', { class: 'headline-s', style: { marginTop: '6px', color: up ? 'var(--leaf)' : 'var(--ember)' } },
      `${up ? '▲' : '▼'} ${fmt.percent(Math.abs(change))}`),
    h('div', { class: 'body muted' }, `${fmt.kwh(current)} contra ${fmt.kwh(previous)} nos mesmos dias da semana passada`));
}
