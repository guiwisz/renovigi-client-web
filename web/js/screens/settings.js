import * as repo from '../repository.js';
import { closeSettings } from '../app.js';
import { store } from '../store.js';
import { errorMessage } from '../errors.js';
import { fmt, parseDecimal } from '../format.js';
import { h, icon, appear, card, sectionTitle, segmented, confirmDialog, spinner } from '../ui.js';

const THEMES = ['SYSTEM', 'LIGHT', 'DARK'];

/** Estado dos campos: sobrevive aos redesenhos (ex.: quando chega um snapshot novo). */
const form = {
  name: null, nameFor: null, nameStatus: {},
  tariffText: null, tariffFor: null, tariffStatus: {}, editingTariff: false, bill: '', kwh: '',
  investmentText: null, investmentFor: null, investmentSaved: false,
};

let redraw = () => {};

/** Grava algo e mostra o resultado embaixo do campo. */
async function save(statusKey, success, block) {
  form[statusKey] = { saving: true };
  redraw();
  try {
    await block();
    form[statusKey] = { message: success };
  } catch (e) {
    form[statusKey] = { message: errorMessage(e), failed: true };
  }
  redraw();
}

function statusText(status) {
  if (!status?.message) return h('div');
  return h('div', { class: `body ${status.failed ? 'error-text' : 'ok-text'}` }, status.message);
}

function saveRow(enabled, status, onSave, label = 'Salvar') {
  return h('div', { class: 'save-row' },
    h('div', { class: 'grow' }, statusText(status)),
    h('button', { class: 'btn pressable', disabled: !enabled, onclick: onSave }, status?.saving ? spinner(true) : label));
}

/** Campo de texto que atualiza o estado sem redesenhar a tela inteira. */
function input(value, onInput, props = {}) {
  const el = h('input', { class: 'input', value, ...props });
  el.addEventListener('input', () => onInput(el.value));
  return el;
}

function labeled(label, el) {
  return h('label', { style: { display: 'block' } }, h('span', { class: 'input-label' }, label), el);
}

export function renderSettings(ctx) {
  redraw = ctx.redraw;
  const s = ctx.snapshot;
  const demo = s?.isDemo === true;
  let i = 0;

  return h('div', { class: 'content' },
    h('div', { class: 'hstack', style: { paddingTop: '16px' } },
      h('div', { class: 'headline grow' }, 'Ajustes'),
      h('button', { class: 'icon-btn pressable', 'aria-label': 'Fechar', onclick: closeSettings }, icon('close'))),

    appear(sectionTitle('Usina'), i++),
    appear(nameCard(s?.plantName ?? ''), i++),

    appear(sectionTitle('Tarifa de energia'), i++),
    appear(tariffCard(ctx.tariff, demo), i++),

    appear(sectionTitle('Retorno do investimento'), i++),
    appear(investmentCard(ctx.investment), i++),

    appear(sectionTitle('Aparência'), i++),
    appear(card(null, segmented(['Sistema', 'Claro', 'Escuro'], Math.max(0, THEMES.indexOf(store.theme)), (n) => {
      store.theme = THEMES[n];
    })), i++),

    appear(sectionTitle(demo ? 'Demonstração' : 'Conta'), i++),
    demo ? null : appear(sessionCard(), i++),
    appear(signOutCard(ctx.signOut, demo), i++),

    h('div', { class: 'label muted', style: { padding: '16px 0' } },
      'Dados da nuvem ShineMonitor (a mesma do RenoClient). Leituras do inversor a cada 5 minutos.'));
}

function nameCard(current) {
  if (form.nameFor !== current) {
    form.nameFor = current;
    form.name = current === 'PLANT' ? '' : current;
  }
  const canSave = () => form.name.trim() !== '' && form.name.trim() !== current && !form.nameStatus.saving;
  const row = () => saveRow(canSave(), form.nameStatus, () => save('nameStatus', 'Nome atualizado', () => repo.setPlantName(form.name)));
  let rowEl = row();
  return card(null,
    h('div', { class: 'title' }, 'Nome da usina'),
    h('div', { class: 'body muted' }, 'Aparece no app e no portal.'),
    h('div', { style: { marginTop: '10px' } }, input(form.name, (v) => {
      form.name = v.slice(0, 64);
      const next = row();
      rowEl.replaceWith(next);
      rowEl = next;
    }, { placeholder: 'Ex.: Casa', maxLength: 64 })),
    rowEl);
}

