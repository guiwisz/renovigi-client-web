/*
 * Estado persistido no navegador (localStorage): credenciais, último snapshot
 * da usina e preferências. Outras abas são avisadas pelo evento `storage`
 * do navegador.
 */

const PREFIX = 'solar.';

/** Usada só até o servidor responder com a tarifa configurada na usina. */
export const DEFAULT_TARIFF = 0.93;
export const DEFAULT_INVESTMENT = 26_000;

const listeners = new Set();

function read(key) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw == null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
}

function write(key, value) {
  try {
    if (value == null) localStorage.removeItem(PREFIX + key);
    else localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch { /* armazenamento indisponível (aba anônima): segue só em memória */ }
  listeners.forEach((fn) => fn(key));
}

window.addEventListener('storage', (e) => {
  if (e.key?.startsWith(PREFIX)) listeners.forEach((fn) => fn(e.key.slice(PREFIX.length)));
});

export const store = {
  /** Chamado com o nome da chave alterada (nesta aba ou em outra). */
  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  /**
   * Usuário, SHA-1 da senha (nunca a senha em si), usina e token atual.
   * O SHA-1 basta para refazer o login quando o token expira.
   */
  get credentials() { return read('credentials'); },
  set credentials(value) { write('credentials', value); },

  get snapshot() { return read('snapshot'); },
  set snapshot(value) { write('snapshot', value); },

  get tariff() { return this.snapshot?.tariffPerKwh ?? DEFAULT_TARIFF; },

  /** Valor investido no sistema (R$), para o retorno do investimento. */
  get investment() { return read('investment') ?? DEFAULT_INVESTMENT; },
  set investment(value) { write('investment', value); },

  /** SYSTEM, LIGHT ou DARK. */
  get theme() { return read('theme') ?? 'SYSTEM'; },
  set theme(value) { write('theme', value); },

  /** Cards da aba Agora: [{ id, on }] na ordem escolhida (ver layout.js). */
  get nowLayout() { return read('nowLayout'); },
  set nowLayout(value) { write('nowLayout', value); },

  /** Modo demonstração: dados fictícios, sem conta nem rede. */
  get demo() { return read('demo') === true; },
  setDemo(enabled) {
    if (enabled) write('demo', true);
    else { write('demo', null); write('demoTariff', null); write('demoName', null); }
  },
  get demoTariff() { return read('demoTariff'); },
  set demoTariff(value) { write('demoTariff', value); },
  get demoName() { return read('demoName'); },
  set demoName(value) { write('demoName', value); },

  get loggedIn() { return this.credentials != null || this.demo; },
};
