import { useEffect, useId, useRef, useState, type KeyboardEvent, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'
import { Icon } from './Icon'
import { Logo } from './Logo'
import { Kbd, Tooltip } from './Tooltip'
import { Avatar } from './Avatar'
import { IconButton } from './Button'

// ---------------------------------------------------------------- Tabs & Breadcrumbs

export interface TabItem { value: string; label: ReactNode; count?: number; icon?: string; disabled?: boolean }
export interface TabsProps { items: TabItem[]; value: string; onChange?: (v: string) => void; label?: string }

/** Tagiga chizilgan tablar. */
export function Tabs({ items = [], value, onChange, label }: TabsProps) {
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return
    const en = items.filter((i) => !i.disabled)
    const i = en.findIndex((x) => x.value === value)
    const n = en[(i + (e.key === 'ArrowRight' ? 1 : -1) + en.length) % en.length]
    onChange?.(n.value)
    const root = e.currentTarget
    setTimeout(() => root.querySelector<HTMLElement>(`[data-v="${n.value}"]`)?.focus())
  }
  return (
    <div className="sf-tabs" role="tablist" aria-label={label} onKeyDown={onKey}>
      {items.map((t) => (
        <button key={t.value} data-v={t.value} type="button" role="tab" className="sf-tab" aria-selected={t.value === value}
          aria-disabled={t.disabled || undefined} tabIndex={t.value === value ? 0 : -1} onClick={() => !t.disabled && onChange?.(t.value)}>
          {t.icon && <Icon name={t.icon} size={14} />}{t.label}{t.count != null && <span className="sf-tab__count">{t.count}</span>}
        </button>
      ))}
    </div>
  )
}

export interface Crumb { label: ReactNode; href?: string; onClick?: (e: ReactMouseEvent<HTMLAnchorElement>) => void }

