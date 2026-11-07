import React, { useState, useEffect, useRef, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Kbd } from '../display/Tooltip.jsx';

/**
 * ⌘K / Ctrl+K command palette.
 * items: [{id, label, group, icon?, hint?, shortcut?, onSelect}]
 * bindShortcut: listens for ⌘K/Ctrl+K globally and calls onOpenChange(true).
 */
export function CommandPalette({ open, onOpenChange, items = [], placeholder = 'Sotuvchi, sahifa yoki amalni qidiring…', bindShortcut = true, contained }) {
  const [q, setQ] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef(null);
  const listId = useId();

  useEffect(() => {
    if (!bindShortcut) return;
    const h = (e) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); onOpenChange && onOpenChange(!open); } };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, bindShortcut, onOpenChange]);
  useEffect(() => { if (open) { setQ(''); setActive(0); setTimeout(() => inputRef.current?.focus(), 0); } }, [open]);
  useEffect(() => setActive(0), [q]);

  if (!open) return null;
  const filtered = items.filter((i) => (i.label + ' ' + (i.hint || '')).toLowerCase().includes(q.toLowerCase()));
  const groups = [...new Set(filtered.map((i) => i.group || ''))];
  const run = (it) => { onOpenChange && onOpenChange(false); it.onSelect && it.onSelect(); };
  const onKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, filtered.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    else if (e.key === 'Enter' && filtered[active]) { e.preventDefault(); run(filtered[active]); }
    else if (e.key === 'Escape') { e.preventDefault(); onOpenChange && onOpenChange(false); }
  };
  let idx = -1;
  return (
    <div className={`sf-overlay sf-overlay--top ${contained ? 'sf-overlay--contained' : ''}`} onMouseDown={(e) => e.target === e.currentTarget && onOpenChange(false)}>
      <div className="sf-cmd" role="dialog" aria-modal="true" aria-label="Buyruqlar paneli">
        <div className="sf-cmd__input">
          <Icon name="search" size={18} style={{ color: 'var(--text-tertiary)' }} />
          <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKey} placeholder={placeholder}
            role="combobox" aria-expanded="true" aria-controls={listId} aria-activedescendant={filtered[active] ? `${listId}-${active}` : undefined} />
          <Kbd>Esc</Kbd>
        </div>
        <div className="sf-cmd__list" role="listbox" id={listId}>
          {filtered.length === 0 && <div className="sf-listbox__empty">«{q}» bo‘yicha hech narsa topilmadi</div>}
          {groups.map((g) => (
            <div key={g} role="group" aria-label={g}>
              {g && <div className="sf-menu__label">{g}</div>}
              {filtered.filter((i) => (i.group || '') === g).map((it) => {
                idx++; const my = idx;
                return (
                  <div key={it.id || it.label} id={`${listId}-${my}`} role="option" aria-selected={my === active} data-active={my === active}
                    className="sf-option" onMouseEnter={() => setActive(my)} onClick={() => run(it)}>
                    {it.icon && <Icon name={it.icon} size={16} />}<span>{it.label}</span>
                    {(it.hint || it.shortcut) && <span className="sf-option__meta">{it.hint && <span className={it.hintMono ? 'sf-mono' : ''}>{it.hint}</span>}{it.shortcut && it.shortcut.map((k) => <Kbd key={k}>{k}</Kbd>)}</span>}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
        <div className="sf-cmd__foot">
          <span><Kbd>↑</Kbd><Kbd>↓</Kbd> tanlash</span><span><Kbd>↵</Kbd> ochish</span><span><Kbd>Esc</Kbd> yopish</span>
        </div>
      </div>
    </div>
  );
}
