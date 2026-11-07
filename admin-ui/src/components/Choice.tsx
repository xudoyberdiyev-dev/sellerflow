import { useEffect, useRef, type InputHTMLAttributes, type ReactNode } from 'react'
import { Icon } from './Icon'

export interface ChoiceProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode; description?: ReactNode; invalid?: boolean; indeterminate?: boolean
}

function Choice({ kind, label, description, invalid, disabled, indeterminate, className = '', ...rest }: ChoiceProps & { kind: 'checkbox' | 'radio' | 'switch' }) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => { if (ref.current) ref.current.indeterminate = !!indeterminate }, [indeterminate])
  return (
    <label className={`sf-check ${kind === 'radio' ? 'sf-check--radio' : ''} ${className}`} data-disabled={disabled || undefined} data-invalid={invalid || undefined}>
      <input ref={ref} type={kind === 'radio' ? 'radio' : 'checkbox'} role={kind === 'switch' ? 'switch' : undefined} disabled={disabled} aria-invalid={invalid || undefined} {...rest} />
      {kind === 'switch' ? <span className="sf-switch__track" aria-hidden="true" /> :
        <span className="sf-check__box" aria-hidden="true">
          {kind === 'checkbox' && (indeterminate ? <Icon name="minus" size={12} /> : rest.checked ? <Icon name="check" size={12} /> : null)}
        </span>}
      {(label || description) && <span className="sf-check__text">{label}{description && <span className="sf-check__desc">{description}</span>}</span>}
    </label>
  )
}

export function Checkbox(props: ChoiceProps) { return <Choice kind="checkbox" {...props} /> }
export function Radio(props: ChoiceProps) { return <Choice kind="radio" {...props} /> }
export function Switch(props: ChoiceProps) { return <Choice kind="switch" {...props} /> }