function tariffCard(current, demo) {
  if (form.tariffFor !== current) {
    form.tariffFor = current;
    form.tariffText = fmt.number(current, 4);
  }
  const calculated = () => {
    const bill = parseDecimal(form.bill);
    const kwh = parseDecimal(form.kwh);
    return bill != null && kwh != null && kwh > 0 ? bill / kwh : null;
  };
  const canSave = () => {
    const v = parseDecimal(form.tariffText);
    return v != null && v >= 0.1 && v <= 5 && Math.abs(v - current) > 0.00005 && !form.tariffStatus.saving;
  };

  const tariffInput = input(form.tariffText, (v) => { form.tariffText = v; refreshDynamic(); }, { inputMode: 'decimal' });
  const calcSlot = h('div');
  let rowEl = h('div');
  const refreshDynamic = () => {
    const value = calculated();
    calcSlot.replaceChildren(value == null ? '' : h('button', {
      class: 'link pressable', style: { textAlign: 'left' },
      onclick: () => { form.tariffText = fmt.number(value, 4); tariffInput.value = form.tariffText; refreshDynamic(); },
    }, `≈ ${fmt.money(value)}/kWh · usar este valor`));
    const next = saveRow(canSave(), form.tariffStatus, () => {
      const value = parseDecimal(form.tariffText);
      confirmDialog('Gravar nova tarifa?',
        `A tarifa da usina passará de ${fmt.money(current)} para ${fmt.money(value)} por kWh. ` +
          'Isso muda a economia calculada aqui, no RenoClient e no portal.',
        'Gravar', () => {
          form.editingTariff = false;
          save('tariffStatus', 'Tarifa gravada na usina', () => repo.setTariff(value));
        });
    }, 'Gravar na usina');
    rowEl.replaceWith(next);
    rowEl = next;
  };

  const editor = h('div', { class: `collapsible ${form.editingTariff ? 'open' : ''}` },
    h('div', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px', paddingTop: '12px' } },
      labeled('R$ por kWh', tariffInput),
      h('div', { class: 'divider' }),
      h('div', { class: 'title' }, 'Calcular pela conta de luz'),
      h('div', { class: 'body muted' },
        'Divida o valor total da conta pelo consumo em kWh. Use uma conta com pouca compensação de créditos ' +
          'para chegar perto do preço real do kWh com impostos.'),
      h('div', { class: 'row2', style: { gap: '10px' } },
        labeled('Total (R$)', input(form.bill, (v) => { form.bill = v; refreshDynamic(); }, { inputMode: 'decimal' })),
        labeled('Consumo (kWh)', input(form.kwh, (v) => { form.kwh = v; refreshDynamic(); }, { inputMode: 'decimal' }))),
      calcSlot,
      h('div', { class: 'body muted' },
        'Sistemas instalados a partir de 2023 (Lei 14.300) pagam uma parte do "Fio B" sobre a energia ' +
          'injetada, então a economia real por kWh fica um pouco abaixo da tarifa cheia. Se quiser ser ' +
          'conservador, use um valor ~10% menor.'),
      rowEl)));
  refreshDynamic();

  const toggle = h('button', {
    class: 'link pressable', style: { marginTop: '12px' },
    onclick: () => {
      form.editingTariff = !form.editingTariff;
      editor.classList.toggle('open', form.editingTariff);
      toggle.textContent = form.editingTariff ? 'Cancelar' : 'Alterar tarifa';
      statusOutside.hidden = form.editingTariff;
    },
  }, form.editingTariff ? 'Cancelar' : 'Alterar tarifa');
  const statusOutside = h('div', { hidden: form.editingTariff }, statusText(form.tariffStatus));

  return card(null,
    h('div', { class: 'title' }, `${fmt.money(current)} por kWh`),
    h('div', { class: 'body muted' }, demo
      ? 'Na demonstração, a tarifa fica só neste navegador.'
      : 'Gravada na usina (servidor) — vale também no RenoClient e no portal.'),
    h('div', null, toggle),
    editor,
    statusOutside);
}

function investmentCard(current) {
  if (form.investmentFor !== current) {
    form.investmentFor = current;
    form.investmentText = fmt.number(current, 2);
  }
  const canSave = () => {
    const v = parseDecimal(form.investmentText);
    return v != null && v > 0 && Math.abs(v - current) > 0.005;
  };
  const row = () => saveRow(canSave(), { message: form.investmentSaved ? 'Salvo' : null }, () => {
    const v = parseDecimal(form.investmentText);
    if (v == null) return;
    form.investmentSaved = true;
    store.investment = v;
  });
  let rowEl = row();
  return card(null,
    h('div', { class: 'title' }, 'Valor investido no sistema'),
    h('div', { class: 'body muted' }, 'Se foi financiado, o mais realista é usar o total pago com juros. Fica salvo só neste navegador.'),
    h('div', { style: { marginTop: '10px' } }, labeled('R$', input(form.investmentText, (v) => {
      form.investmentText = v;
      form.investmentSaved = false;
      const next = row();
      rowEl.replaceWith(next);
      rowEl = next;
    }, { inputMode: 'decimal' }))),
    rowEl);
}

/** Mostra se o hash da senha está guardado e permite apagá-lo. */
function sessionCard() {
  const creds = store.credentials;
  if (!creds) return null;
  const until = creds.expiresAtMillis ? new Date(creds.expiresAtMillis).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : null;
  if (!creds.passwordSha1) {
    return card(null,
      h('div', { class: 'title' }, 'Sessão sem senha salva'),
      h('div', { class: 'body muted' },
        `Só o token da sessão fica neste navegador${until ? `, válido até ${until}` : ''}. Depois disso, é preciso entrar de novo.`));
  }
  return card({
    onclick: () => confirmDialog('Esquecer a senha salva?',
      'O hash da senha será apagado deste navegador. Você continua conectado até a sessão atual expirar e depois precisará entrar de novo.',
      'Esquecer', () => { repo.forgetPassword(); redraw(); }),
  },
  h('div', { class: 'title' }, 'Esquecer senha salva'),
  h('div', { class: 'body muted' },
    '"Manter conectado" está ativo: um hash da senha fica neste navegador para renovar a sessão sozinho. Quem tiver acesso a este navegador pode usá-lo para entrar na sua conta.'));
}

function signOutCard(onSignOut, demo) {
  // A casca volta para o login e limpa a rota.
  const leave = onSignOut;
  return card({
    onclick: () => (demo ? leave()
      : confirmDialog('Sair da conta?', 'Você vai precisar entrar de novo com usuário e senha.', 'Sair', leave)),
  },
  h('div', { class: 'title error-text' }, demo ? 'Sair da demonstração' : 'Sair da conta'),
  h('div', { class: 'body muted' }, demo
    ? 'Volta para a tela de login para entrar com a sua conta.'
    : 'Remove as credenciais salvas neste navegador.'));
}
