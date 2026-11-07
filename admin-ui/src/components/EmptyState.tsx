import type { ReactNode } from 'react'
import { Icon } from './Icon'

type Kind = 'empty' | 'search' | 'error' | 'forbidden' | 'privacy'
const PRESETS: Record<Kind, { icon: string; tone: string }> = {
  empty: { icon: 'inbox', tone: '' },
  search: { icon: 'search-x', tone: '' },
  error: { icon: 'triangle-alert', tone: 'danger' },
  forbidden: { icon: 'shield-x', tone: 'danger' },
  privacy: { icon: 'lock', tone: 'privacy' },
}

export interface EmptyStateProps { kind?: Kind; icon?: string; title: ReactNode; description?: ReactNode; actions?: ReactNode; code?: string }

/** Bo'sh, natija yo'q, xato va 403 holatlari. */
export function EmptyState({ kind = 'empty', icon, title, description, actions, code }: EmptyStateProps) {
  const p = PRESETS[kind] || PRESETS.empty
  return (
    <div className="sf-empty" role={kind === 'error' ? 'alert' : undefined}>
      <div className={`sf-empty__icon ${p.tone ? 'sf-empty__icon--' + p.tone : ''}`}><Icon name={icon || p.icon} size={20} /></div>
      {code && <div className="sf-empty__code">{code}</div>}
      <h3 className="sf-empty__title">{title}</h3>
      {description && <p className="sf-empty__desc">{description}</p>}
      {actions && <div className="sf-empty__actions">{actions}</div>}
    </div>
  )
}
