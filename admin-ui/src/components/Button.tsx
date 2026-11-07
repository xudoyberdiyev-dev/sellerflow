import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Icon } from './Icon'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'privacy'
  size?: 'sm' | 'md'
  icon?: string
  iconRight?: string
  loading?: boolean
  children?: ReactNode
}

/** Fe'l bilan boshlanadigan amal tugmasi. */
export function Button({ variant = 'secondary', size = 'md', icon, iconRight, loading = false, disabled = false, type = 'button', children, className = '', ...rest }: ButtonProps) {
  const cls = ['sf-btn', `sf-btn--${variant}`, size === 'sm' && 'sf-btn--sm', className].filter(Boolean).join(' ')
  const iconSize = size === 'sm' ? 14 : 16
  return (
    <button type={type} className={cls} disabled={disabled} data-loading={loading || undefined} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="sf-spinner" aria-hidden="true" />}
      {icon && <Icon name={icon} size={iconSize} />}
      {children != null && <span>{children}</span>}
      {iconRight && <Icon name={iconRight} size={iconSize} />}
    </button>
  )
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: string
  /** Majburiy — aria-label va title bo'ladi. */
  label: string
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md'
  loading?: boolean
}

/** Faqat ikonkali kvadrat tugma. */
export function IconButton({ icon, label, variant = 'ghost', size = 'md', loading, disabled, className = '', ...rest }: IconButtonProps) {
  const cls = ['sf-btn', `sf-btn--${variant}`, 'sf-btn--icon', size === 'sm' && 'sf-btn--sm', className].filter(Boolean).join(' ')
  return (
    <button type="button" className={cls} aria-label={label} title={label} disabled={disabled} data-loading={loading || undefined} {...rest}>
      {loading && <span className="sf-spinner" aria-hidden="true" />}
      <Icon name={icon} size={size === 'sm' ? 14 : 16} />
    </button>
  )
}
