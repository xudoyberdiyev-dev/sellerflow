import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { IconButton } from './Button'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger' | 'privacy'
const ICONS: Record<AlertTone, string> = { info: 'info', success: 'circle-check', warning: 'triangle-alert', danger: 'circle-alert', privacy: 'lock' }

export interface InlineAlertProps { tone?: AlertTone; title?: ReactNode; children?: ReactNode; actions?: ReactNode; onClose?: () => void }

/** Bo'lim yoki formaga bog'liq xabar. */
export function InlineAlert({ tone = 'info', title, children, actions, onClose }: InlineAlertProps) {
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
  )
}

export interface BannerProps { tone?: Exclude<AlertTone, 'privacy'>; children: ReactNode; action?: ReactNode; onClose?: () => void }

/** Sahifa tepasidagi tizim darajasidagi xabar. */
export function Banner({ tone = 'info', children, action, onClose }: BannerProps) {
  return (
    <div className={`sf-banner sf-banner--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone] || 'info'} size={16} />
      <div className="sf-banner__text">{children}</div>
      {action}
      {onClose && <IconButton size="sm" icon="x" label="Yopish" onClick={onClose} style={{ color: 'inherit' }} />}
    </div>
  )
}
