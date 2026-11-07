import React from 'react';
import { Icon } from '../core/Icon.jsx';

const PRESETS = {
  empty: { icon: 'inbox', tone: '' },
  search: { icon: 'search-x', tone: '' },
  error: { icon: 'triangle-alert', tone: 'danger' },
  forbidden: { icon: 'shield-x', tone: 'danger' },
  privacy: { icon: 'lock', tone: 'privacy' },
};

/** Empty, no-results, error and 403 states. kind picks the icon/tone. */
export function EmptyState({ kind = 'empty', icon, title, description, actions, code }) {
  const p = PRESETS[kind] || PRESETS.empty;
  return (
    <div className="sf-empty" role={kind === 'error' ? 'alert' : undefined}>
      <div className={`sf-empty__icon ${p.tone ? 'sf-empty__icon--' + p.tone : ''}`}><Icon name={icon || p.icon} size={20} /></div>
      {code && <div className="sf-empty__code">{code}</div>}
      <h3 className="sf-empty__title">{title}</h3>
      {description && <p className="sf-empty__desc">{description}</p>}
      {actions && <div className="sf-empty__actions">{actions}</div>}
    </div>
  );
}
