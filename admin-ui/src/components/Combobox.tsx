import { useEffect, useId, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'
import { Icon } from './Icon'

export interface ComboboxOption { value: string; label: string; hint?: string; icon?: string }
export interface ComboboxProps { options: ComboboxOption[]; value?: string; onChange?: (v: string) => void; placeholder?: string; emptyText?: string; invalid?: boolean; disabled?: boolean; id?: string; style?: CSSProperties }

/** Qidiriladigan yagona tanlov. */
export function Combobox({ options = [], value, onChange, placeholder = 'Qidirish…', emptyText = 'Hech narsa topilmadi', invalid, disabled, id, style }: ComboboxProps) {
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState('')
  const [active, setActive] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const listId = useId()
  const selected = options.find((o) => o.value === value)
  const filtered = options.filter((o) => o.label.toLowerCase().includes(q.toLowerCase()))

  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const pick = (o: ComboboxOption) => { onChange?.(o.value); setQ(''); setOpen(false) }
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setOpen(true); setActive((a) => Math.min(a + 1, filtered.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
    else if (e.key === 'Enter' && open && filtered[active]) { e.preventDefault(); pick(filtered[active]) }
    else if (e.key === 'Escape') setOpen(false)
  }

  return (
    <div ref={ref} style={{ position: 'relative', ...style }}>
      <div className="sf-input" data-invalid={invalid || undefined} data-disabled={disabled || undefined}>
        <Icon name="search" size={16} />
        <input id={id} role="combobox" aria-expanded={open} aria-controls={listId} aria-autocomplete="list"
          aria-activedescendant={open && filtered[active] ? `${listId}-${active}` : undefined}
          disabled={disabled} placeholder={selected ? selected.label : placeholder}
          value={open ? q : selected ? selected.label : ''}
          onFocus={() => setOpen(true)} onChange={(e) => { setQ(e.target.value); setActive(0); setOpen(true) }} onKeyDown={onKey} />
        <Icon name="chevrons-up-down" size={14} />
      </div>
      {open && (
        <div className="sf-pop" role="listbox" id={listId} style={{ left: 0, right: 0, top: 'calc(100% + 4px)', maxHeight: 240, overflowY: 'auto' }}>
          {filtered.length === 0 && <div className="sf-listbox__empty">{emptyText}</div>}
          {filtered.map((o, i) => (
            <div key={o.value} id={`${listId}-${i}`} role="option" aria-selected={o.value === value} data-active={i === active}
              className="sf-option" onMouseEnter={() => setActive(i)} onMouseDown={(e) => { e.preventDefault(); pick(o) }}>
              {o.icon && <Icon name={o.icon} size={16} />}
              <span>{o.label}</span>
              {o.hint && <span className="sf-option__meta">{o.hint}</span>}
              {o.value === value && <Icon name="check" size={16} style={{ marginLeft: o.hint ? 0 : 'auto', color: 'var(--primary)' }} />}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
