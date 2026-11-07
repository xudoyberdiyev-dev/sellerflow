import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { IconButton } from './Button'

export type ToastTone = 'success' | 'danger' | 'info' | 'warning'
const ICONS: Record<ToastTone, string> = { info: 'info', success: 'circle-check', warning: 'triangle-alert', danger: 'circle-alert' }

export interface ToastProps { tone?: ToastTone; title: ReactNode; description?: ReactNode; audit?: boolean | string; onClose?: () => void }
export type ToastItem = ToastProps & { id: string }

/** Vaqtinchalik natija xabari. `audit` — «Auditga yozildi» izohi. */
export function Toast({ tone = 'success', title, description, audit, onClose }: ToastProps) {
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
  )
}

export function ToastStack({ toasts = [], onDismiss }: { toasts: ToastItem[]; onDismiss?: (id: string) => void }) {
  return (
    <div className="sf-toast-stack" aria-live="polite">
      {toasts.map(({ id, ...t }) => <Toast key={id} {...t} onClose={() => onDismiss?.(id)} />)}
    </div>
  )
}
