/** SellerFlow Admin uchun o'zbekcha formatlovchilar. Vaqt zonasi: Asia/Tashkent. */
const TZ = 'Asia/Tashkent'
const NBSP = ' '
const group = (n: number) => String(Math.trunc(Math.abs(n))).replace(/\B(?=(\d{3})+(?!\d))/g, NBSP)

/** 120000 → "120 000 so‘m" (bo'shliqlar — bo'linmaydigan). */
export function formatSom(n: number): string { return (n < 0 ? '−' : '') + group(n) + NBSP + 'so‘m' }
export function formatNumber(n: number): string { return (n < 0 ? '−' : '') + group(n) }
export function formatPercent(n: number, digits = 2): string { return n.toFixed(digits).replace('.', ',') + '%' }

type DateInput = Date | number | string
function parts(d: DateInput) {
  const p = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(d))
  const g = (t: Intl.DateTimeFormatPartTypes) => p.find((x) => x.type === t)?.value ?? ''
  return { dd: g('day'), mm: g('month'), yyyy: g('year'), HH: g('hour'), min: g('minute') }
}
export function formatDate(d: DateInput): string { const p = parts(d); return `${p.dd}.${p.mm}.${p.yyyy}` }
export function formatTime(d: DateInput): string { const p = parts(d); return `${p.HH}:${p.min}` }
export function formatDateTime(d: DateInput): string { return `${formatDate(d)} ${formatTime(d)}` }
export function formatDuration(sec: number): string {
  const s0 = Math.max(0, Math.floor(sec))
  const h = Math.floor(s0 / 3600), m = Math.floor((s0 % 3600) / 60), s = s0 % 60
  const pad = (x: number) => String(x).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}
