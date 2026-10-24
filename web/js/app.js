/*
 * Casca do app: login ou abas (Agora, Inversor, Histórico, Alertas) com a
 * folha de Ajustes por cima. Rotas pelo hash da URL (o botão "voltar" do
 * navegador funciona). Atualização automática só com a aba visível.
 */
import * as repo from './repository.js';
import { store } from './store.js';
import { h, icon } from './ui.js';
import { parseTs } from './format.js';
import { errorMessage } from './errors.js';
import { renderLogin } from './screens/login.js';
import { renderNow } from './screens/now.js';
import { renderInverter } from './screens/inverter.js';
import { renderHistory } from './screens/history.js';
import { renderAlerts } from './screens/alerts.js';
import { renderSettings } from './screens/settings.js';

const TABS = [
  { id: 'agora', label: 'Agora', icon: 'bolt', render: renderNow },
  { id: 'inversor', label: 'Inversor', icon: 'cpu', render: renderInverter },
  { id: 'historico', label: 'Histórico', icon: 'insights', render: renderHistory },
  { id: 'alertas', label: 'Alertas', icon: 'bell', render: renderAlerts },
];

const RETRY_MS = 60_000;
const MAX_WAIT_MS = 10 * 60_000;
const GRACE_SECONDS = 45;

const root = document.getElementById('app');
const state = { refreshing: false, error: null };
let mode = null; // 'login' | 'app'
let renderedTab = null;
let settingsEl = null;

// ------------------------------------------------------------------- tema

const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
function applyTheme() {
  const theme = store.theme;
  const dark = theme === 'DARK' || (theme === 'SYSTEM' && darkQuery.matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0e0d0c' : '#efede8');
}
darkQuery.addEventListener('change', () => { applyTheme(); renderTab(true); });

// ------------------------------------------------------------------ rotas

const currentTab = () => {
  const id = location.hash.slice(1).split('/')[0];
  return TABS.find((t) => t.id === id) ?? TABS[0];
};
const settingsOpen = () => location.hash.endsWith('/ajustes');

export function go(tabId) { location.hash = tabId; }
export function openSettings() { location.hash = `${currentTab().id}/ajustes`; }
export function closeSettings() { history.back(); }

window.addEventListener('hashchange', () => { if (mode === 'app') { renderTab(); syncSettings(); } });

// ---------------------------------------------------------- contexto/telas

/** O que as telas recebem para se desenhar. */
function context(redraw) {
  return {
    snapshot: store.snapshot,
    tariff: store.tariff,
    investment: store.investment,
    refreshing: state.refreshing,
    error: state.error,
    redraw,
    refresh: refreshNow,
    openSettings,
    go,
    signOut,
  };
}

/** Estrutura de cada aba: título grande que, ao rolar, dá lugar a um título pequeno numa barra de vidro. */
export function scaffold({ title, subtitle, actions = [], body, animate }) {
  const bar = h('div', { class: 'topbar' },
    h('div', { class: 'lead' }), h('div', { class: 'bar-title' }, title), h('div', { class: 'actions' }, actions));
  const content = h('div', { class: 'content' },
    h('div', { class: `page-title ${animate ? 'fade-in' : ''}` },
      h('div', { class: 'headline' }, title), subtitle ? h('div', { class: 'body muted' }, subtitle) : null),
    body);
  const page = h('div', { class: 'page' }, content);
  page.threshold = 70;
  page.bar = bar;
  return [bar, page];
}

export function refreshButton() {
  return h('button', { class: 'icon-btn pressable', title: 'Atualizar', 'aria-label': 'Atualizar', onclick: refreshNow, disabled: state.refreshing },
    icon('refresh', state.refreshing ? 'spin' : ''));
}

function updateTopbar() {
  const page = root.querySelector('.page');
  page?.bar?.classList.toggle('glass', window.scrollY > page.threshold);
}
window.addEventListener('scroll', updateTopbar, { passive: true });

/** Desenha a aba atual. `soft`: só atualiza os dados, sem repetir as animações de entrada. */
function renderTab(soft = false) {
  if (mode !== 'app') return;
  const tab = currentTab();
  const changed = renderedTab !== tab.id;
  if (soft && !changed && document.activeElement?.closest('.screen input')) return;
  const screen = root.querySelector('.screen');
  const nodes = tab.render(context(() => renderTab(true)), { animate: changed });
  screen.replaceChildren(...[nodes].flat());
  screen.dataset.tab = tab.id;
  if (changed) window.scrollTo(0, 0);
  renderedTab = tab.id;
  updateTopbar();
  updateTabbar();
}

function updateTabbar() {
  const tab = currentTab();
  const alarm = store.snapshot?.recentAlarms?.some((a) => a.active) === true;
  root.querySelectorAll('.tab[data-id]').forEach((el) => {
    el.classList.toggle('active', el.dataset.id === tab.id);
    el.querySelector('.badge').hidden = !(el.dataset.id === 'alertas' && alarm);
  });
}

/** Celular: barra de abas flutuante embaixo. Tela grande: barra lateral com atalhos extras. */
function tabbar() {
  const extra = (iconName, label, onclick, cls = '') => h('button', { class: `tab desk-only ${cls}`, title: label, onclick },
    h('span', { class: 'tab-icon' }, icon(iconName)), h('span', { class: 'tab-label' }, label));
  const fullscreen = extra('expand', 'Tela cheia', toggleFullscreen, 'fs-btn');
  return h('nav', { class: 'tabbar' },
    h('div', { class: 'brand desk-only' }, h('span', { class: 'brand-icon' }, icon('sun')), 'Painel Solar Web'),
    TABS.map((t) => h('button', {
      class: 'tab', 'data-id': t.id, onclick: () => { if (currentTab().id !== t.id) go(t.id); },
    }, h('span', { class: 'tab-icon' }, icon(t.icon), h('span', { class: 'badge', hidden: true })), h('span', { class: 'tab-label' }, t.label))),
    h('div', { class: 'spacer desk-only' }),
    extra('settings', 'Ajustes', openSettings),
    document.fullscreenEnabled ? fullscreen : null);
}

/** Tela cheia (ex.: painel fixo numa TV ou monitor). */
function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen().catch(() => {});
}
document.addEventListener('fullscreenchange', () => {
  const btn = root.querySelector('.fs-btn');
  if (!btn) return;
  const on = !!document.fullscreenElement;
  btn.querySelector('.tab-icon').replaceChildren(icon(on ? 'shrink' : 'expand'));
  btn.querySelector('.tab-label').textContent = on ? 'Sair da tela cheia' : 'Tela cheia';
});

