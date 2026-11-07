import type { CSSProperties } from 'react'
import { ICONS } from './icons'

export interface IconProps {
  /** Lucide nomi (kebab-case), masalan "circle-check". */
  name: string
  size?: number
  /** Berilsa — role="img" va aria-label; aks holda dekorativ (aria-hidden). */
  label?: string
  className?: string
  style?: CSSProperties
}

/** Lucide ikonka (lucide-react, reestr orqali), stroke = currentColor. Noma'lum nom — hech narsa chizilmaydi. */
export function Icon({ name, size = 16, label, className = '', style }: IconProps) {
  const C = ICONS[name]
  if (!C) {
    if (import.meta.env.DEV) console.warn(`[Icon] unknown icon: ${name}`)
    return null
  }
  return (
    <span
      className={'sf-icon ' + className}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={{ width: size, height: size, ...style }}
    >
      <C width={size} height={size} aria-hidden="true" focusable="false" />
    </span>
  )
}
