import { useEffect, useId, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode, type RefObject } from 'react'
import { Icon } from './Icon'
import { Kbd } from './Tooltip'
import { Button, IconButton } from './Button'
import { Field, Textarea } from './Input'

// ---------------------------------------------------------------- Drawer

export interface DrawerProps { open: boolean; onClose?: () => void; title: ReactNode; subtitle?: ReactNode; actions?: ReactNode; children?: ReactNode; footer?: ReactNode; width?: number; contained?: boolean }

/** O'ng tomondan chiquvchi panel — ro'yxatdan chiqmasdan yozuv tafsilotlari. */
export function Drawer({ open, onClose, title, subtitle, actions, children, footer, width, contained }: DrawerProps) {
  const ref = useRef<HTMLElement>(null)
  const tid = useId()
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    if (!open) return
    const prev = document.activeElement as HTMLElement | null
    setTimeout(() => ref.current?.focus(), 0)
    const k = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') closeRef.current?.() }
    document.addEventListener('keydown', k)
    return () => { document.removeEventListener('keydown', k); prev?.focus?.() }
  }, [open])
  if (!open) return null
  return (
    <div className={`sf-drawer-overlay ${contained ? 'sf-drawer-overlay--contained' : ''}`} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.() }}>
      <aside ref={ref} tabIndex={-1} className="sf-drawer" role="dialog" aria-modal="true" aria-labelledby={tid} style={width ? { width: `min(${width}px, 100%)` } : undefined}>
        <div className="sf-drawer__head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2 className="sf-drawer__title" id={tid}>{title}</h2>
            {subtitle && <div className="sf-drawer__sub">{subtitle}</div>}
          </div>
          {actions}
          <IconButton icon="x" label="Yopish" onClick={onClose} />
        </div>
        <div className="sf-drawer__body">{children}</div>
        {footer && <div className="sf-drawer__foot">{footer}</div>}
      </aside>
    </div>
  )
}

// ---------------------------------------------------------------- Popover & DropdownMenu

function useOutside(open: boolean, ref: RefObject<HTMLElement | null>, close: () => void) {
  const closeRef = useRef(close)
  closeRef.current = close
  useEffect(() => {
    if (!open) return
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) closeRef.current() }
    const k = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') closeRef.current() }
    document.addEventListener('mousedown', h); document.addEventListener('keydown', k)
    return () => { document.removeEventListener('mousedown', h); document.removeEventListener('keydown', k) }
  }, [open, ref])
}

export interface TriggerProps { onClick: () => void; 'aria-expanded': boolean; 'aria-haspopup': 'dialog' | 'menu'; 'aria-controls': string }

export interface PopoverProps { trigger: (p: TriggerProps) => ReactNode; children?: ReactNode; align?: 'start' | 'end'; width?: number; defaultOpen?: boolean; label?: string }

/** Langarlangan suzuvchi panel. trigger — render-prop. */
export function Popover({ trigger, children, align = 'start', width, defaultOpen = false, label }: PopoverProps) {
  const [open, setOpen] = useState(defaultOpen)
  const ref = useRef<HTMLDivElement>(null)
  const id = useId()
  useOutside(open, ref, () => setOpen(false))
  const pos: CSSProperties = align === 'end' ? { right: 0 } : { left: 0 }
  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex' }}>
      {trigger({ onClick: () => setOpen((o) => !o), 'aria-expanded': open, 'aria-haspopup': 'dialog', 'aria-controls': id })}
      {open && <div id={id} role="dialog" aria-label={label} className="sf-pop" style={{ top: 'calc(100% + 6px)', ...pos, width, padding: 12 }}>{children}</div>}
    </div>
  )
}

export type MenuItem =
  | { separator: true }
  | { heading: ReactNode }
  | { label: ReactNode; icon?: string; onSelect?: () => void; danger?: boolean; disabled?: boolean; shortcut?: string[]; keepOpen?: boolean }

export interface DropdownMenuProps { trigger: (p: TriggerProps) => ReactNode; items?: MenuItem[]; align?: 'start' | 'end'; label?: string; defaultOpen?: boolean; width?: number }

