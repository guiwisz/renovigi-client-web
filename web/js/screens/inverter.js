import { scaffold, refreshButton } from '../app.js';
import { efficiency, stringInUse, stringPowerW } from '../domain.js';
import { fmt } from '../format.js';
import { h, appear, card, cardLabel, animatedNumber, semiGauge } from '../ui.js';

export function renderInverter(ctx, { animate }) {
  const s = ctx.snapshot;
  const live = s?.inverter;
  const reading = fmt.readingTime(live?.readingAt);
  const subtitle = reading ? `Leitura das ${reading} · atualiza a cada ${Math.floor((s?.collector?.intervalSeconds ?? 300) / 60)} min` : null;

  const body = !s || !live
    ? [h('div', { class: 'body-l muted' }, 'Sem leitura do inversor ainda.')]
    : [
      appear(conversionCard(live), 0, animate),
      appear(stringsCard(live), 1, animate),
      appear(h('div', { class: 'row2' }, temperatureCard(live), frequencyCard(live)), 2, animate),
      appear(gridCard(live), 3, animate),
      appear(equipmentCard(s, live), 4, animate),
    ];
  return scaffold({ title: 'Inversor', subtitle, animate, actions: [refreshButton()], body });
}

/** Painéis (DC) → inversor → casa/rede (AC), com eficiência da conversão. */
export function conversionCard(live) {
  const active = (live.acPowerW ?? 0) > 10;
  const eff = efficiency(live);
  const power = (label, key, watts, end) => h('div', { style: { flex: 1, textAlign: end ? 'right' : 'left' } },
    h('div', { class: 'label muted' }, label),
    h('div', { class: 'headline-s' }, animatedNumber(key, (watts ?? 0) / 1000, (v) => `${fmt.kw(v)} kW`)));
  return card(null,
    cardLabel('Conversão', 'swap', 'var(--primary)'),
    h('div', { class: 'hstack', style: { marginTop: '14px' } },
      power('Painéis (DC)', 'inv.dc', live.dcPowerW, false),
      h('div', { style: { flex: 0.8, position: 'relative', height: '40px', display: 'grid', placeItems: 'center' } },
        h('div', { class: `flow-line ${active ? 'active' : ''}`, style: { position: 'absolute', left: 0, right: 0, top: '18px' } }),
        eff != null ? h('span', { class: 'pill', style: { position: 'relative' } }, fmt.percent(eff)) : null),
      power('Saída (AC)', 'inv.ac', live.acPowerW, true)),
    h('div', { class: 'body muted', style: { marginTop: '12px' } }, eff != null
      ? 'Eficiência do inversor: quanto da energia dos painéis vira energia utilizável.'
      : 'Sem geração no momento.'));
}

/** Cada entrada MPPT com barra de potência que cresce, tensão e corrente. */
export function stringsCard(live) {
  const used = live.strings.filter(stringInUse);
  const unused = live.strings.filter((x) => !stringInUse(x));
  const maxW = Math.max(1, ...used.map(stringPowerW));
  const rows = used.map((x, i) => {
    const bar = h('div');
    setTimeout(() => { bar.style.width = `${Math.min(1, stringPowerW(x) / maxW) * 100}%`; }, 50 + 120 * i);
    return h('div', { style: { marginTop: '14px' } },
      h('div', { class: 'hstack' }, h('div', { class: 'title grow' }, `MPPT ${x.index}`),
        h('div', { class: 'title accent' }, `${fmt.number(stringPowerW(x), 0)} W`)),
      h('div', { class: 'bar-track' }, bar),
      h('div', { class: 'body muted' }, `${fmt.number(x.voltage, 1)} V · ${fmt.number(x.current, 1)} A`));
  });
  let balance = null;
  if (used.length >= 2) {
    const a = stringPowerW(used[0]);
    const b = stringPowerW(used[1]);
    const diff = Math.max(a, b) > 200 ? Math.abs(a - b) / Math.max(a, b) : 0;
    balance = h('div', { class: 'body', style: { marginTop: '12px', color: diff > 0.25 ? 'var(--sun)' : 'var(--on-surface-variant)' } },
      diff > 0.25
        ? `As strings estão bem diferentes (${fmt.percent(diff)}). Pode ser sombra, sujeira ou orientação diferente dos painéis.`
        : 'Strings equilibradas.');
  }
  return card(null,
    cardLabel('Strings de painéis', 'panel', 'var(--sky)'),
    rows,
    balance,
    unused.length ? h('div', { class: 'body muted', style: { marginTop: '4px' } },
      `Entradas sem painéis: ${unused.map((x) => `MPPT ${x.index}`).join(', ')}`) : null);
}

