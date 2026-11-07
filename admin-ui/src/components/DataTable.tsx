import { useMemo, useState, type ReactNode } from 'react'
import { Icon } from './Icon'
import { Button, IconButton } from './Button'
import { Checkbox } from './Choice'
import { SegmentedControl } from './SegmentedControl'
import { DropdownMenu, type MenuItem } from './Overlays'
import { Skeleton } from './Skeleton'
import { EmptyState } from './EmptyState'

export interface Column<R> {
  key: string; header: ReactNode; sortable?: boolean; align?: 'left' | 'right' | 'center'
  render?: (row: R) => ReactNode; width?: number | string; hideable?: boolean; defaultHidden?: boolean
}
export type Density = 'comfortable' | 'compact'

export interface DataTableProps<R> {
  columns: Column<R>[]; rows: R[]; rowKey?: keyof R & string; selectable?: boolean; pageSize?: number; maxHeight?: number
  density?: Density; onDensityChange?: (d: Density) => void; rowActions?: (row: R) => MenuItem[]; onRowClick?: (row: R) => void
  loading?: boolean; error?: ReactNode; onRetry?: () => void; emptyTitle?: ReactNode; emptyDescription?: ReactNode
  toolbar?: ReactNode; bulkActions?: (keys: unknown[]) => ReactNode; caption?: string
  /** D-6: server sahifalash uchun — berilsa ichki sahifalash o'rniga shu pastki qism ko'rsatiladi (null — hech narsa). */
  footer?: ReactNode
}

const cell = (r: object, k: string) => (r as Record<string, unknown>)[k]

