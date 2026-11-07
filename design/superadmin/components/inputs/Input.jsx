import React, { useId } from 'react';
import { Icon } from '../core/Icon.jsx';

/** Label + control + hint/error wrapper. Passes generated id to a render-prop child or clones single child. */
export function Field({ label, optional, hint, error, children, id: idProp }) {
  const auto = useId();
  const id = idProp || auto;
  const describedBy = error ? id + '-err' : hint ? id + '-hint' : undefined;
  const control = typeof children === 'function' ? children({ id, describedBy, invalid: !!error }) :
    React.isValidElement(children) ? React.cloneElement(children, { id, 'aria-describedby': describedBy, invalid: children.props.invalid ?? !!error }) : children;
  return (
    <div className="sf-field">
      {label && <label className="sf-field__label" htmlFor={id}>{label}{optional && <small>(ixtiyoriy)</small>}</label>}
      {control}
      {error ? <div className="sf-field__error" id={id + '-err'} role="alert"><Icon name="circle-alert" size={14} />{error}</div>
        : hint ? <div className="sf-field__hint" id={id + '-hint'}>{hint}</div> : null}
    </div>
  );
}

export function Input({ icon, suffix, size = 'md', invalid, disabled, className = '', style, ...rest }) {
  return (
    <div className={`sf-input ${size === 'sm' ? 'sf-input--sm' : ''} ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={style}>
      {icon && <Icon name={icon} size={16} />}
      <input disabled={disabled} aria-invalid={invalid || undefined} {...rest} />
      {suffix && <span className="sf-input__suffix">{suffix}</span>}
    </div>
  );
}

export function Textarea({ invalid, disabled, rows = 3, maxLength, value, className = '', style, ...rest }) {
  return (
    <div className={`sf-input sf-input--textarea ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={{ flexDirection: 'column', ...style }}>
      <textarea rows={rows} disabled={disabled} aria-invalid={invalid || undefined} maxLength={maxLength} value={value} {...rest} style={{ width: '100%' }} />
      {maxLength && <span className="sf-input__suffix" style={{ alignSelf: 'flex-end' }}>{(value || '').length} / {maxLength}</span>}
    </div>
  );
}
