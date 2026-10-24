import * as repo from '../repository.js';
import { dayProgress, sunTimes } from '../domain.js';
import { errorMessage } from '../errors.js';
import { h, icon, appear, spinner } from '../ui.js';
import { createScene } from '../scene.js';

/** Portal oficial, onde existe a recuperação de senha da conta ShineMonitor. */
const PORTAL_URL = 'https://www.shinemonitor.com/';

export function renderLogin() {
  // Antes do login não sabemos onde fica a usina: céu pelo relógio do navegador (nascer 6h, pôr 18h).
  const now = new Date();
  const progress = dayProgress(now.getHours() * 60 + now.getMinutes(), sunTimes(1, null, null, 0));
  const scene = createScene();
  scene.update({ dayProgress: progress, powerFraction: progress >= 0.27 && progress <= 0.73 ? 0.6 : 0, cloudiness: 0.2 });

  let loading = false;
  const username = h('input', { type: 'text', placeholder: 'Usuário ou e-mail', autocomplete: 'username', autocapitalize: 'none', spellcheck: false });
  const password = h('input', { type: 'password', placeholder: 'Sua senha', autocomplete: 'current-password' });
  const remember = h('input', { type: 'checkbox' });
  const notice = repo.takeLoginNotice();
  const error = h('div', { class: 'body error-text', hidden: !notice }, notice ?? '');
  const submit = h('button', { type: 'submit', class: 'submit pressable', disabled: true });
  const toggle = h('button', { type: 'button', class: 'muted', style: { fontSize: '22px', display: 'flex' }, 'aria-label': 'Mostrar senha' }, icon('eye'));

  const sync = () => {
    submit.disabled = loading || !username.value.trim() || !password.value;
    submit.classList.toggle('loading', loading);
    submit.replaceChildren(...(loading ? [spinner(true)] : ['Entrar', h('span', { class: 'arrow' }, icon('arrow'))]));
  };
  const showError = (message) => { error.textContent = message ?? ''; error.hidden = !message; };

  username.addEventListener('input', () => { showError(null); sync(); });
  password.addEventListener('input', () => { showError(null); sync(); });
  toggle.addEventListener('click', () => {
    const show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.replaceChildren(icon(show ? 'eyeOff' : 'eye'));
    toggle.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
  });

  async function run(action) {
    loading = true;
    showError(null);
    sync();
    try {
      await action();
    } catch (e) {
      loading = false;
      showError(errorMessage(e));
      sync();
    }
  }

  const form = h('form', {
    class: 'login-card', autocomplete: 'on',
    onsubmit: (e) => {
      e.preventDefault();
      if (submit.disabled) return;
      // A casca troca para o painel assim que as credenciais são gravadas.
      run(() => repo.signIn(username.value, password.value, remember.checked));
    },
  },
  h('div', { class: 'label muted' }, 'Use a mesma conta do RenoClient'),
  h('label', { class: 'field' }, icon('user'), username),
  h('label', { class: 'field' }, icon('lock'), password, toggle),
  h('div', { class: 'remember-row' },
    h('label', { class: 'remember' }, remember, 'Manter conectado'),
    h('a', { class: 'link', href: PORTAL_URL, target: '_blank', rel: 'noopener' }, 'Esqueceu sua senha?')),
  h('div', { class: 'label muted' },
    'Com "Manter conectado", este navegador guarda um hash da senha para renovar a sessão sozinho. ',
    'Use só em computador seu.'),
  error,
  submit);
  sync();

  const demo = h('button', {
    type: 'button', class: 'link pressable', style: { padding: '6px 0' },
    onclick: () => { if (!loading) run(() => repo.startDemo()); },
  }, 'Ainda não tem conta? Ver demonstração');

  return h('div', { class: 'login' },
    h('div', { class: 'scene-box' }, scene.el),
    h('div', { class: 'login-body' },
      appear(h('h1', null, 'Bem-vindo'), 0),
      appear(h('p', { class: 'body-l muted', style: { margin: '6px 0 22px' } }, 'Acesse sua usina e acompanhe sua energia em tempo real.'), 1),
      appear(form, 2),
      h('div', { style: { height: '16px' } }),
      appear(demo, 3),
      appear(h('p', { class: 'label muted', style: { margin: '8px 0 0' } },
        'Seus dados ficam só neste navegador.'), 3)));
}
