// SellerFlow Admin API klienti. Token sessionStorage'da (brauzer yopilsa o'chadi).

const BASE = '/admin/api/v1'
const KEY = 'sf.admin.token'

export type Role = 'SUPERADMIN' | 'SUPPORT'
export interface Me { id: number; email: string; role: Role; mustChangePassword?: boolean }

export class ApiError extends Error {
  constructor(public status: number, public code: string) {
    super(code)
  }
}

function readToken(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function setToken(t: string | null) {
  try {
    if (t) sessionStorage.setItem(KEY, t)
    else sessionStorage.removeItem(KEY)
  } catch {
    /* storage mavjud emas — token faqat xotirada */
  }
  memoryToken = t
}

let memoryToken: string | null = readToken()
export const getToken = () => memoryToken

/** D-4: 401 tufayli chiqarilgan bo'lsa login ekranida «sessiya tugadi» xabari bir marta ko'rsatiladi. */
let sessionExpiredFlag = false
export function takeSessionExpired(): boolean {
  const v = sessionExpiredFlag
  sessionExpiredFlag = false
  return v
}

export async function api<T>(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (memoryToken) headers.Authorization = `Bearer ${memoryToken}`
  const res = await fetch(BASE + path, {
    method: init.method ?? 'GET',
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
    signal: init.signal,
  })
  const text = await res.text()
  // LOW: proxy 502/413 kabi JSON bo'lmagan javob SyntaxError bermasin
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      if (res.ok) throw new ApiError(res.status, 'BAD_RESPONSE')
    }
  }
  if (!res.ok) {
    const err = data && typeof data === 'object' ? (data as { error?: unknown }).error : undefined
    const code = typeof err === 'string' && err ? err : `HTTP_${res.status}`
    if (res.status === 401 && path !== '/auth/login' && path !== '/auth/totp/confirm') {
      setToken(null)
      sessionExpiredFlag = true
      window.dispatchEvent(new Event('sf:logout'))
    }
    throw new ApiError(res.status, code)
  }
  return data as T
}

