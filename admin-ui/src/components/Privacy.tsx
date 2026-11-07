import { useEffect, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'
import { Button } from './Button'
import { Badge } from './Badge'
import { formatDuration } from './format'
import { useCountdown } from './useCountdown'

export interface CountdownChipProps { expiresAt?: Date | number | string | null; seconds?: number; warnAt?: number; onExpire?: () => void }

/** Kirish oynasi taymeri: «23:41 qoldi». Oxirgi `warnAt` soniyada ogohlantiruvchi rangda. */
export function CountdownChip({ expiresAt, seconds, warnAt = 300, onExpire }: CountdownChipProps) {
  const live = useCountdown(expiresAt)
  const left = seconds != null ? seconds : live ?? 0
  const expired = left <= 0
  const urgent = !expired && left <= warnAt
  const onExpireRef = useRef(onExpire)
  onExpireRef.current = onExpire
  const live1 = expiresAt != null
  useEffect(() => { if (expired && live1) onExpireRef.current?.() }, [expired, live1])
  return (
    <span className="sf-countdown" data-urgent={urgent || undefined} data-expired={expired || undefined} role="timer" aria-live={urgent ? 'polite' : 'off'}
      aria-label={expired ? 'Kirish muddati tugadi' : `Kirish muddati: ${formatDuration(left)} qoldi`}>
      <Icon name={expired ? 'timer-off' : urgent ? 'alarm-clock' : 'timer'} size={12} />
      {expired ? 'Muddati tugadi' : <><b>{formatDuration(left)}</b> qoldi</>}
    </span>
  )
}

export type GuardState = 'locked' | 'pending' | 'denied' | 'expired'
const STATES: Record<GuardState, { title: string; text: string; action: string; icon: string }> = {
  locked: { title: 'Tijoriy ma’lumot yashirin — sotuvchi roziligi kerak', text: 'Tannarx, foyda, narxlar va hisoblashlar sukut bo‘yicha ko‘rinmaydi. Ko‘rish uchun sotuvchiga Telegram bot orqali so‘rov yuboring. Ruxsat 30–60 daqiqaga, faqat o‘qish uchun beriladi.', action: 'Kirish so‘rash', icon: 'lock' },
  pending: { title: 'So‘rov yuborildi — sotuvchi javobi kutilmoqda', text: 'Sotuvchi Telegram botda «Ruxsat berish» tugmasini bossa, ma’lumot shu yerda faqat o‘qish rejimida ochiladi.', action: 'So‘rovni bekor qilish', icon: 'hourglass' },
  denied: { title: 'Sotuvchi so‘rovni rad etdi', text: 'Tijoriy ma’lumot yashirin qoladi. Zarurat bo‘lsa, sababni aniqroq yozib, yangi so‘rov yuboring.', action: 'Qayta so‘rash', icon: 'lock' },
  expired: { title: 'Kirish muddati tugadi', text: 'Ma’lumot yana yashirildi. Davom etish uchun sotuvchidan yangi rozilik oling.', action: 'Yangi so‘rov yuborish', icon: 'lock' },
}

/** Bitta himoyalangan maydon o'rnidagi yashirin qiymat. */
export function Redacted({ label = 'Yashirin' }: { label?: string }) {
  return <span className="sf-redacted" aria-label={`${label}: sotuvchi roziligi kerak`}><Icon name="lock" size={12} />{label}</span>
}

export interface PrivacyGuardProps {
  state?: GuardState; title?: ReactNode; description?: ReactNode; fields?: string[]; onAction?: () => void
  actionLabel?: ReactNode; canRequest?: boolean; inline?: boolean; loading?: boolean
}

/** Sotuvchi rozilik bermaguncha tijoriy ma'lumot o'rnini egallaydigan blok. canRequest=false — amal yashiriladi. */
export function PrivacyGuard({ state = 'locked', title, description, fields, onAction, actionLabel, canRequest = true, inline, loading }: PrivacyGuardProps) {
  const s = STATES[state] || STATES.locked
  const action = canRequest ? (onAction && (
    <Button variant={state === 'pending' ? 'secondary' : 'privacy'} icon={state === 'pending' ? 'x' : 'send'} onClick={onAction} loading={loading} size={inline ? 'sm' : 'md'}>
      {actionLabel || s.action}
    </Button>
  )) : <span style={{ font: 'var(--type-small)', color: 'var(--text-tertiary)' }}>So‘rov yuborish huquqi yo‘q</span>
  return (
    <div className={`sf-guard ${inline ? 'sf-guard--inline' : ''}`} role="region" aria-label="Himoyalangan ma’lumot">
      <div className="sf-guard__icon"><Icon name={s.icon} size={18} /></div>
      <div className="sf-guard__body">
        <p className="sf-guard__title">{title || s.title}</p>
        {!inline && <p className="sf-guard__text">{description || s.text}</p>}
        {fields && !inline && <div className="sf-guard__fields">{fields.map((f) => <Redacted key={f} label={f} />)}</div>}
        {state === 'pending' && !inline && <div><Badge tone="info">Kutilmoqda</Badge></div>}
        {!inline && action && <div style={{ marginTop: 8 }}>{action}</div>}
      </div>
      {inline && action}
    </div>
  )
}

export interface ReadOnlyBannerProps { sellerName?: ReactNode; expiresAt?: Date | number | string | null; seconds?: number; onEnd?: () => void; onExpire?: () => void; warnAt?: number }

/** Yopishqoq «Faqat ko'rish · auditga yozilmoqda · 23:41 qoldi» banneri. */
export function ReadOnlyBanner({ sellerName, expiresAt, seconds, onEnd, onExpire, warnAt = 300 }: ReadOnlyBannerProps) {
  const live = useCountdown(expiresAt)
  const left = seconds != null ? seconds : live ?? 0
  const urgent = left > 0 && left <= warnAt
  return (
    <div className="sf-readonly" role="status" data-urgent={urgent || undefined}>
      <Icon name="eye" size={16} />
      <div className="sf-readonly__items">
        <strong>Faqat ko‘rish</strong>
        <span className="sf-readonly__dot" aria-hidden="true" />
        <span className="sf-readonly__rec">Auditga yozilmoqda</span>
        {sellerName && <><span className="sf-readonly__dot" aria-hidden="true" /><span>Sotuvchi roziligi: {sellerName}</span></>}
      </div>
      <CountdownChip expiresAt={expiresAt} seconds={seconds} warnAt={warnAt} onExpire={onExpire} />
      {onEnd && <Button size="sm" variant="ghost" icon="log-out" onClick={onEnd}>Ko‘rishni yakunlash</Button>}
    </div>
  )
}

export interface TelegramButton { text: string; onClick?: () => void }
export interface TelegramPreviewProps { botName?: string; text: ReactNode; keyboard?: TelegramButton[][]; time?: string; caption?: string }

/** SellerFlow Telegram bot xabari ko'rinishi: matn + inline klaviatura. */
export function TelegramPreview({ botName = 'SellerFlow bot', text, keyboard = [], time = '14:32', caption = 'Telegram xabar ko‘rinishi' }: TelegramPreviewProps) {
  return (
    <div className="sf-tg" role="figure" aria-label={caption}>
      <div className="sf-tg__head"><Icon name="send" size={12} />{caption}</div>
      <div className="sf-tg__bubble">
        <span className="sf-tg__name">{botName}</span>
        {text}
        <span className="sf-tg__time sf-num">{time}</span>
      </div>
      {keyboard.length > 0 && (
        <div className="sf-tg__kb">
          {keyboard.map((row, i) => (
            <div key={i} className="sf-tg__kb-row">
              {row.map((b, j) => <button key={j} type="button" className="sf-tg__btn" onClick={b.onClick}>{b.text}</button>)}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
