import React, { useState, useCallback, useEffect } from 'react';
import { Icon } from '../core/Icon.jsx';
import { IconButton } from '../buttons/Button.jsx';

const ICONS = { info: 'info', success: 'circle-check', warning: 'triangle-alert', danger: 'circle-alert' };

/** Transient result notice. `audit` adds the «Auditga yozildi» footnote for logged actions. */
export function Toast({ tone = 'success', title, description, audit, onClose }) {
  return (
    <div className={`sf-toast sf-toast--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone]} size={16} />
      <div className="sf-toast__body">
        <div className="sf-toast__title">{title}</div>
        {description && <div className="sf-toast__text">{description}</div>}
        {audit && <div className="sf-toast__audit"><Icon name="scroll-text" size={12} />{audit === true ? 'Auditga yozildi' : audit}</div>}
      </div>
      {onClose && <IconButton size="sm" icon="x" label="Bildirishnomani yopish" onClick={onClose} style={{ margin: '-4px -4px 0 0' }} />}
    </div>
  );
}

export function ToastStack({ toasts = [], onDismiss }) {
  return (
    <div className="sf-toast-stack" aria-live="polite">
      {toasts.map((t) => <Toast key={t.id} {...t} onClose={() => onDismiss && onDismiss(t.id)} />)}
    </div>
  );
}

/** Minimal toast state hook. push({tone,title,description,audit}) → auto-dismiss after `ttl` ms. */
export function useToasts(ttl = 5000) {
  const [toasts, setToasts] = useState([]);
  const dismiss = useCallback((id) => setToasts((ts) => ts.filter((t) => t.id !== id)), []);
  const push = useCallback((t) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((ts) => [...ts, { ...t, id }]);
    if (ttl) setTimeout(() => dismiss(id), ttl);
  }, [ttl, dismiss]);
  return { toasts, push, dismiss };
}