/** Ro'yxat + server jami soni (X-Total-Count). */
export async function apiPage<T>(path: string, init: { signal?: AbortSignal } = {}): Promise<{ items: T[]; total: number }> {
  const headers: Record<string, string> = {}
  if (memoryToken) headers.Authorization = `Bearer ${memoryToken}`
  const res = await fetch(BASE + path, { headers, signal: init.signal })
  if (!res.ok) {
    // xato yo'li api() bilan bir xil bo'lishi uchun
    return api<{ items: T[]; total: number }>(path, init)
  }
  const items = (await res.json()) as T[]
  const total = Number(res.headers.get('X-Total-Count') ?? items.length)
  return { items, total: Number.isFinite(total) ? total : items.length }
}

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email yoki parol noto‘g‘ri. Ko‘p xato urinishdan keyin hisob 15 daqiqaga bloklanadi.',
  STEP_UP_REQUIRED: 'Joriy parolingiz noto‘g‘ri. Amal bajarilmadi.',
  PURGE_ONLY_CLOSED: 'Faqat yopilgan yoki o‘chirish so‘ralgan akkauntni to‘liq o‘chirish mumkin.',
  DATE_INVALID: 'Sana noto‘g‘ri.',
  PASSWORD_REUSED: 'Yangi parol joriy paroldan farq qilishi kerak.',
  CONCURRENT_MODIFICATION: 'Ma’lumot boshqa joyda o‘zgartirildi. Sahifani yangilab, qayta urinib ko‘ring.',
  PAYLOAD_TOO_LARGE: 'So‘rov juda katta.',
  BAD_RESPONSE: 'Serverdan kutilmagan javob keldi.',
  INVALID_REQUEST: 'So‘rov to‘liq emas.',
  REASON_REQUIRED: 'Sababni yozing (kamida 5 belgi).',
  INVALID_CODE: 'Tasdiqlash kodi noto‘g‘ri yoki eskirgan.',
  INVALID_SESSION: 'Sessiya muddati tugagan. Qaytadan kiring.',
  LOCKED: 'Ko‘p marta xato kiritildi. 15 daqiqadan keyin urinib ko‘ring.',
  PASSWORD_CHANGE_REQUIRED: 'Avval vaqtinchalik parolni almashtiring.',
  TOO_MANY_ATTEMPTS: 'Bu qurilmadan juda ko‘p xato urinish. 15 daqiqadan keyin urinib ko‘ring.',
  UNAUTHORIZED: 'Avval tizimga kiring.',
  SUPERADMIN_REQUIRED: 'Bu amal faqat SUPERADMIN uchun.',
  NOT_FOUND: 'Topilmadi.',
  PURPOSE_REQUIRED: 'Sababni kamida 10 belgida yozing.',
  MINUTES_RANGE: 'Muddat 30–60 daqiqa bo‘lishi kerak.',
  SELLER_NOT_ACTIVE: 'Sotuvchi akkaunti faol emas.',
  GRANT_NOT_ACTIVE: 'Ruxsat faol emas (tasdiqlanmagan, bekor qilingan yoki muddati tugagan).',
  NOT_YOUR_GRANT: 'Bu ruxsat boshqa administratorga berilgan.',
  EMAIL_INVALID: 'Email noto‘g‘ri.',
  EMAIL_TAKEN: 'Bu email bilan admin allaqachon bor.',
  PASSWORD_TOO_SHORT: 'Parol kamida 12 belgidan iborat bo‘lsin.',
  PASSWORD_TOO_LONG: 'Parol juda uzun (ko‘pi bilan 72 bayt).',
  PAGE_RANGE: 'Sahifa raqami noto‘g‘ri.',
  GRANT_LIMIT: 'Bu sotuvchiga 24 soatda ko‘pi bilan 3 ta so‘rov yuborish mumkin.',
  WRONG_PASSWORD: 'Joriy parol noto‘g‘ri.',
  CANNOT_CHANGE_SELF: 'O‘zingizning holatingizni o‘zgartira olmaysiz.',
  SUPPORT_URL_REQUIRED: 'Yoqilgan kontakt uchun havola kerak.',
  SUPPORT_URL_INVALID: 'Havola https:// bilan boshlanishi yoki @username bo‘lishi kerak.',
  ONLY_ACTIVE_CAN_BE_SUSPENDED: 'Faqat faol akkauntni bloklash mumkin.',
  NO_BUSINESS: 'Bu foydalanuvchi ro‘yxatdan o‘tishni tugatmagan.',
  TELEGRAM_USER_ID_REQUIRED: 'Telegram ID kiriting.',
}

export function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (MESSAGES[e.code]) return MESSAGES[e.code]
    if (e.code.startsWith('CANNOT_REACTIVATE_FROM_')) return 'Bu holatdan qayta faollashtirib bo‘lmaydi.'
    if (e.code === 'HTTP_502' || e.code === 'HTTP_503' || e.code === 'HTTP_504') return 'Server vaqtincha javob bermayapti. Birozdan keyin urinib ko‘ring.'
    return `Xatolik: ${e.code}`
  }
  return 'Server bilan aloqa yo‘q. Qaytadan urinib ko‘ring.'
}

export const fmtDate = (s?: string | null) =>
  s ? new Date(s).toLocaleString('uz-UZ', { timeZone: 'Asia/Tashkent', dateStyle: 'short', timeStyle: 'short' }) : '—'

export const fmtMoney = (v?: string | number | null) => {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? n.toLocaleString('ru-RU', { maximumFractionDigits: 2 }) + ' so‘m' : String(v)
}

/** D-6: autentifikatsiyalangan fayl yuklab olish (CSV eksport) — token header orqali, URL'da emas. */
export async function download(path: string, filename: string): Promise<{ truncated: boolean }> {
  const headers: Record<string, string> = {}
  if (memoryToken) headers.Authorization = `Bearer ${memoryToken}`
  const res = await fetch(BASE + path, { headers })
  if (!res.ok) {
    let code = `HTTP_${res.status}`
    try { code = (await res.json()).error ?? code } catch { /* JSON emas */ }
    throw new ApiError(res.status, code)
  }
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  return { truncated: res.headers.get('X-Export-Truncated') === 'true' }
}