type ActionItem = Extract<MenuItem, { label: ReactNode }>
const isAction = (it: MenuItem): it is ActionItem => 'label' in it

/** Amallar menyusi. */
export function DropdownMenu({ trigger, items = [], align = 'start', label, defaultOpen = false, width = 220 }: DropdownMenuProps) {
  const [open, setOpen] = useState(defaultOpen)
  const [active, setActive] = useState(-1)
  const ref = useRef<HTMLDivElement>(null)
  const id = useId()
  useOutside(open, ref, () => setOpen(false))
  const actionable = items.map((it, i) => (isAction(it) && !it.disabled ? i : -1)).filter((i) => i >= 0)
  const toggle = () => setOpen((o) => {
    if (!o) { setActive(actionable[0] ?? -1); setTimeout(() => ref.current?.querySelector<HTMLElement>('[role=menu]')?.focus(), 0) }
    return !o
  })
  const select = (it: MenuItem) => {
    if (!isAction(it) || it.disabled) return
    it.onSelect?.()
    if (!it.keepOpen) setOpen(false)
  }
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const p = actionable.indexOf(active)
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(actionable[(p + 1) % actionable.length]) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(actionable[(p - 1 + actionable.length) % actionable.length]) }
    else if ((e.key === 'Enter' || e.key === ' ') && items[active]) { e.preventDefault(); select(items[active]) }
    else if (e.key === 'Tab') setOpen(false)
  }
  const pos: CSSProperties = align === 'end' ? { right: 0 } : { left: 0 }
  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex' }}>
      {trigger({ onClick: toggle, 'aria-expanded': open, 'aria-haspopup': 'menu', 'aria-controls': id })}
      {open && (
        <div id={id} role="menu" aria-label={label} tabIndex={-1} className="sf-pop" onKeyDown={onKey}
          aria-activedescendant={active >= 0 ? `${id}-${active}` : undefined}
          style={{ top: 'calc(100% + 4px)', ...pos, width, outline: 'none' }}>
          {items.map((it, i) => 'separator' in it ? <div key={i} className="sf-menu__sep" role="separator" /> :
            'heading' in it ? <div key={i} className="sf-menu__label">{it.heading}</div> : (
              <div key={i} id={`${id}-${i}`} role="menuitem" aria-disabled={it.disabled || undefined} data-active={i === active}
                className={`sf-option ${it.danger ? 'sf-option--danger' : ''}`} onMouseEnter={() => setActive(i)} onClick={() => select(it)}>
                {it.icon && <Icon name={it.icon} size={16} />}<span>{it.label}</span>
                {it.shortcut && <span className="sf-option__meta">{it.shortcut.map((k) => <Kbd key={k}>{k}</Kbd>)}</span>}
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- Modal & ConfirmDialog

function useDialog(open: boolean, onClose: (() => void) | undefined, ref: RefObject<HTMLDivElement | null>) {
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    if (!open) return
    const prev = document.activeElement as HTMLElement | null
    const t = setTimeout(() => {
      ref.current?.querySelector<HTMLElement>('[data-autofocus], textarea, input, button:not([aria-label="Yopish"])')?.focus()
    }, 0)
    const k = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current?.() }
      if (e.key === 'Tab' && ref.current) {
        const f = [...ref.current.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), select, [tabindex]:not([tabindex="-1"])')]
        if (!f.length) return
        const first = f[0], last = f[f.length - 1]
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
      }
    }
    document.addEventListener('keydown', k)
    return () => { clearTimeout(t); document.removeEventListener('keydown', k); prev?.focus?.() }
  }, [open, ref])
}

export type ModalTone = 'danger' | 'warning' | 'privacy' | 'info'
export interface ModalProps {
  open: boolean; onClose?: () => void; title: ReactNode; description?: ReactNode; tone?: ModalTone; icon?: string
  children?: ReactNode; footer?: ReactNode; width?: number; contained?: boolean; role?: 'dialog' | 'alertdialog'
}

/** Markazdagi dialog. tone sarlavha ikonkasini belgilaydi. */
export function Modal({ open, onClose, title, description, tone, icon, children, footer, width = 480, contained, role = 'dialog' }: ModalProps) {
  const ref = useRef<HTMLDivElement>(null)
  const tid = useId()
  useDialog(open, onClose, ref)
  if (!open) return null
  const ic = icon || (tone ? { danger: 'triangle-alert', warning: 'triangle-alert', privacy: 'lock', info: 'info' }[tone] : undefined)
  return (
    <div className={`sf-overlay ${contained ? 'sf-overlay--contained' : ''}`} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.() }}>
      <div ref={ref} className="sf-modal" role={role} aria-modal="true" aria-labelledby={tid} style={{ '--sf-modal-w': width + 'px' } as CSSProperties}>
        <div className="sf-modal__head">
          {ic && <div className={`sf-modal__icon sf-modal__icon--${tone || 'info'}`}><Icon name={ic} size={18} /></div>}
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2 className="sf-modal__title" id={tid}>{title}</h2>
            {description && <p className="sf-modal__desc">{description}</p>}
          </div>
          <IconButton size="sm" icon="x" label="Yopish" onClick={onClose} style={{ margin: '-4px -8px 0 0' }} />
        </div>
        {children && <div className="sf-modal__body">{children}</div>}
        {footer && <div className="sf-modal__foot" style={children ? undefined : { paddingTop: 20 }}>{footer}</div>}
      </div>
    </div>
  )
}

