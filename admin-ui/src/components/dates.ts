export const day0 = (d: Date) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x }
export const addDays = (d: Date, n: number) => { const x = new Date(d); x.setDate(x.getDate() + n); return x }

export function rangeForPreset(preset: string, today: Date = new Date()): { from: Date; to: Date } | null {
  const t = day0(today)
  if (preset === 'today') return { from: t, to: t }
  if (preset === '7d') return { from: addDays(t, -6), to: t }
  if (preset === '30d') return { from: addDays(t, -29), to: t }
  return null
}

