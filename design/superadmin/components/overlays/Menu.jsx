import React, { useState, useRef, useEffect, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Kbd } from '../display/Tooltip.jsx';

function useOutside(open, ref, close) {
  useEffect(() => {
    if (!open) return;
    const h = (e) => ref.current && !ref.current.contains(e.target) && close();
    const k = (e) => e.key === 'Escape' && close();
    document.addEventListener('mousedown', h); document.addEventListener('keydown', k);
    return () => { document.removeEventListener('mousedown', h); document.removeEventListener('keydown', k); };
  }, [open]);
}

/** Anchored floating panel. trigger is a render-prop receiving {onClick, aria-expanded, aria-haspopup}. */
export function Popover({ trigger, children, align = 'start', width, defaultOpen = false, label }) {
  const [open, setOpen] = useState(defaultOpen);
  const ref = useRef(null);
  const id = useId();
  useOutside(open, ref, () => setOpen(false));
  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex' }}>
      {trigger({ onClick: () => setOpen((o) => !o), 'aria-expanded': open, 'aria-haspopup': 'dialog', 'aria-controls': id })}
      {open && <div id={id} role="dialog" aria-label={label} className="sf-pop" style={{ top: 'calc(100% + 6px)', [align === 'end' ? 'right' : 'left']: 0, width, padding: 12 }}>{children}</div>}
    </div>
  );
}

/**
 * Action menu. items: [{label, icon?, onSelect, danger?, disabled?, shortcut?, keepOpen?} | {separator:true} | {heading}]
 */
export function DropdownMenu({ trigger, items = [], align = 'start', label, defaultOpen = false, width = 220 }) {
  const [open, setOpen] = useState(defaultOpen);
  const [active, setActive] = useState(-1);
  const ref = useRef(null);
  const id = useId();
  useOutside(open, ref, () => setOpen(false));
  const actionable = items.map((it, i) => (!it.separator && !it.heading && !it.disabled ? i : -1)).filter((i) => i >= 0);
  useEffect(() => { if (open) { setActive(actionable[0] ?? -1); setTimeout(() => ref.current?.querySelector('[role=menu]')?.focus(), 0); } }, [open]);
  const onKey = (e) => {
    const p = actionable.indexOf(active);
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(actionable[(p + 1) % actionable.length]); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(actionable[(p - 1 + actionable.length) % actionable.length]); }
    else if ((e.key === 'Enter' || e.key === ' ') && items[active]) { e.preventDefault(); select(items[active]); }
    else if (e.key === 'Tab') setOpen(false);
  };
  const select = (it) => { if (it.disabled) return; it.onSelect && it.onSelect(); if (!it.keepOpen) setOpen(false); };
  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex' }}>
      {trigger({ onClick: () => setOpen((o) => !o), 'aria-expanded': open, 'aria-haspopup': 'menu', 'aria-controls': id })}
      {open && (
        <div id={id} role="menu" aria-label={label} tabIndex={-1} className="sf-pop" onKeyDown={onKey}
          aria-activedescendant={active >= 0 ? `${id}-${active}` : undefined}
          style={{ top: 'calc(100% + 4px)', [align === 'end' ? 'right' : 'left']: 0, width, outline: 'none' }}>
          {items.map((it, i) => it.separator ? <div key={i} className="sf-menu__sep" role="separator" /> :
            it.heading ? <div key={i} className="sf-menu__label">{it.heading}</div> : (
              <div key={i} id={`${id}-${i}`} role="menuitem" aria-disabled={it.disabled || undefined} data-active={i === active}
                className={`sf-option ${it.danger ? 'sf-option--danger' : ''}`} onMouseEnter={() => setActive(i)} onClick={() => select(it)}>
                {it.icon && <Icon name={it.icon} size={16} />}<span>{it.label}</span>
                {it.shortcut && <span className="sf-option__meta">{it.shortcut.map((k) => <Kbd key={k}>{k}</Kbd>)}</span>}
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