export interface ConfirmDialogProps {
  open: boolean; onClose?: () => void; onConfirm?: (reason: string) => void | Promise<unknown>
  title: ReactNode; description?: ReactNode; tone?: ModalTone; confirmLabel?: ReactNode; cancelLabel?: ReactNode
  requireReason?: boolean; reasonLabel?: ReactNode; reasonPlaceholder?: string; minReasonLength?: number
  auditNote?: ReactNode; children?: ReactNode; contained?: boolean
}

/** Tasdiqlash dialogi. requireReason=true — sabab majburiy (xavfli amallar). onConfirm Promise qaytarsa — tugma loading. */
export function ConfirmDialog({
  open, onClose, onConfirm, title, description, tone = 'danger', confirmLabel = 'Tasdiqlash', cancelLabel = 'Bekor qilish',
  requireReason = false, reasonLabel = 'Sabab', reasonPlaceholder = 'Nima uchun bu amal bajarilayotganini yozing', minReasonLength = 10,
  auditNote = 'Amal auditga yoziladi', children, contained,
}: ConfirmDialogProps) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) { // ochilganda holatni tozalash (effect'siz)
    setWasOpen(open)
    if (open) { setReason(''); setTouched(false); setLoading(false) }
  }
  const tooShort = requireReason && reason.trim().length < minReasonLength
  const submit = async () => {
    setTouched(true)
    if (tooShort) return
    setLoading(true)
    try { await onConfirm?.(reason.trim()) } finally { setLoading(false) }
  }
  return (
    <Modal open={open} onClose={loading ? undefined : onClose} title={title} description={description} tone={tone} contained={contained} role="alertdialog"
      footer={<>
        {auditNote && <span className="sf-modal__audit"><Icon name="scroll-text" size={14} />{auditNote}</span>}
        <Button variant="secondary" onClick={onClose} disabled={loading}>{cancelLabel}</Button>
        <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={submit} loading={loading}>{confirmLabel}</Button>
      </>}>
      {children}
      {requireReason && (
        <Field label={reasonLabel} hint={`Kamida ${minReasonLength} ta belgi. Sabab audit jurnalida saqlanadi.`}
          error={touched && tooShort ? `Sababni yozing — kamida ${minReasonLength} ta belgi kerak.` : null}>
          <Textarea value={reason} onChange={(e) => setReason(e.target.value)} onBlur={() => setTouched(true)} placeholder={reasonPlaceholder} maxLength={500} required aria-required="true" data-autofocus />
        </Field>
      )}
    </Modal>
  )
}
