import { useState } from 'react'
import { api, download, errorText } from '../api'
import { Badge, Button, DataTable, Input, Select, formatDateTime, type Column } from '../components'
import { useShell } from '../shell'
import { PageHeader, Pager, PeriodSelect } from './common'
import { periodParams, today } from './labels'
import { useLoad } from './useLoad'

interface Event { id: number; at: string; actorType: string; actorId: string | null; businessId: number | null; action: string; entityType: string | null; entityId: string | null; summary: string | null; sourceIp?: string | null }
interface Page { total: number; page: number; items: Event[] }

const SIZE = 50
const ACTOR_TONE: Record<string, 'brand' | 'info' | 'neutral'> = { ADMIN: 'brand', USER: 'info', SYSTEM: 'neutral' }
const ACTIONS = [
  'ADMIN_LOGIN', 'ADMIN_LOGIN_FAILED', 'ADMIN_LOCKED', 'ADMIN_CREATED', 'ADMIN_STATUS_CHANGE', 'ADMIN_PASSWORD_CHANGED', 'ADMIN_RECOVERED',
  'ACCESS_REQUESTED', 'ACCESS_APPROVED', 'ACCESS_DECLINED', 'ACCESS_REVOKED', 'ACCESS_DATA_VIEWED',
  'USER_REGISTERED', 'CONSENT_ACCEPTED', 'ACCOUNT_ACTIVATED', 'DELETION_REQUESTED', 'DELETION_CANCELLED', 'ACCOUNT_CLOSED', 'ACCOUNT_PURGED',
  'SUPPORT_UPDATED', 'EXPORT_SELLERS', 'EXPORT_AUDIT',
]

/** D-6: audit jurnali — sana, bajaruvchi, amal, biznes filtrlari; server sahifalash; CSV eksport. */
export function AuditPage() {
  const [page, setPage] = useState(0)
  const [range, setRange] = useState('')
  const [actor, setActor] = useState('')
  const [actorQ, setActorQ] = useState('')
  const [action, setAction] = useState('')
  const [biz, setBiz] = useState('')
  const [bizQ, setBizQ] = useState('')
  const [exporting, setExporting] = useState(false)
  const { toast } = useShell()
  const filters = () => {
    const p = new URLSearchParams()
    periodParams(range, p)
    if (actorQ) p.set('actor', actorQ)
    if (action) p.set('action', action)
    if (bizQ) p.set('businessId', bizQ)
    return p
  }
  const list = useLoad((signal) => {
    const p = filters()
    p.set('page', String(page)); p.set('size', String(SIZE))
    return api<Page>(`/audit?${p}`, { signal })
  }, [page, range, actorQ, action, bizQ])
  const reset = (fn: () => void) => { fn(); setPage(0) }
  const any = !!(range || actorQ || action || bizQ)

  const exportCsv = async () => {
    setExporting(true)
    try {
      const r = await download(`/audit/export.csv?${filters()}`, `sellerflow-audit-${today()}.csv`)
      toast(r.truncated
        ? { tone: 'warning', title: 'Eksport qisqartirildi', description: 'Faqat eng yangi 10 000 qator yuklandi — davrni toraytiring.', audit: true }
        : { tone: 'success', title: 'Eksport tayyor', audit: true })
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    } finally {
      setExporting(false)
    }
  }

  const columns: Column<Event>[] = [
    { key: 'at', header: 'Vaqt', hideable: false, render: (e) => <span className="sf-num">{formatDateTime(e.at)}</span> },
    { key: 'actorType', header: 'Kim', render: (e) => (
      <span className="sf-row"><Badge tone={ACTOR_TONE[e.actorType] ?? 'neutral'} dot={false}>{e.actorType}</Badge>
        {e.actorId && <span className="sf-cell-sub sf-truncate" style={{ maxWidth: 180 }}>{e.actorId}</span>}</span>) },
    { key: 'action', header: 'Amal', render: (e) => <span className="sf-id">{e.action}</span> },
    { key: 'entity', header: 'Obyekt', render: (e) => <span className="sf-cell-sub">{e.entityType ? `${e.entityType} #${e.entityId ?? ''}` : '—'}</span> },
    { key: 'businessId', header: 'Biznes', align: 'right', render: (e) => <span className="sf-num">{e.businessId ?? '—'}</span> },
    { key: 'sourceIp', header: 'IP', defaultHidden: true, render: (e) => <span className="sf-id">{e.sourceIp ?? '—'}</span> },
    { key: 'summary', header: 'Izoh', render: (e) => <span className="sf-truncate sf-cell-sub" title={e.summary ?? ''}>{e.summary ?? ''}</span> },
  ]

  return (
    <div className="sf-page">
      <PageHeader title="Audit jurnali" description="Muhim amallar: kim, qachon, nima. Tijoriy qiymatlar yozilmaydi."
        actions={<Button icon="download" loading={exporting} onClick={exportCsv}>Eksport (CSV)</Button>} />
      <DataTable caption="Audit hodisalari" selectable={false} columns={columns} rows={list.data?.items ?? []} pageSize={SIZE}
        loading={list.loading && !list.data} error={list.error} onRetry={list.reload}
        footer={list.data ? <Pager page={page} total={list.data.total} size={SIZE} onPage={setPage} /> : null}
        emptyTitle={any ? 'Filtr bo‘yicha hodisa yo‘q' : 'Hali hodisa yo‘q'} emptyDescription={any ? 'Filtrlarni o‘zgartirib ko‘ring.' : undefined}
        toolbar={<>
          <PeriodSelect value={range} onChange={(v) => reset(() => setRange(v))} />
          <Select size="sm" aria-label="Amal" value={action} onChange={(e) => reset(() => setAction(e.target.value))}
            options={[{ value: '', label: 'Barcha amallar' }, ...ACTIONS.map((a) => ({ value: a, label: a }))]} />
          <Input size="sm" icon="user-search" placeholder="Bajaruvchi (email/ID)" value={actor} style={{ width: 200 }} aria-label="Bajaruvchi"
            onChange={(e) => setActor(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') reset(() => setActorQ(actor.trim())) }} />
          <Input size="sm" icon="store" placeholder="Biznes ID" inputMode="numeric" value={biz} style={{ width: 130 }} aria-label="Biznes ID"
            onChange={(e) => setBiz(e.target.value.replace(/\D/g, ''))} onKeyDown={(e) => { if (e.key === 'Enter') reset(() => setBizQ(biz)) }} />
          {any && <Button size="sm" variant="ghost" icon="x" onClick={() => reset(() => { setRange(''); setActor(''); setActorQ(''); setAction(''); setBiz(''); setBizQ('') })}>Tozalash</Button>}
        </>} />
    </div>
  )
}
