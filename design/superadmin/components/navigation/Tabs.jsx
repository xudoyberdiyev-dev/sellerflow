import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Underlined tabs. items: [{value, label, count?, icon?, disabled?}] */
export function Tabs({ items = [], value, onChange, label }) {
  const onKey = (e) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    const en = items.filter((i) => !i.disabled);
    const i = en.findIndex((x) => x.value === value);
    const n = en[(i + (e.key === 'ArrowRight' ? 1 : -1) + en.length) % en.length];
    onChange && onChange(n.value);
    setTimeout(() => e.currentTarget?.querySelector(`[data-v="${n.value}"]`)?.focus());
  };
  return (
    <div className="sf-tabs" role="tablist" aria-label={label} onKeyDown={onKey}>
      {items.map((t) => (
        <button key={t.value} data-v={t.value} type="button" role="tab" className="sf-tab" aria-selected={t.value === value}
          aria-disabled={t.disabled || undefined} tabIndex={t.value === value ? 0 : -1} onClick={() => !t.disabled && onChange && onChange(t.value)}>
          {t.icon && <Icon name={t.icon} size={14} />}{t.label}{t.count != null && <span className="sf-tab__count">{t.count}</span>}
        </button>
      ))}
    </div>
  );
}

/** Location trail. items: [{label, href?, onClick?}] — last item is current page. */
export function Breadcrumbs({ items = [] }) {
  return (
    <nav aria-label="Joylashuv">
      <ol className="sf-crumbs">
        {items.map((it, i) => {
          const last = i === items.length - 1;
          return (
            <li key={i} style={{ display: 'contents' }}>
              {last ? <span aria-current="page">{it.label}</span> : <a href={it.href || '#'} onClick={it.onClick}>{it.label}</a>}
              {!last && <Icon name="chevron-right" size={12} />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