/** Joylashuv izi. Oxirgisi — joriy sahifa. */
export function Breadcrumbs({ items = [] }: { items: Crumb[] }) {
  return (
    <nav aria-label="Joylashuv">
      <ol className="sf-crumbs">
        {items.map((it, i) => {
          const last = i === items.length - 1
          return (
            <li key={i} style={{ display: 'contents' }}>
              {last ? <span aria-current="page">{it.label}</span> : <a href={it.href || '#'} onClick={it.onClick}>{it.label}</a>}
              {!last && <Icon name="chevron-right" size={12} />}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

// ---------------------------------------------------------------- Sidebar

export interface NavItem { id: string; label: string; icon: string; count?: number; roles?: string[] }
export interface NavGroup { label: string; items: NavItem[] }
export interface SidebarProps {
  groups: NavGroup[]; active?: string; onNavigate?: (id: string) => void; collapsed?: boolean
  onToggleCollapsed?: () => void; footer?: ReactNode; role?: string
}

/** Guruhlangan, yig'iladigan yon navigatsiya. Rolga mos kelmagan bandlar yashiriladi. */
export function Sidebar({ groups = [], active, onNavigate, collapsed = false, onToggleCollapsed, footer, role }: SidebarProps) {
  const [closed, setClosed] = useState<Record<string, boolean>>({})
  return (
    <aside className="sf-sidebar" data-collapsed={collapsed || undefined} aria-label="Asosiy navigatsiya">
      <div className="sf-sidebar__head"><Logo variant={collapsed ? 'mark' : 'full'} size={28} /></div>
      <nav className="sf-sidebar__nav">
        {groups.map((g) => {
          const items = g.items.filter((it) => !it.roles || !role || it.roles.includes(role))
          if (!items.length) return null
          const isClosed = closed[g.label] && !collapsed
          return (
            <div key={g.label} className="sf-sidebar__group">
              <button type="button" className="sf-sidebar__group-label" aria-expanded={!isClosed} onClick={() => setClosed((c) => ({ ...c, [g.label]: !c[g.label] }))}>
                {g.label}<Icon name={isClosed ? 'chevron-right' : 'chevron-down'} size={12} />
              </button>
              {!isClosed && items.map((it) => {
                const btn = (
                  <button key={it.id} type="button" className="sf-nav-item" aria-current={it.id === active ? 'page' : undefined}
                    aria-label={collapsed ? it.label : undefined} onClick={() => onNavigate?.(it.id)}>
                    <Icon name={it.icon} size={16} />
                    <span className="sf-nav-item__label">{it.label}</span>
                    {it.count != null && <span className="sf-nav-item__count">{it.count}</span>}
                  </button>
                )
                return collapsed ? <Tooltip key={it.id} content={it.label}>{btn}</Tooltip> : btn
              })}
            </div>
          )
        })}
      </nav>
      <div className="sf-sidebar__foot">
        {footer}
        {onToggleCollapsed && (
          <button type="button" className="sf-nav-item" onClick={onToggleCollapsed} aria-label={collapsed ? 'Menyuni yoyish' : 'Menyuni yig‘ish'}>
            <Icon name={collapsed ? 'panel-left-open' : 'panel-left-close'} size={16} />
            <span className="sf-nav-item__label">Menyuni yig‘ish</span>
          </button>
        )}
      </div>
    </aside>
  )
}

// ---------------------------------------------------------------- Topbar

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || '')

export interface TopbarProps {
  breadcrumbs?: Crumb[]; onOpenSearch?: () => void; user?: { name: string; role: string }
  actions?: ReactNode; onMenu?: () => void; onUserClick?: () => void
}

/** Yuqori panel: joylashuv, ⌘K qidiruv, amallar, foydalanuvchi. */
export function Topbar({ breadcrumbs, onOpenSearch, user, actions, onMenu, onUserClick }: TopbarProps) {
  return (
    <header className="sf-topbar">
      {onMenu && <IconButton icon="menu" label="Menyuni ochish" onClick={onMenu} />}
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
      <div className="sf-topbar__right">
        {onOpenSearch && (
          <button type="button" className="sf-topbar__search" onClick={onOpenSearch} aria-label="Qidirish va buyruqlar" aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}>
            <Icon name="search" size={16} /><span>Qidirish yoki buyruq…</span>
            <span style={{ display: 'flex', gap: 2 }}><Kbd>{isMac ? '⌘' : 'Ctrl'}</Kbd><Kbd>K</Kbd></span>
          </button>
        )}
        {actions}
        {user && (
          <button type="button" className="sf-user" onClick={onUserClick} aria-label={`Profil: ${user.name}`}>
            <Avatar name={user.name} size={28} />
            <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
              <span className="sf-user__name">{user.name}</span>
              <span className="sf-user__role">{user.role}</span>
            </span>
          </button>
        )}
      </div>
    </header>
  )
}

// ---------------------------------------------------------------- CommandPalette

export interface CommandItem { id?: string; label: string; group?: string; icon?: string; hint?: string; hintMono?: boolean; shortcut?: string[]; onSelect?: () => void }
export interface CommandPaletteProps { open: boolean; onOpenChange: (open: boolean) => void; items?: CommandItem[]; placeholder?: string; bindShortcut?: boolean; contained?: boolean }

/** ⌘K / Ctrl+K buyruqlar paneli. */
export function CommandPalette({ open, onOpenChange, items = [], placeholder = 'Sotuvchi, sahifa yoki amalni qidiring…', bindShortcut = true, contained }: CommandPaletteProps) {
  const [q, setQ] = useState('')
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()

  useEffect(() => {
    if (!bindShortcut) return
    const h = (e: globalThis.KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); onOpenChange(!open) } }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [open, bindShortcut, onOpenChange])
  useEffect(() => { if (open) setTimeout(() => inputRef.current?.focus(), 0) }, [open])

  if (!open) return null
  const filtered = items.filter((i) => (i.label + ' ' + (i.hint || '')).toLowerCase().includes(q.toLowerCase()))
  const groups = [...new Set(filtered.map((i) => i.group || ''))]
  const close = () => { setQ(''); setActive(0); onOpenChange(false) }
  const run = (it: CommandItem) => { close(); it.onSelect?.() }
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, filtered.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
    else if (e.key === 'Enter' && filtered[active]) { e.preventDefault(); run(filtered[active]) }
    else if (e.key === 'Escape') { e.preventDefault(); close() }
  }
  let idx = -1
  return (
    <div className={`sf-overlay sf-overlay--top ${contained ? 'sf-overlay--contained' : ''}`} onMouseDown={(e) => { if (e.target === e.currentTarget) close() }}>
      <div className="sf-cmd" role="dialog" aria-modal="true" aria-label="Buyruqlar paneli">
        <div className="sf-cmd__input">
          <Icon name="search" size={18} style={{ color: 'var(--text-tertiary)' }} />
          <input ref={inputRef} value={q} onChange={(e) => { setQ(e.target.value); setActive(0) }} onKeyDown={onKey} placeholder={placeholder}
            role="combobox" aria-expanded="true" aria-controls={listId} aria-activedescendant={filtered[active] ? `${listId}-${active}` : undefined} />
          <Kbd>Esc</Kbd>
        </div>
        <div className="sf-cmd__list" role="listbox" id={listId}>
          {filtered.length === 0 && <div className="sf-listbox__empty">«{q}» bo‘yicha hech narsa topilmadi</div>}
          {groups.map((g) => (
            <div key={g} role="group" aria-label={g}>
              {g && <div className="sf-menu__label">{g}</div>}
              {filtered.filter((i) => (i.group || '') === g).map((it) => {
                idx++; const my = idx
                return (
                  <div key={it.id || it.label} id={`${listId}-${my}`} role="option" aria-selected={my === active} data-active={my === active}
                    className="sf-option" onMouseEnter={() => setActive(my)} onClick={() => run(it)}>
                    {it.icon && <Icon name={it.icon} size={16} />}<span>{it.label}</span>
                    {(it.hint || it.shortcut) && <span className="sf-option__meta">{it.hint && <span className={it.hintMono ? 'sf-mono' : ''}>{it.hint}</span>}{it.shortcut?.map((k) => <Kbd key={k}>{k}</Kbd>)}</span>}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
        <div className="sf-cmd__foot">
          <span><Kbd>↑</Kbd><Kbd>↓</Kbd> tanlash</span><span><Kbd>↵</Kbd> ochish</span><span><Kbd>Esc</Kbd> yopish</span>
        </div>
      </div>
    </div>
  )
}
