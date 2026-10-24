/*
 * Cena da usina: céu do ciclo do dia (Lottie posicionado no horário real),
 * nuvens (mais densas quando a geração está abaixo do esperado), ondas do sol
 * indo às placas e energia correndo pelos cabos até a casa e a rede. Sem
 * geração (noite), o fluxo se inverte: a casa consome da rede.
 *
 * As animações em lottie/ usam uma prancheta 400x340 (DY = céu extra acima da paisagem).
 */
import { h, tween } from './ui.js';

const WIDTH = 400;
const DY = 80;
const PANEL = { x: 248, y: 130 + DY };

/** Posição do sol para um progresso do ciclo; null se abaixo do horizonte. */
function sunAt(progress) {
  const u = (progress - 0.25) / 0.5;
  if (u < 0 || u > 1) return null;
  return { x: 30 + 340 * u, y: DY + 228 - 188 * Math.sin(Math.PI * u) };
}

function load(container, name, opts) {
  return window.lottie.loadAnimation({
    container, renderer: 'svg', path: `lottie/${name}.json`,
    rendererSettings: { preserveAspectRatio: 'xMidYMid slice' }, ...opts,
  });
}

/**
 * @param fade se verdadeiro, a base da cena se dissolve na cor de fundo da tela.
 * Devolve { el, update({ dayProgress, powerFraction, cloudiness }) }.
 */
export function createScene({ fade = true } = {}) {
  const skyEl = h('div');
  const cloudsEl = h('div', { class: 'clouds', style: { opacity: 0 } });
  const waves = h('canvas');
  const flowEl = h('div', { class: 'layer', style: { opacity: 0 } });
  const importEl = h('div', { class: 'layer', style: { opacity: 0 } });
  const el = h('div', { class: 'scene' }, skyEl, cloudsEl, waves, flowEl, importEl, fade ? h('div', { class: 'fade' }) : null);

  const sky = load(skyEl, 'sky_cycle', { loop: false, autoplay: false });
  load(cloudsEl, 'clouds', { loop: true, autoplay: true });
  const flow = load(flowEl, 'energy_flow', { loop: true, autoplay: false });
  const gridImport = load(importEl, 'energy_import', { loop: true, autoplay: false });

  // Ao aparecer, "acelera" o dia desde a madrugada até agora.
  let progress = 0.02;
  let skyReady = false;
  let stopTween = null;
  let waveIntensity = 0;

  const showSky = () => {
    if (skyReady) sky.goToAndStop(Math.min(sky.totalFrames - 1, progress * sky.totalFrames), true);
  };
  sky.addEventListener('DOMLoaded', () => { skyReady = true; showSky(); });

  function update({ dayProgress, powerFraction, cloudiness }) {
    const from = progress;
    stopTween?.();
    stopTween = tween(from < 0.05 ? 2600 : 900, (p) => {
      progress = from + (dayProgress - from) * p;
      showSky();
    });

    const generating = powerFraction > 0.005;
    flowEl.style.opacity = generating ? 1 : 0;
    importEl.style.opacity = generating ? 0 : 0.9;
    if (generating) {
      flow.setSpeed(0.5 + 2.5 * Math.min(1, Math.max(0, powerFraction)));
      flow.play();
      gridImport.pause();
    } else {
      // Sem geração a casa consome da rede: pulsos azuis do poste para a casa.
      gridImport.setSpeed(0.6);
      gridImport.play();
      flow.pause();
    }
    const daylight = Math.min(1, Math.max(0, 1 - Math.abs(dayProgress - 0.5) * 4));
    cloudsEl.style.opacity = 0.15 + daylight * (0.35 + 0.6 * cloudiness);
    // Com qualquer geração as ondas ficam bem visíveis; crescem com a potência.
    waveIntensity = generating ? Math.min(1, 0.55 + powerFraction * 0.6) : 0;
  }

  /** "Ondinhas" que saem do sol e viajam até as placas. */
  function drawWaves(now) {
    // Fora da tela: confere de vez em quando em vez de desenhar a cada quadro.
    if (!el.isConnected) { setTimeout(() => requestAnimationFrame(drawWaves), 500); return; }
    const w = waves.clientWidth;
    const hgt = waves.clientHeight;
    const dpr = window.devicePixelRatio || 1;
    if (waves.width !== Math.round(w * dpr)) { waves.width = Math.round(w * dpr); waves.height = Math.round(hgt * dpr); }
    const ctx = waves.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, hgt);
    const sun = sunAt(progress);
    // Sol baixo (atrás dos morros) quase não manda ondas.
    const elevation = Math.min(1, Math.max(0, Math.sin(Math.PI * ((progress - 0.25) / 0.5))));
    const strength = waveIntensity * Math.min(1, Math.max(0, (elevation - 0.15) / 0.35));
    if (sun && strength > 0.02 && w) {
      // A cena preenche a largura; com "slice" a prancheta é centralizada na vertical.
      const scale = Math.max(w / WIDTH, hgt / 340);
      const offX = (w - WIDTH * scale) / 2;
      const offY = (hgt - 340 * scale) / 2;
      const from = { x: offX + sun.x * scale, y: offY + sun.y * scale };
      const to = { x: offX + PANEL.x * scale, y: offY + PANEL.y * scale };
      const angle = Math.atan2(to.y - from.y, to.x - from.x);
      const phase = (now % 1800) / 1800;
      for (let i = 0; i < 4; i++) {
        const t = (phase + i / 4) % 1;
        if (t < 0.18) continue; // nasce já fora do disco do sol
        const cx = from.x + (to.x - from.x) * t;
        const cy = from.y + (to.y - from.y) * t;
        const radius = (12 + 12 * t) * scale;
        ctx.strokeStyle = `rgba(255, 224, 138, ${Math.sin(Math.PI * t) * 0.85 * strength})`;
        ctx.lineWidth = 3 * scale;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.arc(cx, cy, radius, angle - (40 * Math.PI) / 180, angle + (40 * Math.PI) / 180);
        ctx.stroke();
      }
    }
    requestAnimationFrame(drawWaves);
  }
  requestAnimationFrame(drawWaves);

  return { el, update };
}
