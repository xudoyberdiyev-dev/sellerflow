export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'orange' | 'neutral' | 'privacy' | 'brand'

export const STATUS_TONES: Record<string, Tone> = {
  'Faol': 'success', 'Ro‘yxatdan o‘tmoqda': 'info', 'Bloklangan': 'warning', 'O‘chirish so‘ralgan': 'orange', 'Yopilgan': 'neutral',
  'Kutilmoqda': 'info', 'Ruxsat berilgan': 'success', 'Rad etilgan': 'danger', 'Bekor qilingan': 'danger', 'Muddati tugagan': 'neutral',
}

export const CHANNELS: { id: string; label: string; color: string }[] = [
  { id: 'uzum', label: 'Uzum Market', color: 'var(--chart-1)' },
  { id: 'wb', label: 'Wildberries', color: 'var(--chart-2)' },
  { id: 'ozon', label: 'Ozon', color: 'var(--chart-3)' },
  { id: 'instagram', label: 'Instagram', color: 'var(--chart-4)' },
  { id: 'telegram', label: 'Telegram savdo', color: 'var(--chart-5)' },
  { id: 'offline', label: 'Offline do‘kon', color: 'var(--chart-6)' },
  { id: 'other', label: 'Boshqa', color: 'var(--chart-7)' },
]

