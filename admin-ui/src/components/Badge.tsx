import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { CHANNELS, STATUS_TONES, type Tone } from './constants'

export type AccountStatus = 'Faol' | 'Ro‘yxatdan o‘tmoqda' | 'Bloklangan' | 'O‘chirish so‘ralgan' | 'Yopilgan'
export type AccessStatus = 'Kutilmoqda' | 'Ruxsat berilgan' | 'Rad etilgan' | 'Bekor qilingan' | 'Muddati tugagan'

export interface BadgeProps { tone?: Tone; dot?: boolean; icon?: string; children: ReactNode }

/** Kichik rangli yorliq. Doim matn bor — rang yagona signal emas. */
export function Badge({ tone = 'neutral', dot = true, icon, children }: BadgeProps) {
  return (
    <span className={`sf-badge sf-badge--${tone}`}>
      {icon ? <Icon name={icon} size={12} /> : dot ? <span className="sf-badge__dot" aria-hidden="true" /> : null}
      {children}
    </span>
  )
}

/** Akkaunt / kirish so'rovi holati. Rang o'zbekcha nomdan olinadi. */
export function StatusBadge({ status }: { status: AccountStatus | AccessStatus | string }) {
  return <Badge tone={STATUS_TONES[status] || 'neutral'}>{status}</Badge>
}

export interface TagProps { children: ReactNode; color?: string; icon?: string; onRemove?: () => void }

/** Neytral metadata yorlig'i (kanal, filtr). */
export function Tag({ children, color, onRemove, icon }: TagProps) {
  return (
    <span className="sf-tag">
      {color && <span className="sf-tag__swatch" style={{ background: color }} aria-hidden="true" />}
      {icon && <Icon name={icon} size={12} />}
      {children}
      {onRemove && <button type="button" className="sf-tag__x" aria-label={`${typeof children === 'string' ? children : ''} — olib tashlash`} onClick={onRemove}><Icon name="x" size={12} /></button>}
    </span>
  )
}

export function ChannelTag({ channel }: { channel: string }) {
  const c = CHANNELS.find((x) => x.id === channel || x.label === channel) || CHANNELS[6]
  return <Tag color={c.color}>{c.label}</Tag>
}
