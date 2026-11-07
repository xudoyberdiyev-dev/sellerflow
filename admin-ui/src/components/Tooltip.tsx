import { cloneElement, isValidElement, useId, useState, type ReactElement, type ReactNode } from 'react'

/** Klaviatura tugmasi belgisi. */
export function Kbd({ children }: { children: ReactNode }) { return <kbd className="sf-kbd">{children}</kbd> }

export interface TooltipProps { content: ReactNode; shortcut?: string; placement?: 'top' | 'bottom'; defaultOpen?: boolean; children: ReactElement }

/** Hover/fokus tooltip. Faqat qisqa matn; muhim ma'lumot bu yerda yashirilmaydi. */
export function Tooltip({ content, shortcut, placement = 'top', children, defaultOpen = false }: TooltipProps) {
  const [open, setOpen] = useState(defaultOpen)
  const id = useId()
  const child = isValidElement(children)
    ? cloneElement(children as ReactElement<{ 'aria-describedby'?: string }>, { 'aria-describedby': open ? id : undefined })
    : children
  return (
    <span className="sf-tooltip-wrap" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(defaultOpen)}
      onFocus={() => setOpen(true)} onBlur={() => setOpen(defaultOpen)} onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false) }}>
      {child}
      {open && <span role="tooltip" id={id} className={`sf-tooltip ${placement === 'bottom' ? 'sf-tooltip--bottom' : ''}`}>
        {content}{shortcut && <Kbd>{shortcut}</Kbd>}
      </span>}
    </span>
  )
}