function syncSettings() {
  if (settingsOpen() && !settingsEl) {
    settingsEl = h('div', { class: 'sheet' });
    settingsEl.append(...[renderSettings(context(() => redrawSettings()))].flat());
    // Em tela grande os Ajustes abrem como painel lateral; clicar fora fecha.
    settingsEl.backdrop = h('div', { class: 'sheet-backdrop', onclick: closeSettings });
    root.append(settingsEl.backdrop, settingsEl);
    document.body.style.overflow = 'hidden';
  } else if (!settingsOpen() && settingsEl) {
    const el = settingsEl;
    settingsEl = null;
    el.classList.add('closing');
    el.backdrop.classList.add('closing');
    el.addEventListener('animationend', () => { el.remove(); el.backdrop.remove(); }, { once: true });
    document.body.style.overflow = '';
  }
}

function redrawSettings() {
  if (!settingsEl || settingsEl.contains(document.activeElement) && document.activeElement.tagName === 'INPUT') return;
  const scroll = settingsEl.scrollTop;
  settingsEl.replaceChildren(...[renderSettings(context(() => redrawSettings()))].flat());
  settingsEl.scrollTop = scroll;
}

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && settingsEl && !document.querySelector('.modal-backdrop')) closeSettings();
});

// -------------------------------------------------------------- atualização

async function refreshNow() {
  if (state.refreshing) return;
  state.refreshing = true;
  renderTab(true);
  try {
    await repo.refresh();
    state.error = null;
  } catch (e) {
    state.error = errorMessage(e);
  } finally {
    state.refreshing = false;
    renderTab(true);
  }
}

/**
 * O coletor envia uma leitura a cada ~5 min: buscamos logo depois do próximo
 * envio esperado e, se ainda não chegou, tentamos de novo a cada minuto.
 */
function nextRefreshDelayMs(s) {
  const last = parseTs(s?.lastReadingAt);
  if (!last) return RETRY_MS;
  const readingUtc = Date.parse(`${last.date}T00:00:00Z`) + last.seconds * 1000 - s.utcOffsetSeconds * 1000;
  const interval = s.collector?.intervalSeconds ?? 300;
  const expected = readingUtc + (interval + GRACE_SECONDS) * 1000;
  return Math.min(MAX_WAIT_MS, Math.max(RETRY_MS, expected - Date.now()));
}

let timer = null;
async function autoRefresh() {
  clearTimeout(timer);
  if (mode !== 'app' || document.hidden) return;
  await refreshNow();
  if (mode !== 'app' || document.hidden) return;
  timer = setTimeout(autoRefresh, nextRefreshDelayMs(store.snapshot));
}
document.addEventListener('visibilitychange', () => { if (document.hidden) clearTimeout(timer); else autoRefresh(); });

// ------------------------------------------------------------------- raiz

function signOut() {
  repo.signOut();
}

function renderRoot() {
  applyTheme();
  const next = store.loggedIn ? 'app' : 'login';
  if (next === mode) return;
  mode = next;
  clearTimeout(timer);
  renderedTab = null;
  settingsEl = null;
  document.body.style.overflow = '';
  if (mode === 'login') {
    if (location.hash) history.replaceState(null, '', location.pathname + location.search);
    root.replaceChildren(renderLogin());
  } else {
    // Ajustes não abrem sozinhos ao recarregar a página.
    if (settingsOpen()) history.replaceState(null, '', `#${currentTab().id}`);
    root.replaceChildren(h('div', { class: 'screen' }), tabbar());
    renderTab();
    autoRefresh();
  }
  window.scrollTo(0, 0);
}

store.subscribe((key) => {
  if (key === 'theme') { applyTheme(); renderTab(true); redrawSettings(); }
  if (key === 'credentials' || key === 'demo') renderRoot();
  if (key === 'snapshot' || key === 'investment') { renderTab(true); redrawSettings(); }
  if (key === 'nowLayout') renderTab(true);
});

renderRoot();
