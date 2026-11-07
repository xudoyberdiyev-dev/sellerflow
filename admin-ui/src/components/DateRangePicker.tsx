import { useEffect, useRef, useState } from 'react'
import { SegmentedControl } from './SegmentedControl'
import { Button, IconButton } from './Button'
import { day0, rangeForPreset } from './dates'

const MONTHS = ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun', 'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr']
const DOW = ['Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sh', 'Ya']
const fmt = (d: Date) => `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}`

export type DatePreset = 'today' | '7d' | '30d' | 'custom'
export interface DateRangeValue { preset: DatePreset; from: Date; to: Date }
export interface DateRangePickerProps { value?: DateRangeValue; onChange?: (v: DateRangeValue) => void; today?: Date }

/** Tayyor davrlar (Bugun / 7 kun / 30 kun) + ixtiyoriy oraliq kalendari. */
export function DateRangePicker({ value, onChange, today = new Date() }: DateRangePickerProps) {
  const v: DateRangeValue = value ?? { preset: '7d', ...(rangeForPreset('7d', today) as { from: Date; to: Date }) }
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<{ from: Date | null; to: Date | null }>({ from: v.from, to: v.to })
  const [month, setMonth] = useState(() => { const d = day0(v.to || today); d.setDate(1); return d })
  const ref = useRef<HTMLDivElement>(null)
  const t0 = day0(today)

  useEffect(() => {
    if (!open) return
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', h); document.addEventListener('keydown', k)
    return () => { document.removeEventListener('mousedown', h); document.removeEventListener('keydown', k) }
  }, [open])

  const onPreset = (p: string) => {
    if (p === 'custom') { setDraft({ from: v.from, to: v.to }); setOpen(true); return }
    const r = rangeForPreset(p, today)
    if (r) onChange?.({ preset: p as DatePreset, ...r })
  }
  const clickDay = (d: Date) => {
    if (!draft.from || (draft.from && draft.to)) setDraft({ from: d, to: null })
    else if (d < draft.from) setDraft({ from: d, to: draft.from })
    else setDraft({ from: draft.from, to: d })
  }

  const first = new Date(month); const offset = (first.getDay() + 6) % 7
  const daysIn = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const cells: (Date | null)[] = [...Array<null>(offset).fill(null), ...Array.from({ length: daysIn }, (_, i) => new Date(month.getFullYear(), month.getMonth(), i + 1))]

  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
      <SegmentedControl label="Davr" value={v.preset} onChange={onPreset}
        options={[{ value: 'today', label: 'Bugun' }, { value: '7d', label: '7 kun' }, { value: '30d', label: '30 kun' }, { value: 'custom', label: 'Oraliq', icon: 'calendar' }]} />
      {v.from && v.to && <span className="sf-num" style={{ font: 'var(--type-small)', color: 'var(--text-secondary)' }}>{fmt(v.from)} – {fmt(v.to)}</span>}
      {open && (
        <div className="sf-pop" role="dialog" aria-label="Sana oralig‘ini tanlash" style={{ top: 'calc(100% + 6px)', left: 0, padding: 12, width: 260 }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
            <IconButton size="sm" icon="chevron-left" label="Oldingi oy" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} />
            <div style={{ flex: 1, textAlign: 'center', font: 'var(--type-body-strong)' }}>{MONTHS[month.getMonth()]} {month.getFullYear()}</div>
            <IconButton size="sm" icon="chevron-right" label="Keyingi oy" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} />
          </div>
          <div className="sf-cal" role="grid">
            {DOW.map((d) => <div key={d} className="sf-cal__dow">{d}</div>)}
            {cells.map((d, i) => {
              if (!d) return <span key={'e' + i} />
              const future = d > t0
              const edge = (draft.from != null && +d === +draft.from) || (draft.to != null && +d === +draft.to)
              const inR = draft.from != null && draft.to != null && d > draft.from && d < draft.to
              return <button key={i} type="button" className="sf-cal__day" disabled={future} data-muted={future || undefined}
                data-edge={edge || undefined} data-in={inR || undefined} data-today={+d === +t0 || undefined}
                aria-label={fmt(d)} aria-pressed={edge} onClick={() => clickDay(d)}>{d.getDate()}</button>
            })}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border-default)' }}>
            <span className="sf-num" style={{ font: 'var(--type-small)', color: 'var(--text-secondary)', flex: 1 }}>
              {draft.from ? fmt(draft.from) : '—'} – {draft.to ? fmt(draft.to) : '—'}
            </span>
            <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>Bekor qilish</Button>
            <Button size="sm" variant="primary" disabled={!draft.from || !draft.to}
              onClick={() => { if (draft.from && draft.to) onChange?.({ preset: 'custom', from: draft.from, to: draft.to }); setOpen(false) }}>Qo‘llash</Button>
          </div>
        </div>
      )}
    </div>
  )
}
