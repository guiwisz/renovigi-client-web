/*
 * Personalizar a aba Agora: quais cards aparecem e em que ordem (inclusive os do Inversor). Cada mudança é salva na hora e a
 * tela atrás já se redesenha, servindo de pré-visualização.
 */
import { store } from '../store.js';
import { NOW_ITEMS, itemInfo, nowLayout } from '../layout.js';
import { h, icon } from '../ui.js';

export function openCustomize() {
  const list = h('div', { class: 'cz-list' });
  const close = () => {
    document.removeEventListener('keydown', onKey);
    backdrop.remove();
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);

  const save = (layout) => { store.nowLayout = layout; drawList(); };
  let dragFrom = null;

  function drawList() {
    const layout = nowLayout();
    list.replaceChildren(...layout.map((item, i) => {
      const info = itemInfo(item.id);
      const move = (to) => {
        const next = [...layout];
        next.splice(to, 0, next.splice(i, 1)[0]);
        save(next);
      };
      const check = h('input', { type: 'checkbox', checked: item.on, 'aria-label': info.label });
      check.addEventListener('change', () => save(layout.map((x, j) => (j === i ? { ...x, on: check.checked } : x))));
      // Arrastar (mouse) ou setas (toque) para mudar a posição.
      return h('div', {
        class: `cz-row ${item.on ? '' : 'off'}`, draggable: true,
        ondragstart: (e) => { dragFrom = i; e.dataTransfer.effectAllowed = 'move'; e.currentTarget.classList.add('dragging'); },
        ondragend: (e) => e.currentTarget.classList.remove('dragging'),
        ondragover: (e) => { e.preventDefault(); e.currentTarget.classList.add('over'); },
        ondragleave: (e) => e.currentTarget.classList.remove('over'),
        ondrop: (e) => {
          e.preventDefault();
          if (dragFrom != null && dragFrom !== i) {
            const next = [...layout];
            next.splice(i, 0, next.splice(dragFrom, 1)[0]);
            save(next);
          }
          dragFrom = null;
        },
      },
      h('span', { class: 'cz-grip muted' }, icon('grip')),
      h('label', { class: 'cz-label' }, check,
        h('span', null, info.label, info.inverter ? h('span', { class: 'cz-tag' }, 'Inversor') : null,
          info.hint ? h('span', { class: 'label muted cz-hint' }, info.hint) : null)),
      h('button', { class: 'cz-move', 'aria-label': 'Subir', disabled: i === 0, onclick: () => move(i - 1) }, icon('up')),
      h('button', { class: 'cz-move', 'aria-label': 'Descer', disabled: i === layout.length - 1, onclick: () => move(i + 1) }, icon('down')));
    }));
  }

  drawList();
  const backdrop = h('div', { class: 'modal-backdrop', onclick: (e) => { if (e.target === backdrop) close(); } },
    h('div', { class: 'modal customize', role: 'dialog' },
      h('div', { class: 'hstack' }, h('h2', { class: 'grow' }, 'Personalizar Agora'),
        h('button', { class: 'icon-btn pressable', 'aria-label': 'Fechar', onclick: close }, icon('close'))),
      h('div', { class: 'body muted' }, `Marque o que aparece e arraste (ou use as setas) para mudar a ordem. ${NOW_ITEMS.filter((x) => x.inverter).length} cards vêm da aba Inversor.`),
      list,
      h('div', { class: 'buttons' },
        h('button', { onclick: () => { store.nowLayout = null; drawList(); } }, 'Restaurar padrão'),
        h('button', { onclick: close }, 'Pronto'))));
  document.body.append(backdrop);
}
