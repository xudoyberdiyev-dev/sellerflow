import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Native select styled as a control. options: [{value, label, disabled?}] */
export function Select({ options = [], placeholder, size = 'md', invalid, disabled, icon, className = '', style, ...rest }) {
  return (
    <div className={`sf-input sf-input--select ${size === 'sm' ? 'sf-input--sm' : ''} ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={style}>
      {icon && <Icon name={icon} size={16} />}
      <select disabled={disabled} aria-invalid={invalid || undefined} {...rest}>
        {placeholder && <option value="" disabled>{placeholder}</option>}
        {options.map((o) => <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>)}
      </select>
      <Icon name="chevron-down" size={16} />
    </div>
  );
}
