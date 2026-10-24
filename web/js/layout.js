/*
 * Cards que podem aparecer na aba Agora, na ordem padrão. Os do Inversor
 * começam desligados; a pessoa escolhe quais mostrar e em que ordem.
 */
import { store } from './store.js';

export const NOW_ITEMS = [
  { id: 'economy', label: 'Economia e CO₂ hoje' },
  { id: 'curve', label: 'Curva do dia' },
  { id: 'goal', label: 'Comparado a um dia típico' },
  { id: 'payback', label: 'Retorno do investimento' },
  { id: 'alarm', label: 'Último alerta', hint: 'Só aparece se houve alerta nos últimos 7 dias' },
  { id: 'periods', label: 'Este mês e este ano' },
  { id: 'impact', label: 'Impacto desde a instalação' },
  { id: 'conversion', label: 'Conversão (painéis → saída)', inverter: true },
  { id: 'strings', label: 'Strings de painéis', inverter: true },
  { id: 'temperature', label: 'Temperatura do inversor', inverter: true },
  { id: 'frequency', label: 'Frequência da rede', inverter: true },
  { id: 'grid', label: 'Rede elétrica (tensão)', inverter: true },
  { id: 'equipment', label: 'Equipamentos', inverter: true },
];

export const itemInfo = (id) => NOW_ITEMS.find((item) => item.id === id);

export const defaultNowLayout = () => NOW_ITEMS.map((item) => ({ id: item.id, on: !item.inverter }));

/** Layout salvo, sem itens desconhecidos e com os novos acrescentados no fim. */
export function nowLayout() {
  const saved = (store.nowLayout ?? []).filter((x) => itemInfo(x.id));
  if (!saved.length) return defaultNowLayout();
  const missing = defaultNowLayout().filter((x) => !saved.some((y) => y.id === x.id));
  return [...saved, ...missing];
}
