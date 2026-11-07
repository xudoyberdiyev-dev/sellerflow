// D-6: sahifalar uchun umumiy qismlar — sarlavha, holat/kanal yorliqlari, sahifalash, xato chegarasi.
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Badge, Button, Card, EmptyState, IconButton, Select } from '../components'
import { ACCOUNT_STATUS, GRANT_STATUS } from './labels'

export function PageHeader({ title, description, actions, meta }:
  { title: ReactNode; description?: ReactNode; actions?: ReactNode; meta?: ReactNode }) {
  return (
    <div className="sf-page-header">
      <div className="sf-page-header__text">
        <h1 className="sf-page-header__title">{title}</h1>
        {description && <p className="sf-page-header__desc">{description}</p>}
        {meta}
      </div>
      {actions && <div className="sf-page-header__actions">{actions}</div>}
    </div>
  )
}

export function AccountBadge({ status }: { status: string }) {
  const s = ACCOUNT_STATUS[status]
  return <Badge tone={s?.tone ?? 'neutral'}>{s?.label ?? status}</Badge>
}

export function GrantBadge({ status }: { status: string }) {
  const s = GRANT_STATUS[status]
  return <Badge tone={s?.tone ?? 'neutral'}>{s?.label ?? status}</Badge>
}

/** Server tomoni sahifalash (DataTable `footer` ichida). */
export function Pager({ page, total, size, onPage }: { page: number; total: number; size: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size))
  return (
    <>
      <span className="sf-num">{Math.min(total, page * size + 1)}–{Math.min(total, (page + 1) * size)} / {total}</span>
      <div className="sf-table-foot__pages">
        <IconButton size="sm" icon="chevron-left" label="Oldingi sahifa" disabled={page === 0} onClick={() => onPage(page - 1)} />
        <span className="sf-num" style={{ padding: '0 6px' }}>{page + 1} / {pages}</span>
        <IconButton size="sm" icon="chevron-right" label="Keyingi sahifa" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} />
      </div>
    </>
  )
}

/** Sana davri filtri (Asia/Tashkent kunlari, server `from`/`to` = yyyy-mm-dd). */
export function PeriodSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <Select size="sm" aria-label="Davr" value={value} onChange={(e) => onChange(e.target.value)} icon="calendar"
      options={[{ value: '', label: 'Barcha vaqt' }, { value: '0', label: 'Bugun' }, { value: '6', label: 'Oxirgi 7 kun' },
        { value: '29', label: 'Oxirgi 30 kun' }, { value: '89', label: 'Oxirgi 90 kun' }]} />
  )
}

/** Sahifa darajasidagi yuklash xatosi. */
export function LoadError({ error, onRetry, title = 'Ma’lumotni yuklab bo‘lmadi' }: { error: string; onRetry?: () => void; title?: string }) {
  return (
    <Card>
      <EmptyState kind={/SUPERADMIN|ruxsat|403/i.test(error) ? 'forbidden' : 'error'} title={title} description={error}
        actions={onRetry && <Button icon="rotate-cw" onClick={onRetry}>Qayta urinish</Button>} />
    </Card>
  )
}

/**
 * D-7: xato chegarasi — API kutilmagan javob qaytarsa yoki render xatosi bo'lsa ekran oqarib qolmaydi.
 * `resetKey` o'zgarganda (sahifa almashganda) holat tiklanadi.
 */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, { error: Error | null }> {
  state: { error: Error | null } = { error: null }
  static getDerivedStateFromError(error: Error) { return { error } }
  componentDidCatch(error: Error, info: ErrorInfo) {
    // tijoriy ma'lumot konsolga chiqmasin — faqat xato turi
    console.error('UI error:', error.name, info.componentStack?.split('\n')[1]?.trim())
  }
  componentDidUpdate(prev: { resetKey?: string }) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null })
  }
  render() {
    if (!this.state.error) return this.props.children
    return (
      <Card>
        <EmptyState kind="error" title="Sahifani ko‘rsatib bo‘lmadi"
          description="Kutilmagan xatolik yuz berdi. Sahifani qayta yuklang; muammo takrorlansa, administratorga xabar bering."
          actions={<Button icon="rotate-cw" onClick={() => this.setState({ error: null })}>Qayta urinish</Button>} />
      </Card>
    )
  }
}
