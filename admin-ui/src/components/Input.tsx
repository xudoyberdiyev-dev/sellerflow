import { cloneElement, isValidElement, useId, type CSSProperties, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Icon } from './Icon'

export interface FieldProps {
  label?: ReactNode; optional?: boolean; hint?: ReactNode; error?: ReactNode; id?: string
  children: ReactElement | ((a: { id: string; describedBy?: string; invalid: boolean }) => ReactNode)
}

/** Label + boshqaruv + izoh/xato. Yaratilgan id render-prop yoki yagona child'ga uzatiladi. */
export function Field({ label, optional, hint, error, children, id: idProp }: FieldProps) {
  const auto = useId()
  const id = idProp || auto
  const describedBy = error ? id + '-err' : hint ? id + '-hint' : undefined
  let control: ReactNode
  if (typeof children === 'function') control = children({ id, describedBy, invalid: !!error })
  else if (isValidElement(children)) {
    const el = children as ReactElement<{ id?: string; 'aria-describedby'?: string; invalid?: boolean }>
    control = cloneElement(el, { id, 'aria-describedby': describedBy, invalid: el.props.invalid ?? !!error })
  } else control = children
  return (
    <div className="sf-field">
      {label && <label className="sf-field__label" htmlFor={id}>{label}{optional && <small>(ixtiyoriy)</small>}</label>}
      {control}
      {error ? <div className="sf-field__error" id={id + '-err'} role="alert"><Icon name="circle-alert" size={14} />{error}</div>
        : hint ? <div className="sf-field__hint" id={id + '-hint'}>{hint}</div> : null}
    </div>
  )
}

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> { icon?: string; suffix?: ReactNode; size?: 'sm' | 'md'; invalid?: boolean }

export function Input({ icon, suffix, size = 'md', invalid, disabled, className = '', style, ...rest }: InputProps) {
  return (
    <div className={`sf-input ${size === 'sm' ? 'sf-input--sm' : ''} ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={style}>
      {icon && <Icon name={icon} size={16} />}
      <input disabled={disabled} aria-invalid={invalid || undefined} {...rest} />
      {suffix && <span className="sf-input__suffix">{suffix}</span>}
    </div>
  )
}

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> { invalid?: boolean }

export function Textarea({ invalid, disabled, rows = 3, maxLength, value, className = '', style, ...rest }: TextareaProps) {
  const wrap: CSSProperties = { flexDirection: 'column', ...style }
  return (
    <div className={`sf-input sf-input--textarea ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={wrap}>
      <textarea rows={rows} disabled={disabled} aria-invalid={invalid || undefined} maxLength={maxLength} value={value} {...rest} style={{ width: '100%' }} />
      {maxLength && <span className="sf-input__suffix" style={{ alignSelf: 'flex-end' }}>{String(value ?? '').length} / {maxLength}</span>}
    </div>
  )
}

export interface SelectOption { value: string; label: string; disabled?: boolean }
export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> { options: SelectOption[]; placeholder?: string; size?: 'sm' | 'md'; invalid?: boolean; icon?: string }

/** Boshqaruv ko'rinishidagi native select. */
export function Select({ options = [], placeholder, size = 'md', invalid, disabled, icon, className = '', style, ...rest }: SelectProps) {
  return (
    <div className={`sf-input sf-input--select ${size === 'sm' ? 'sf-input--sm' : ''} ${className}`} data-invalid={invalid || undefined} data-disabled={disabled || undefined} style={style}>
      {icon && <Icon name={icon} size={16} />}
      <select disabled={disabled} aria-invalid={invalid || undefined} {...rest}>
        {placeholder && <option value="" disabled>{placeholder}</option>}
        {options.map((o) => <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>)}
      </select>
      <Icon name="chevron-down" size={16} />
    </div>
  )
}
