// D-6: holat, kanal va rol yorliqlari (API kodi → o'zbekcha matn va ton).
import type { Tone } from '../components'

export const ACCOUNT_STATUS: Record<string, { label: string; tone: Tone }> = {
  ONBOARDING: { label: 'Ro‘yxatdan o‘tmoqda', tone: 'info' },
  ACTIVE: { label: 'Faol', tone: 'success' },
  SUSPENDED: { label: 'Bloklangan', tone: 'warning' },
  DELETION_REQUESTED: { label: 'O‘chirish so‘ralgan', tone: 'orange' },
  CLOSED: { label: 'Yopilgan', tone: 'neutral' },
}

export const GRANT_STATUS: Record<string, { label: string; tone: Tone }> = {
  REQUESTED: { label: 'Kutilmoqda', tone: 'info' },
  APPROVED: { label: 'Ruxsat berilgan', tone: 'success' },
  DECLINED: { label: 'Rad etilgan', tone: 'danger' },
  REVOKED: { label: 'Bekor qilingan', tone: 'danger' },
  EXPIRED: { label: 'Muddati tugagan', tone: 'neutral' },
}

export const ADMIN_STATUS: Record<string, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Faol', tone: 'success' },
  DISABLED: { label: 'O‘chirilgan', tone: 'neutral' },
  LOCKED: { label: 'Qulflangan', tone: 'warning' },
}

/** API kanal kodi → dizayn CHANNELS id. */
export const CHANNEL_ID: Record<string, string> = {
  UZUM: 'uzum', WILDBERRIES: 'wb', OZON: 'ozon', INSTAGRAM: 'instagram',
  TELEGRAM_SALES: 'telegram', OFFLINE_STORE: 'offline', OTHER: 'other',
}
export const CHANNEL_OPTIONS = [
  { value: 'UZUM', label: 'Uzum Market' }, { value: 'WILDBERRIES', label: 'Wildberries' }, { value: 'OZON', label: 'Ozon' },
  { value: 'INSTAGRAM', label: 'Instagram' }, { value: 'TELEGRAM_SALES', label: 'Telegram savdo' },
  { value: 'OFFLINE_STORE', label: 'Offline do‘kon' }, { value: 'OTHER', label: 'Boshqa' },
]
export const channelId = (c?: string | null) => (c ? CHANNEL_ID[c] ?? 'other' : 'other')

export const ROLE_LABEL: Record<string, string> = { SUPERADMIN: 'Superadmin', SUPPORT: 'Support' }

/** Sotuvchi nomi: biznes nomi → ko'rinadigan ism → «Sotuvchi <ID>». Username/telefon yig'ilmaydi. */
export function sellerName(s: { businessName?: string | null; displayName?: string | null; telegramUserId: number | string }) {
  return s.businessName || s.displayName || `Sotuvchi ${s.telegramUserId}`
}


const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
export const today = () => ymd(new Date())

/** PeriodSelect qiymati (oxirgi N+1 kun) → `from`/`to` so'rov parametrlari. */
export function periodParams(v: string, p: URLSearchParams) {
  if (v === '') return
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - Number(v))
  p.set('from', ymd(from))
  p.set('to', ymd(to))
}