export function temperatureCard(live) {
  const temp = live.temperatureC;
  if (temp == null) return h('div');
  const [label, color] = temp >= 80 ? ['Alta', 'var(--alert)'] : temp >= 70 ? ['Quente', 'var(--sun)'] : ['Normal', 'var(--leaf)'];
  return card(null,
    cardLabel('Temperatura', 'thermo', color),
    h('div', { style: { position: 'relative', marginTop: '10px' } },
      semiGauge(temp / 90),
      h('div', { style: { position: 'absolute', left: 0, right: 0, bottom: '-4px', textAlign: 'center' } },
        h('div', { class: 'headline-s' }, animatedNumber('inv.temp', temp, (v) => `${fmt.number(v, 0)}°`)),
        h('div', { class: 'label', style: { color } }, label))));
}

export function frequencyCard(live) {
  const freq = live.gridFrequency;
  if (freq == null) return h('div');
  return card(null,
    cardLabel('Frequência', 'plug', 'var(--sky)'),
    h('div', { class: 'headline-s', style: { marginTop: '18px' } }, animatedNumber('inv.freq', freq, (v) => `${fmt.number(v, 2)} Hz`)),
    h('div', { class: 'body muted' }, Math.abs(freq - 60) <= 0.5 ? 'Dentro do padrão (60 Hz)' : 'Fora do padrão'));
}

/** Tensão da rede com a faixa adequada da ANEEL para 220 V (202–231 V). */
export function gridCard(live) {
  const voltage = live.gridVoltage;
  if (voltage == null) return null;
  const extra = [
    live.gridCurrent != null ? `${fmt.number(live.gridCurrent, 1)} A` : null,
    live.apparentPowerKva != null ? `${fmt.number(live.apparentPowerKva, 2)} kVA` : null,
  ].filter(Boolean).join(' · ');
  const min = 190;
  const max = 245;
  const pos = (v) => `${Math.min(1, Math.max(0, (v - min) / (max - min))) * 100}%`;
  const knob = h('div', {
    style: {
      position: 'absolute', top: '0', left: '0', width: '18px', height: '18px', marginLeft: '-9px', borderRadius: '50%',
      background: 'var(--primary)', border: '3px solid var(--container-high)', transition: 'left 0.9s cubic-bezier(0.4,0,0.2,1)',
    },
  });
  requestAnimationFrame(() => requestAnimationFrame(() => { knob.style.left = pos(voltage); }));
  const band = h('div', { style: { position: 'relative', height: '18px', marginTop: '14px' } },
    h('div', { style: { position: 'absolute', top: '5px', left: 0, right: 0, height: '8px', borderRadius: '4px', background: 'var(--surface-variant)' } }),
    h('div', {
      style: {
        position: 'absolute', top: '5px', height: '8px', borderRadius: '4px', left: pos(202),
        width: `calc(${pos(231)} - ${pos(202)})`, background: 'color-mix(in srgb, var(--leaf) 70%, transparent)',
      },
    }),
    knob);
  let note = 'Dentro da faixa adequada da ANEEL para 220 V (202–231 V).';
  if (voltage < 202) note = 'Abaixo da faixa adequada. Se for frequente, o inversor pode desligar (alarme de tensão baixa).';
  else if (voltage > 231) note = 'Acima da faixa adequada. Tensão alta faz o inversor reduzir ou parar a injeção.';
  return card(null,
    cardLabel('Rede elétrica', 'plug', 'var(--primary)'),
    h('div', { class: 'hstack', style: { marginTop: '12px', alignItems: 'baseline' } },
      h('div', { class: 'headline-s grow' }, animatedNumber('inv.volt', voltage, (v) => `${fmt.number(v, 1)} V`)),
      h('div', { class: 'body muted' }, extra)),
    band,
    h('div', { class: 'body muted', style: { marginTop: '10px' } }, note));
}

export function equipmentCard(s, live) {
  const install = s.installDate?.slice(0, 10);
  const rows = [
    ['Firmware do inversor', [live.firmwareHmi && `HMI ${live.firmwareHmi}`, live.firmwareDsp && `DSP ${live.firmwareDsp}`].filter(Boolean).join(' · ')],
    live.gridStandard && ['Padrão de rede', live.gridStandard],
    s.collector?.firmware && ['Firmware do coletor Wi-Fi', s.collector.firmware],
    s.collector?.intervalSeconds && ['Envio de dados', `a cada ${Math.floor(s.collector.intervalSeconds / 60)} min`],
    s.collector && ['Comunicação', s.collector.online ? 'Online' : 'Offline'],
    s.runningMinutes != null && ['Operando sem falhas há', fmt.days(s.runningMinutes)],
    live.busVoltage != null && ['Barramento DC', `${fmt.number(live.busVoltage, 1)} V`],
    s.nominalPowerKw != null && ['Potência instalada', `${fmt.number(s.nominalPowerKw, 1)} kWp`],
    install && ['Instalado em', /^\d{4}-\d{2}-\d{2}$/.test(install) ? fmt.fullDate(install) : install],
  ].filter(Boolean);
  return card(null,
    cardLabel('Equipamentos', 'info', 'var(--on-surface-variant)'),
    rows.flatMap(([label, value], i) => [i > 0 ? h('div', { class: 'divider' }) : null,
      h('div', { class: 'kv' }, h('div', null, label), h('div', null, value))]));
}
