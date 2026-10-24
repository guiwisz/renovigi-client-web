/* Componentes de interface compartilhados pelas telas (DOM puro, sem framework). */

/** Cria um elemento: h('div', { class: 'card', onclick }, filho1, 'texto', ...). */
export function h(tag, props, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props ?? {})) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
    else if (key === 'class') el.className = value;
    else if (key in el && typeof value !== 'string') el[key] = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

// ---------------------------------------------------------------- ícones

const ICONS = {
  bolt: '<path fill="currentColor" stroke="none" d="M11 21h-1l1-7H7.5c-.88 0-.33-.75-.31-.78C8.48 10.94 10.42 7.54 13.01 3h1l-1 7h3.51c.4 0 .62.19.4.66C12.97 17.55 11 21 11 21z"/>',
  cpu: '<rect x="5" y="5" width="14" height="14" rx="2"/><rect x="9" y="9" width="6" height="6" rx="1"/><path d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3"/>',
  insights: '<path d="M3 3v18h18"/><path d="m7 15 4-4 3 3 6-6"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  settings: '<path fill="currentColor" stroke="none" d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84a.47.47 0 0 0-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87a.47.47 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-2.64-6.36L21 8"/><path d="M21 3v5h-5"/>',
  close: '<path d="M18 6 6 18M6 6l12 12"/>',
  left: '<path d="m15 18-6-6 6-6"/>',
  right: '<path d="m9 18 6-6-6-6"/>',
  payments: '<rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/><path d="M6 10v4M18 10v4"/>',
  eco: '<path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  today: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/><rect x="7" y="13" width="4" height="4" rx="0.5" fill="currentColor"/>',
  chart: '<path d="M3 17 9 11l4 4 8-8"/><path d="M14 7h7v7"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>',
  savings: '<path d="M19 7c-1.5-2-4-3-7-3C7 4 3 7 3 11c0 2.5 1.3 4.6 3.5 5.9V20h3v-2h5v2h3v-3.1c1.2-.7 2.2-1.7 2.8-2.9H22v-4h-1.5c-.3-1.1-.8-2.1-1.5-3z"/><circle cx="16" cy="10" r="0.6" fill="currentColor"/>',
  warning: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4M12 17h.01"/>',
  swap: '<path d="M8 3 4 7l4 4M4 7h16M16 21l4-4-4-4M20 17H4"/>',
  panel: '<path d="M3 15 5 4h14l2 11z"/><path d="M4 9.5h16M12 4v11M12 15v5M8 20h8"/>',
  thermo: '<path d="M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"/>',
  plug: '<path d="M12 22v-5M9 8V2M15 8V2M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8Z"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
  check: '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>',
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  lock: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
  eye: '<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
  eyeOff: '<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24M10.73 5.08A10.4 10.4 0 0 1 12 5c7 0 10 7 10 7a13.2 13.2 0 0 1-1.67 2.68M6.61 6.61A13.5 13.5 0 0 0 2 12s3 7 10 7a9.7 9.7 0 0 0 5.39-1.61M2 2l20 20"/>',
  arrow: '<path d="M5 12h14M12 5l7 7-7 7"/>',
  bars: '<path d="M3 3v18h18"/><path d="M8 17v-4M13 17V7M18 17v-7"/>',
  home: '<path d="m3 10 9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/>',
  tune: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
  up: '<path d="m18 15-6-6-6 6"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  grip: '<circle cx="9" cy="6" r="1" fill="currentColor"/><circle cx="15" cy="6" r="1" fill="currentColor"/><circle cx="9" cy="12" r="1" fill="currentColor"/><circle cx="15" cy="12" r="1" fill="currentColor"/><circle cx="9" cy="18" r="1" fill="currentColor"/><circle cx="15" cy="18" r="1" fill="currentColor"/>',
  expand: '<path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>',
  shrink: '<path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7"/>',
};

