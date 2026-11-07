import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Logo } from '../core/Logo.jsx';
import { Tooltip } from '../display/Tooltip.jsx';

/**
 * Grouped, collapsible side navigation.
 * groups: [{label, items: [{id, label, icon, count?, roles?}]}]
 */
export function Sidebar({ groups = [], active, onNavigate, collapsed = false, onToggleCollapsed, footer, role }) {
  const [closed, setClosed] = useState({});
  return (
    <aside className="sf-sidebar" data-collapsed={collapsed || undefined} aria-label="Asosiy navigatsiya">
      <div className="sf-sidebar__head"><Logo variant={collapsed ? 'mark' : 'full'} size={28} /></div>
      <nav className="sf-sidebar__nav">
        {groups.map((g) => {
          const items = g.items.filter((it) => !it.roles || !role || it.roles.includes(role));
          if (!items.length) return null;
          const isClosed = closed[g.label] && !collapsed;
          return (
            <div key={g.label} className="sf-sidebar__group">
              <button type="button" className="sf-sidebar__group-label" aria-expanded={!isClosed} onClick={() => setClosed((c) => ({ ...c, [g.label]: !c[g.label] }))}>
                {g.label}<Icon name={isClosed ? 'chevron-right' : 'chevron-down'} size={12} />
              </button>
              {!isClosed && items.map((it) => {
                const btn = (
                  <button key={it.id} type="button" className="sf-nav-item" aria-current={it.id === active ? 'page' : undefined}
                    aria-label={collapsed ? it.label : undefined} onClick={() => onNavigate && onNavigate(it.id)}>
                    <Icon name={it.icon} size={16} />
                    <span className="sf-nav-item__label">{it.label}</span>
                    {it.count != null && <span className="sf-nav-item__count">{it.count}</span>}
                  </button>
                );
                return collapsed ? <Tooltip key={it.id} content={it.label}>{btn}</Tooltip> : btn;
              })}
            </div>
          );
        })}
      </nav>
      <div className="sf-sidebar__foot">
        {footer}
        {onToggleCollapsed && (
          <button type="button" className="sf-nav-item" onClick={onToggleCollapsed} aria-label={collapsed ? 'Menyuni yoyish' : 'Menyuni yig‘ish'}>
            <Icon name={collapsed ? 'panel-left-open' : 'panel-left-close'} size={16} />
            <span className="sf-nav-item__label">Menyuni yig‘ish</span>
          </button>
        )}
      </div>
    </aside>
  );
}
