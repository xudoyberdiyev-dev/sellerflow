// D-1: mavzu (yorug'/qorong'i). Tanlov — foydalanuvchi sozlamasi (localStorage, try/catch), standart — tizim sozlamasi.
export type Theme = 'light' | 'dark'
const KEY = 'sf.theme'

export function storedTheme(): Theme | null {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : null
  } catch {
    return null
  }
}

export function systemTheme(): Theme {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export function applyTheme(t: Theme = storedTheme() ?? systemTheme()) {
  document.documentElement.setAttribute('data-theme', t)
  return t
}

export function setTheme(t: Theme) {
  try {
    localStorage.setItem(KEY, t)
  } catch {
    /* saqlab bo'lmasa ham joriy sahifada qo'llanadi */
  }
  applyTheme(t)
}
