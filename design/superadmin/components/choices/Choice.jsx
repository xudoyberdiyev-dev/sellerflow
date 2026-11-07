import React, { useEffect, useRef } from 'react';
import { Icon } from '../core/Icon.jsx';

function Choice({ kind, label, description, invalid, disabled, indeterminate, className = '', ...rest }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.indeterminate = !!indeterminate; }, [indeterminate]);
  return (
    <label className={`sf-check ${kind === 'radio' ? 'sf-check--radio' : ''} ${className}`} data-disabled={disabled || undefined} data-invalid={invalid || undefined}>
      <input ref={ref} type={kind === 'radio' ? 'radio' : 'checkbox'} role={kind === 'switch' ? 'switch' : undefined} disabled={disabled} aria-invalid={invalid || undefined} {...rest} />
      {kind === 'switch' ? <span className="sf-switch__track" aria-hidden="true" /> :
        <span className="sf-check__box" aria-hidden="true">
          {kind === 'checkbox' && (indeterminate ? <Icon name="minus" size={12} /> : rest.checked ? <Icon name="check" size={12} /> : null)}
        </span>}
      {(label || description) && <span className="sf-check__text">{label}{description && <span className="sf-check__desc">{description}</span>}</span>}
    </label>
  );
}

export function Checkbox(props) { return <Choice kind="checkbox" {...props} />; }
export function Radio(props) { return <Choice kind="radio" {...props} />; }
export function Switch(props) { return <Choice kind="switch" {...props} />; }
