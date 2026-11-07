import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { IconButton } from '../buttons/Button.jsx';

const ICONS = { info: 'info', success: 'circle-check', warning: 'triangle-alert', danger: 'circle-alert', privacy: 'lock' };

/** In-content message tied to a section or form. tone: info | success | warning | danger | privacy */
export function InlineAlert({ tone = 'info', title, children, actions, onClose }) {
  return (
    <div className={`sf-alert sf-alert--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone]} size={16} />
      <div className="sf-alert__body">
        {title && <div className="sf-alert__title">{title}</div>}
        {children && <div className="sf-alert__text">{children}</div>}
        {actions && <div className="sf-alert__actions">{actions}</div>}
      </div>
      {onClose && <IconButton size="sm" icon="x" label="Yopish" onClick={onClose} style={{ margin: '-4px -6px 0 0' }} />}
    </div>
  );
}

/** Full-width page-top banner for system-level notices. */
export function Banner({ tone = 'info', children, action, onClose }) {
  return (
    <div className={`sf-banner sf-banner--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone] || 'info'} size={16} />
      <div className="sf-banner__text">{children}</div>
      {action}
      {onClose && <IconButton size="sm" icon="x" label="Yopish" onClick={onClose} style={{ color: 'inherit' }} />}
    </div>
  );
}