/** Jadval: saralash, ustun tanlash, qator tanlash, sahifalash, zichlik, qator amallari menyusi. */
export function DataTable<R extends object>({
  columns = [], rows = [], rowKey = 'id' as keyof R & string, selectable = true, pageSize = 10, maxHeight = 520,
  density: densityProp, onDensityChange, rowActions, onRowClick, loading, error, onRetry, emptyTitle = 'Ma’lumot yo‘q', emptyDescription,
  toolbar, bulkActions, caption, footer,
}: DataTableProps<R>) {
  const [sort, setSort] = useState<{ key: string | null; dir: 'asc' | 'desc' }>({ key: null, dir: 'asc' })
  const [hidden, setHidden] = useState<Set<string>>(() => new Set(columns.filter((c) => c.defaultHidden).map((c) => c.key)))
  const [selected, setSelected] = useState<Set<unknown>>(new Set())
  const [pageState, setPage] = useState(0)
  const [densityState, setDensityState] = useState<Density>('comfortable')
  const density = densityProp || densityState
  const setDensity = (d: string) => { setDensityState(d as Density); onDensityChange?.(d as Density) }
  const keyOf = (r: R) => cell(r, rowKey)

  const visible = columns.filter((c) => !hidden.has(c.key))
  const sorted = useMemo(() => {
    if (!sort.key) return rows
    const k = sort.key
    const s = [...rows].sort((a, b) => {
      const x = cell(a, k), y = cell(b, k)
      return typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'uz')
    })
    return sort.dir === 'desc' ? s.reverse() : s
  }, [rows, sort])
  const pages = Math.max(1, Math.ceil(sorted.length / pageSize))
  // qatorlar soni kamaysa (filtr) sahifa chegaradan chiqmasin
  const page = Math.min(pageState, pages - 1)
  const pageRows = sorted.slice(page * pageSize, page * pageSize + pageSize)
  const allOnPage = pageRows.length > 0 && pageRows.every((r) => selected.has(keyOf(r)))
  const someOnPage = pageRows.some((r) => selected.has(keyOf(r)))

  const toggleSort = (k: string) => setSort((s) => (s.key !== k ? { key: k, dir: 'asc' } : s.dir === 'asc' ? { key: k, dir: 'desc' } : { key: null, dir: 'asc' }))
  const toggleRow = (k: unknown) => setSelected((s) => { const n = new Set(s); if (n.has(k)) n.delete(k); else n.add(k); return n })
  const togglePage = () => setSelected((s) => { const n = new Set(s); pageRows.forEach((r) => { if (allOnPage) n.delete(keyOf(r)); else n.add(keyOf(r)) }); return n })

  const colItems: MenuItem[] = columns.filter((c) => c.hideable !== false).map((c) => ({
    label: c.header, icon: hidden.has(c.key) ? 'square' : 'square-check', keepOpen: true,
    onSelect: () => setHidden((h) => { const n = new Set(h); if (n.has(c.key)) n.delete(c.key); else n.add(c.key); return n }),
  }))

  const colCount = visible.length + (selectable ? 1 : 0) + (rowActions ? 1 : 0)
  let body: ReactNode
  if (loading) {
    body = Array.from({ length: Math.min(pageSize, 6) }, (_, i) => (
      <tr key={i}>{selectable && <td className="sf-table__check"><Skeleton width={16} height={16} /></td>}
        {visible.map((c, j) => <td key={c.key}><Skeleton width={j === 0 ? 160 : 80} /></td>)}{rowActions && <td />}</tr>
    ))
  } else if (error) {
    body = <tr><td colSpan={colCount} style={{ height: 'auto' }}><EmptyState kind="error" title="Ma’lumotni yuklab bo‘lmadi" description={error} actions={onRetry && <Button icon="rotate-cw" onClick={onRetry}>Qayta urinish</Button>} /></td></tr>
  } else if (rows.length === 0) {
    body = <tr><td colSpan={colCount} style={{ height: 'auto' }}><EmptyState title={emptyTitle} description={emptyDescription} /></td></tr>
  } else {
    body = pageRows.map((r) => {
      const k = keyOf(r); const sel = selected.has(k)
      return (
        <tr key={String(k)} aria-selected={selectable ? sel : undefined} data-clickable={!!onRowClick || undefined}
          onClick={onRowClick ? (e) => { if (!(e.target as HTMLElement).closest('button,input,label,a')) onRowClick(r) } : undefined}>
          {selectable && <td className="sf-table__check"><Checkbox checked={sel} onChange={() => toggleRow(k)} aria-label="Qatorni tanlash" /></td>}
          {visible.map((c) => <td key={c.key} data-align={c.align}>{c.render ? c.render(r) : (cell(r, c.key) as ReactNode)}</td>)}
          {rowActions && <td className="sf-table__actions"><DropdownMenu align="end" items={rowActions(r)} trigger={(p) => <IconButton size="sm" icon="ellipsis" label="Qator amallari" {...p} />} /></td>}
        </tr>
      )
    })
  }

  return (
    <div className="sf-table-wrap" data-density={density === 'compact' ? 'compact' : undefined}>
      {selected.size > 0 ? (
        <div className="sf-table-toolbar sf-table-toolbar--selected">
          <span>{selected.size} ta tanlandi</span>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>Tanlovni bekor qilish</Button>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>{bulkActions?.([...selected])}</div>
        </div>
      ) : (
        <div className="sf-table-toolbar">
          {toolbar}
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, alignItems: 'center' }}>
            <SegmentedControl size="sm" label="Zichlik" value={density} onChange={setDensity}
              options={[{ value: 'comfortable', label: 'Keng', icon: 'rows-3' }, { value: 'compact', label: 'Zich', icon: 'rows-4' }]} />
            <DropdownMenu align="end" label="Ustunlar" items={colItems} trigger={(p) => <Button size="sm" icon="columns-3" {...p}>Ustunlar</Button>} />
          </div>
        </div>
      )}
      <div className="sf-table-scroll" style={{ maxHeight }}>
        <table className="sf-table">
          {caption && <caption className="sf-sr-only">{caption}</caption>}
          <thead>
            <tr>
              {selectable && <th className="sf-table__check"><Checkbox checked={allOnPage} indeterminate={!allOnPage && someOnPage} onChange={togglePage} aria-label="Sahifadagi barcha qatorlarni tanlash" /></th>}
              {visible.map((c) => (
                <th key={c.key} data-align={c.align} style={{ width: c.width }} aria-sort={sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                  {c.sortable ? (
                    <button type="button" className="sf-table__sort" onClick={() => toggleSort(c.key)}>
                      {c.header}<Icon name={sort.key !== c.key ? 'chevrons-up-down' : sort.dir === 'asc' ? 'arrow-up' : 'arrow-down'} size={12} />
                    </button>
                  ) : c.header}
                </th>
              ))}
              {rowActions && <th className="sf-table__actions"><span className="sf-sr-only">Amallar</span></th>}
            </tr>
          </thead>
          <tbody>{body}</tbody>
        </table>
      </div>
      {footer !== undefined ? (footer && !loading && !error && rows.length > 0 && <div className="sf-table-foot">{footer}</div>) : !loading && !error && rows.length > 0 && (
        <div className="sf-table-foot">
          <span className="sf-num">{page * pageSize + 1}–{Math.min(sorted.length, (page + 1) * pageSize)} / {sorted.length}</span>
          <div className="sf-table-foot__pages">
            <IconButton size="sm" icon="chevron-left" label="Oldingi sahifa" disabled={page === 0} onClick={() => setPage(page - 1)} />
            <span className="sf-num" style={{ padding: '0 6px' }}>{page + 1} / {pages}</span>
            <IconButton size="sm" icon="chevron-right" label="Keyingi sahifa" disabled={page >= pages - 1} onClick={() => setPage(page + 1)} />
          </div>
        </div>
      )}
    </div>
  )
}
