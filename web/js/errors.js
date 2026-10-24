import { NetworkError, ShineError } from './api.js';

/** Mensagem amigável para uma falha. */
export function errorMessage(e) {
  console.warn('Falha exibida ao usuário', e);
  if (e instanceof ShineError) return e.isAuth ? 'Usuário ou senha incorretos' : `O servidor recusou a solicitação (${e.description})`;
  if (e instanceof NetworkError) return 'Sem conexão com o servidor de monitoramento';
  return 'Algo deu errado. Tente novamente.';
}
