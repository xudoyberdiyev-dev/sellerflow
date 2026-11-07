import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Verb-first action button. variant: primary | secondary | ghost | danger | privacy. */
export function Button({ variant = 'secondary', size = 'md', icon, iconRight, loading = false, disabled = false, type = 'button', children, className = '', ...rest }) {
  const cls = ['sf-btn', `sf-btn--${variant}`, size === 'sm' && 'sf-btn--sm', className].filter(Boolean).join(' ');
  const iconSize = size === 'sm' ? 14 : 16;
  return (
    <button type={type} className={cls} disabled={disabled} data-loading={loading || undefined} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="sf-spinner" aria-hidden="true" />}
      {icon && <Icon name={icon} size={iconSize} />}
      {children != null && <span>{children}</span>}
      {iconRight && <Icon name={iconRight} size={iconSize} />}
    </button>
  );
}

/** Square icon-only button. `label` is required — it becomes aria-label. */
export function IconButton({ icon, label, variant = 'ghost', size = 'md', loading, disabled, className = '', ...rest }) {
  const cls = ['sf-btn', `sf-btn--${variant}`, 'sf-btn--icon', size === 'sm' && 'sf-btn--sm', className].filter(Boolean).join(' ');
  return (
    <button type="button" className={cls} aria-label={label} title={label} disabled={disabled} data-loading={loading || undefined} {...rest}>
      {loading && <span className="sf-spinner" aria-hidden="true" />}
      <Icon name={icon} size={size === 'sm' ? 14 : 16} />
    </button>
  );
}
