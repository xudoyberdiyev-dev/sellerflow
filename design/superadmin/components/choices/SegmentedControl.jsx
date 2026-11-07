import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Mutually-exclusive 2–5 option switcher. options: [{value, label, icon?, disabled?}] */
export function SegmentedControl({ options = [], value, onChange, label, size }) {
  const idx = options.findIndex((o) => o.value === value);
  const onKey = (e) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    e.preventDefault();
    const dir = e.key === 'ArrowRight' ? 1 : -1;
    let n = idx;
    for (let k = 0; k < options.length; k++) { n = (n + dir + options.length) % options.length; if (!options[n].disabled) break; }
    onChange && onChange(options[n].value);
    e.currentTarget.querySelectorAll('[role=radio]')[n]?.focus();
  };
  return (
    <div className="sf-seg" role="radiogroup" aria-label={label} onKeyDown={onKey}>
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" className="sf-seg__item" aria-checked={o.value === value}
          aria-disabled={o.disabled || undefined} tabIndex={o.value === value ? 0 : -1}
          onClick={() => !o.disabled && onChange && onChange(o.value)} style={size === 'sm' ? { height: 24, padding: '0 10px' } : undefined}>
          {o.icon && <Icon name={o.icon} size={14} />}{o.label}
        </button>
      ))}
    </div>
  );
}