export function icon(name, extraClass = '') {
  const wrap = document.createElement('span');
  wrap.innerHTML = `<svg class="icon ${extraClass}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ` +
    `stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;
  return wrap.firstElementChild;
}

// ----------------------------------------------------------------- cores

/** Valor atual de uma variável de cor do tema (ex.: 'primary'). */
export const cssColor = (name) => getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim();

/** "#rrggbb" + alfa → "rgba(...)". */
export function alpha(hex, a) {
  const v = hex.replace('#', '');
  const n = parseInt(v.length === 3 ? v.split('').map((c) => c + c).join('') : v, 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}

// ---------------------------------------------------------------- animação

export const easeOut = (t) => 1 - Math.pow(1 - t, 3);
/** Aproximação do FastOutSlowIn do Material. */
export const fastOutSlowIn = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2) * 0.35 + easeOut(t) * 0.65;
/** Mola com leve "overshoot". */
export const springOut = (t) => 1 + 2.2 * Math.pow(t - 1, 3) + 1.2 * Math.pow(t - 1, 2);

export function tween(duration, onFrame, easing = fastOutSlowIn) {
  const start = performance.now();
  let raf;
  const step = (now) => {
    const t = Math.min(1, (now - start) / duration);
    onFrame(easing(t), t);
    if (t < 1) raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}

const lastNumbers = new Map();

/**
 * Número que "conta" até o novo valor. `key` identifica o número entre
 * redesenhos da tela: só anima quando o valor muda (ou na primeira vez).
 */
export function animatedNumber(key, value, format, className = '') {
  const el = h('span', { class: `tnum ${className}` });
  const from = lastNumbers.get(key) ?? 0;
  lastNumbers.set(key, value);
  if (from === value) {
    el.textContent = format(value);
  } else {
    el.textContent = format(from);
    tween(900, (p) => { el.textContent = format(from + (value - from) * p); });
  }
  return el;
}

/** Marca o elemento para "surgir" com atraso proporcional à posição. */
export function appear(el, index, enabled = true) {
  if (enabled) {
    el.classList.add('appear');
    el.style.setProperty('--i', index);
  }
  return el;
}

// ---------------------------------------------------------------- cartões

/** Card "inset grouped": fundo elevado sutil, cantos grandes e borda finíssima. */
export function card(props, ...children) {
  const { onclick, class: cls = '', ...rest } = props ?? {};
  return h('div', { ...rest, class: `card ${onclick ? 'clickable pressable' : ''} ${cls}`, onclick }, ...children);
}

/** Rótulo pequeno em caixa alta com ícone colorido, usado no topo dos cards. */
export function cardLabel(text, iconName, tint) {
  return h('div', { class: 'card-label', style: { '--tint': tint } },
    h('div', { class: 'dot' }, icon(iconName)), h('span', { class: 'label-s' }, text));
}

/** Card de métrica: rótulo, valor animado e linha de apoio. */
export function metricCard(key, label, iconName, tint, value, format, caption) {
  return card(null,
    cardLabel(label, iconName, tint),
    h('div', { class: 'headline-s', style: { marginTop: '14px' } }, animatedNumber(key, value, format)),
    caption ? h('div', { class: 'body muted' }, caption) : null);
}

export const sectionTitle = (text) => h('div', { class: 'section-title' }, text);

/** Barra segmentada em blocos, preenchendo da esquerda (cada bloco acende progressivamente). */
export function blockProgress(fraction, blocks = 12) {
  const cells = Array.from({ length: blocks }, () => h('div'));
  const target = Math.min(1, Math.max(0, fraction));
  const primary = cssColor('primary');
  tween(1000, (p) => {
    cells.forEach((cell, i) => {
      const fill = Math.min(1, Math.max(0, target * p * blocks - i));
      cell.style.background = fill > 0 ? `color-mix(in srgb, ${primary} ${fill * 100}%, var(--surface-variant))` : '';
    });
  });
  return h('div', { class: 'blocks' }, cells);
}

/** Barra de progresso linear (anima ao aparecer). */
export function linearProgress(fraction, color) {
  const inner = h('div', { style: { background: color } });
  requestAnimationFrame(() => requestAnimationFrame(() => { inner.style.width = `${Math.min(1, Math.max(0, fraction)) * 100}%`; }));
  return h('div', { class: 'progress' }, inner);
}

/** Medidor em semicírculo (0..1) com ponta marcada; anima ao aparecer. */
export function semiGauge(fraction) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 200 100');
  svg.style.cssText = 'width:100%;max-width:200px;display:block;margin:0 auto;';
  const r = 86;
  const arc = (p) => {
    const a = Math.PI * (1 + p);
    return `M ${100 - r} 100 A ${r} ${r} 0 0 1 ${100 + r * Math.cos(a)} ${100 + r * Math.sin(a)}`;
  };
  const mk = (tag, attrs) => {
    const el = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    svg.append(el);
    return el;
  };
  mk('path', { d: arc(1), fill: 'none', stroke: 'var(--surface-variant)', 'stroke-width': 14, 'stroke-linecap': 'round' });
  const value = mk('path', { fill: 'none', stroke: 'var(--primary)', 'stroke-width': 14, 'stroke-linecap': 'round' });
  const outer = mk('circle', { r: 7.7, fill: 'var(--container-high)' });
  const inner = mk('circle', { r: 4.5, fill: 'var(--primary)' });
  const target = Math.min(1, Math.max(0, fraction));
  const draw = (p) => {
    const f = Math.max(0.0001, target * p);
    value.setAttribute('d', arc(f));
    const a = Math.PI * (1 + f);
    for (const c of [outer, inner]) { c.setAttribute('cx', 100 + r * Math.cos(a)); c.setAttribute('cy', 100 + r * Math.sin(a)); }
  };
  draw(0);
  tween(1100, draw);
  svg.setAttribute('overflow', 'visible');
  return svg;
}

/** Controle segmentado com "pílula" que desliza até a opção escolhida. */
export function segmented(options, selected, onSelect) {
  const thumb = h('div', { class: 'thumb', style: { width: `calc((100% - 4px) / ${options.length})` } });
  const buttons = options.map((label, i) => h('button', {
    class: i === selected ? 'on' : '',
    onclick: () => {
      if (i === selected) return;
      selected = i;
      buttons.forEach((b, j) => b.classList.toggle('on', j === i));
      thumb.style.transform = `translateX(${i * 100}%)`;
      onSelect(i);
    },
  }, label));
  thumb.style.transform = `translateX(${selected * 100}%)`;
  return h('div', { class: 'segmented' }, thumb, buttons);
}

/** Diálogo de confirmação. */
export function confirmDialog(title, text, confirmLabel, onConfirm) {
  const close = () => backdrop.remove();
  const backdrop = h('div', { class: 'modal-backdrop', onclick: (e) => { if (e.target === backdrop) close(); } },
    h('div', { class: 'modal', role: 'dialog' },
      h('h2', null, title),
      h('div', { class: 'body muted' }, text),
      h('div', { class: 'buttons' },
        h('button', { onclick: close }, 'Cancelar'),
        h('button', { onclick: () => { close(); onConfirm(); } }, confirmLabel))));
  document.body.append(backdrop);
}

// ---------------------------------------------------------------- gráficos

const animatedCharts = new Map();

/** Canvas que se redesenha ao mudar de tamanho; `draw(ctx, width, height)`. */
function chartCanvas(height, draw) {
  const canvas = h('canvas', { class: 'chart', style: { height: `${height}px` } });
  const paint = () => {
    const width = canvas.clientWidth;
    if (!width) return;
    const dpr = window.devicePixelRatio || 1;
    if (canvas.width !== Math.round(width * dpr)) canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);
    draw(ctx, width, height);
  };
  new ResizeObserver(paint).observe(canvas);
  canvas.paint = paint;
  return canvas;
}

function pillLabel(ctx, text, centerX, width) {
  ctx.font = '12px ' + getComputedStyle(document.body).fontFamily;
  const pad = 8;
  const w = ctx.measureText(text).width + pad * 2;
  const hgt = 24;
  const left = Math.min(Math.max(0, centerX - w / 2), width - w);
  ctx.fillStyle = cssColor('primary');
  ctx.beginPath();
  ctx.roundRect(left, 0, w, hgt, hgt / 2);
  ctx.fill();
  ctx.fillStyle = cssColor('on-primary');
  ctx.textBaseline = 'middle';
  ctx.fillText(text, left + pad, hgt / 2 + 0.5);
}

/** Passo "redondo" (1, 2, 2,5 ou 5 × 10ⁿ) mais próximo acima de `raw`. */
function niceStep(raw) {
  const power = 10 ** Math.floor(Math.log10(raw));
  const n = raw / power;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * power;
}

/**
 * Gráfico de linha ao longo do dia (x em minutos):
 * - as linhas "se desenham" e o preenchimento aparece junto;
 * - passar o mouse ou arrastar mostra um marcador com o valor de cada série;
 * - eixo X fixo [startMinute, endMinute] para o dia "andar" sem esticar;
 * - eixo Y com linhas de referência em valores "redondos" (ex.: 0, 1, 2, 3 kW),
 *   na unidade que `format` usa.
 * `series`: [{ points: [[minuto, valor]], color, label, filled, dashed }]
 */
export function lineChart({ key, series, format, height = 200, startMinute = 300, endMinute = 1140, minY = 0, liveEnd = false }) {
  const visible = series.map((s) => ({ ...s, points: s.points.filter(([m]) => m >= startMinute && m <= endMinute) }));
  let progress = 0;
  let scrub = null;
  let plotLeft = 0;
  const unit = format(1).replace(/^[-\d.,\s]+/, '').trim();

  const canvas = chartCanvas(height, (ctx, width) => {
    const font = getComputedStyle(document.body).fontFamily;
    const labelSpace = 22;
    const top = 34;
    const chartH = height - labelSpace - top;
    const all = visible.flatMap((s) => s.points.map((p) => p[1]));
    const rawMax = all.length ? Math.max(...all) : 1;
    const rawMin = minY ?? (all.length ? Math.min(...all) : 0);
    const step = niceStep(Math.max(rawMax - rawMin, 0.1) / 5);
    const minV = minY ?? Math.floor(rawMin / step) * step;
    const maxV = Math.max(minV + step, Math.ceil((rawMax + step * 0.05) / step) * step);
    const ticks = [];
    for (let v = minV; v <= maxV + step / 2; v += step) ticks.push(v);
    const digits = Math.min(2, (String(+step.toFixed(6)).split('.')[1] ?? '').length);
    const tickText = (v) => `${v.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits })}${unit ? ` ${unit}` : ''}`;

    ctx.font = `11px ${font}`;
    plotLeft = Math.ceil(Math.max(...ticks.map((v) => ctx.measureText(tickText(v)).width))) + 8;
    const plotW = width - plotLeft;
    const x = (m) => plotLeft + ((m - startMinute) / (endMinute - startMinute)) * plotW;
    const y = (v) => top + chartH * (1 - (v - minV) / (maxV - minV));
    const baseline = top + chartH;

    // Linhas de referência do eixo Y, com o valor à esquerda.
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    for (const v of ticks) {
      const gy = y(v);
      ctx.strokeStyle = alpha(cssColor('outline'), 0.6);
      ctx.lineWidth = 1;
      ctx.setLineDash(v === minV ? [] : [3, 4]);
      ctx.beginPath(); ctx.moveTo(plotLeft, gy); ctx.lineTo(width, gy); ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = cssColor('on-surface-variant');
      ctx.fillText(tickText(v), plotLeft - 6, gy);
    }

    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let hour = Math.floor((startMinute / 60 + 2) / 3) * 3; hour <= endMinute / 60; hour += 3) {
      const gx = x(hour * 60);
      if (gx > width - 12) continue;
      ctx.strokeStyle = alpha(cssColor('outline'), 0.6);
      ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(gx, top); ctx.lineTo(gx, baseline); ctx.stroke();
      ctx.fillStyle = cssColor('on-surface-variant');
      ctx.fillText(`${String(hour).padStart(2, '0')}:00`, gx, baseline + 6);
    }
    ctx.textAlign = 'left';

    visible.forEach((s, index) => {
      if (s.points.length < 2) return;
      const path = new Path2D();
      path.moveTo(x(s.points[0][0]), y(s.points[0][1]));
      for (let i = 1; i < s.points.length; i++) {
        const [pm, pv] = s.points[i - 1];
        const [cm, cv] = s.points[i];
        const mid = (x(pm) + x(cm)) / 2;
        path.bezierCurveTo(mid, y(pv), mid, y(cv), x(cm), y(cv));
      }
      const x0 = x(s.points[0][0]);
      const x1 = x(s.points.at(-1)[0]);
      const stagger = Math.min(1, Math.max(0, progress * 1.15 - index * 0.15));
      const revealX = x0 + (x1 - x0) * stagger;
      ctx.save();
      ctx.beginPath(); ctx.rect(0, 0, revealX + 2, height); ctx.clip();
      if (s.filled) {
        const area = new Path2D(path);
        area.lineTo(x1, baseline); area.lineTo(x0, baseline); area.closePath();
        const grad = ctx.createLinearGradient(0, top, 0, baseline);
        grad.addColorStop(0, alpha(s.color, 0.45));
        grad.addColorStop(1, alpha(s.color, 0.02));
        // A borda direita da área se dissolve em vez de terminar num corte reto.
        const fade = width * 0.1;
        const mask = ctx.createLinearGradient(x1 - fade, 0, x1, 0);
        mask.addColorStop(0, 'rgba(0,0,0,1)');
        mask.addColorStop(1, 'rgba(0,0,0,0)');
        const layer = new OffscreenCanvas(ctx.canvas.width, ctx.canvas.height);
        const lc = layer.getContext('2d');
        lc.setTransform(ctx.getTransform());
        lc.fillStyle = grad; lc.fill(area);
        lc.globalCompositeOperation = 'destination-in';
        lc.fillStyle = mask; lc.fillRect(0, 0, width, height);
        ctx.save(); ctx.resetTransform(); ctx.drawImage(layer, 0, 0); ctx.restore();
      }
      ctx.strokeStyle = s.color;
      ctx.lineWidth = 2.5;
      ctx.lineCap = 'round';
      ctx.setLineDash(s.dashed ? [5, 5] : []);
      ctx.stroke(path);
      ctx.setLineDash([]);
      ctx.restore();
    });

    // Ponto do "agora" na ponta da curva, quando ela já terminou de se desenhar.
    const live = visible[0]?.points.at(-1);
    if (liveEnd && live && progress > 0.98 && scrub == null) {
      const color = visible[0].color;
      const cx = x(live[0]);
      const cy = y(live[1]);
      ctx.fillStyle = alpha(color, 0.25); ctx.beginPath(); ctx.arc(cx, cy, 9, 0, 7); ctx.fill();
      ctx.fillStyle = cssColor('container-high'); ctx.beginPath(); ctx.arc(cx, cy, 5.5, 0, 7); ctx.fill();
      ctx.fillStyle = color; ctx.beginPath(); ctx.arc(cx, cy, 4, 0, 7); ctx.fill();
    }

    if (scrub != null) {
      const px = x(scrub);
      ctx.strokeStyle = alpha(cssColor('on-bg'), 0.5);
      ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(px, top - 4); ctx.lineTo(px, baseline); ctx.stroke();
      const texts = [];
      for (const s of visible) {
        let nearest = null;
        for (const p of s.points) if (!nearest || Math.abs(p[0] - scrub) < Math.abs(nearest[0] - scrub)) nearest = p;
        if (!nearest || Math.abs(nearest[0] - scrub) > 20) continue;
        ctx.fillStyle = cssColor('container-high'); ctx.beginPath(); ctx.arc(x(nearest[0]), y(nearest[1]), 6, 0, 7); ctx.fill();
        ctx.fillStyle = s.color; ctx.beginPath(); ctx.arc(x(nearest[0]), y(nearest[1]), 4, 0, 7); ctx.fill();
        texts.push((visible.length > 1 ? `${s.label} ` : '') + format(nearest[1]));
      }
      if (texts.length) {
        const hhmm = `${String(Math.floor(scrub / 60)).padStart(2, '0')}:${String(scrub % 60).padStart(2, '0')}`;
        pillLabel(ctx, `${hhmm}  ${texts.join('  ·  ')}`, px, width);
      }
    }
  });

  const minuteAt = (e) => {
    const rect = canvas.getBoundingClientRect();
    const fraction = Math.min(1, Math.max(0, (e.clientX - rect.left - plotLeft) / (rect.width - plotLeft)));
    return Math.round(startMinute + fraction * (endMinute - startMinute));
  };
  canvas.addEventListener('pointermove', (e) => {
    if (e.pointerType === 'mouse' || e.buttons) { scrub = minuteAt(e); canvas.paint(); }
  });
  canvas.addEventListener('pointerdown', (e) => { scrub = minuteAt(e); canvas.paint(); });
  const clear = () => { scrub = null; canvas.paint(); };
  canvas.addEventListener('pointerleave', clear);
  canvas.addEventListener('pointerup', (e) => { if (e.pointerType !== 'mouse') clear(); });
  canvas.addEventListener('pointercancel', clear);

  // Só anima quando os dados mudam (redesenhos da tela com os mesmos dados não repetem a animação).
  const signature = JSON.stringify(visible.map((s) => s.points));
  if (key && animatedCharts.get(key) === signature) {
    progress = 1;
  } else {
    if (key) animatedCharts.set(key, signature);
    tween(1400, (p) => { progress = p; canvas.paint(); });
  }
  return canvas;
}

/**
 * Barras que crescem em sequência (mola, uma após a outra). Clicar numa barra
 * destaca e mostra o valor; a linha tracejada marca a média.
 * `points`: [{ label, kwh }]
 */
export function barChart({ key, points, format, height = 200, labelEvery = 1 }) {
  const signature = JSON.stringify(points);
  const repeat = key && animatedCharts.get(key) === signature;
  if (key) animatedCharts.set(key, signature);
  const growth = points.map(() => (repeat ? 1 : 0));
  let selected = null;

  const canvas = chartCanvas(height, (ctx, width) => {
    if (!points.length) return;
    const font = getComputedStyle(document.body).fontFamily;
    const primary = cssColor('primary');
    const labelSpace = 20;
    const top = 30;
    const chartH = height - labelSpace - top;
    const maxV = Math.max(...points.map((p) => p.kwh), 0.1) * 1.08;
    const slot = width / points.length;
    const barW = Math.min(slot * 0.62, 28);
    const nonZero = points.filter((p) => p.kwh > 0);
    const avg = nonZero.length ? nonZero.reduce((a, p) => a + p.kwh, 0) / nonZero.length : null;

    ctx.font = `10px ${font}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    points.forEach((p, i) => {
      const hgt = Math.max(chartH * (p.kwh / maxV) * growth[i], p.kwh > 0 ? 3 : 0);
      const left = slot * i + (slot - barW) / 2;
      const isSel = selected === i;
      const dim = selected != null && !isSel;
      if (hgt > 0) {
        const grad = ctx.createLinearGradient(0, top + chartH - hgt, 0, top + chartH);
        grad.addColorStop(0, alpha(primary, dim ? 0.35 : 1));
        grad.addColorStop(1, alpha(primary, dim ? 0.18 : 0.55));
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.roundRect(left, top + chartH - hgt, barW, hgt, Math.min(barW / 2.6, hgt / 2));
        ctx.fill();
      }
      if (i % labelEvery === 0 || isSel) {
        ctx.fillStyle = cssColor(isSel ? 'on-bg' : 'on-surface-variant');
        ctx.fillText(p.label, left + barW / 2, top + chartH + 5);
      }
    });
    ctx.textAlign = 'left';

    if (avg != null) {
      const ay = top + chartH * (1 - avg / maxV);
      ctx.strokeStyle = alpha(cssColor('on-surface-variant'), 0.6);
      ctx.lineWidth = 1;
      ctx.setLineDash([4, 4]);
      ctx.beginPath(); ctx.moveTo(0, ay); ctx.lineTo(width, ay); ctx.stroke();
      ctx.setLineDash([]);
    }

    if (selected != null) {
      const p = points[selected];
      pillLabel(ctx, `${p.label}: ${format(p.kwh)}`, slot * selected + slot / 2, width);
    }
  });

  canvas.style.cursor = 'pointer';
  canvas.addEventListener('click', (e) => {
    const rect = canvas.getBoundingClientRect();
    const index = Math.min(points.length - 1, Math.max(0, Math.floor(((e.clientX - rect.left) / rect.width) * points.length)));
    selected = selected === index ? null : index;
    canvas.paint();
  });

  if (!repeat) {
    points.forEach((_, i) => {
      setTimeout(() => tween(700, (p) => { growth[i] = p; canvas.paint(); }, springOut), 18 * i);
    });
  }
  return canvas;
}

export function spinner(light = false) {
  return h('div', { class: `spinner ${light ? 'light' : ''}` });
}
