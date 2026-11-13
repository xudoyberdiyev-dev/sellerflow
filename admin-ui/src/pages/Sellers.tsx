import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, download, errorText, type Me } from '../api'
import {
  Badge, Button, ChannelTag, DataTable, Drawer, Input, PrivacyGuard, Select, SkeletonText, Tabs,
  formatDate, formatDateTime, formatNumber, type Column,
} from '../components'
import { useShell } from '../shell'
import { AccountBadge, PageHeader, Pager, PeriodSelect } from './common'
import { ACCOUNT_STATUS, CHANNEL_OPTIONS, channelId, periodParams, sellerName, today } from './labels'
import { useSellerStatusAction } from './useSellerStatusAction'
import { useLoad } from './useLoad'

export interface SellerRow {
  telegramUserId: number; status: string; createdAt: string; deletionRequestedAt: string | null
  businessId: number | null; primaryChannel: string | null; planCode: string | null; activatedAt: string | null
}
export interface SellerCard extends SellerRow {
  businessName: string | null; displayName: string | null; terms: { version: string; acceptedAt: string } | null
  productCount: number; savedCalculationCount: number; lastActivityAt: string | null; lastStatusChange: string | null
}
interface Page { total: number; page: number; size: number; counts: Record<string, number>; items: SellerRow[] }

const SIZE = 25

export function Sellers({ me }: { me: Me }) {
  const [page, setPage] = useState(0)
  const [tab, setTab] = useState('all')
  const [q, setQ] = useState('')
  const [tg, setTg] = useState('')
  const [channel, setChannel] = useState('')
  const [range, setRange] = useState('')
  const [preview, setPreview] = useState<SellerRow | null>(null)
  const [exporting, setExporting] = useState(false)
  const nav = useNavigate()
  const { toast } = useShell()
  const statusAction = useSellerStatusAction()
  const superadmin = me.role === 'SUPERADMIN'

  const list = useLoad((signal) => {
    const p = new URLSearchParams({ page: String(page), size: String(SIZE) })
    if (tab !== 'all') p.set('status', tab)
    if (tg) p.set('telegramUserId', tg)
    if (channel) p.set('channel', channel)
    periodParams(range, p)
    return api<Page>(`/sellers?${p}`, { signal })
  }, [page, tab, tg, channel, range])
  const counts = list.data?.counts ?? {}
  const totalAll = Object.values(counts).reduce((a, b) => a + b, 0)
  const reset = (fn: () => void) => { fn(); setPage(0) }

  const exportCsv = async () => {
    setExporting(true)
    try {
      const r = await download('/sellers/export.csv', `sellerflow-sotuvchilar-${today()}.csv`)
      toast(r.truncated
        ? { tone: 'warning', title: 'Eksport qisqartirildi', description: 'Faqat eng yangi 10 000 qator yuklandi.', audit: true }
        : { tone: 'success', title: 'Eksport tayyor', description: 'Faqat metadata — tijoriy qiymatlar yo‘q.', audit: true })
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    } finally {
      setExporting(false)
    }
  }

  const columns: Column<SellerRow>[] = [
    { key: 'telegramUserId', header: 'Telegram ID', hideable: false, render: (r) => <span className="sf-id">{r.telegramUserId}</span> },
    { key: 'status', header: 'Holat', render: (r) => <AccountBadge status={r.status} /> },
    { key: 'primaryChannel', header: 'Asosiy kanal', render: (r) => (r.primaryChannel ? <ChannelTag channel={channelId(r.primaryChannel)} /> : <span className="sf-cell-sub">—</span>) },
    { key: 'planCode', header: 'Tarif', render: (r) => (r.planCode ? <Badge tone="brand" dot={false}>{r.planCode}</Badge> : <span className="sf-cell-sub">—</span>) },
    { key: 'createdAt', header: 'Ro‘yxatdan o‘tgan', sortable: true, render: (r) => <span className="sf-num">{formatDateTime(r.createdAt)}</span> },
    { key: 'activatedAt', header: 'Profil yaratilgan', defaultHidden: true, render: (r) => <span className="sf-num">{r.activatedAt ? formatDate(r.activatedAt) : '—'}</span> },
    { key: 'deletionRequestedAt', header: 'O‘chirish so‘ralgan', defaultHidden: true, render: (r) => <span className="sf-num">{r.deletionRequestedAt ? formatDate(r.deletionRequestedAt) : '—'}</span> },
  ]

  return (
    <div className="sf-page">
      <PageHeader title="Sotuvchilar" description="Bot foydalanuvchilari — faqat metadata. Tijoriy ma’lumot faqat sotuvchi roziligi bilan ochiladi."
        actions={superadmin && <Button icon="download" loading={exporting} onClick={exportCsv}>Eksport (CSV)</Button>} />
      <Tabs label="Holat bo‘yicha" value={tab} onChange={(v) => reset(() => setTab(v))} items={[
        { value: 'all', label: 'Hammasi', count: totalAll },
        ...['ACTIVE', 'ONBOARDING', 'SUSPENDED', 'DELETION_REQUESTED', 'CLOSED'].map((s) => ({ value: s, label: ACCOUNT_STATUS[s].label, count: counts[s] ?? 0 })),
      ]} />
      <DataTable caption="Sotuvchilar ro‘yxati" rowKey="telegramUserId" selectable={false} columns={columns} rows={list.data?.items ?? []} pageSize={SIZE}
        loading={list.loading && !list.data} error={list.error} onRetry={list.reload}
        emptyTitle={tg || channel || range ? 'Hech narsa topilmadi' : 'Hali sotuvchi yo‘q'}
        emptyDescription={tg || channel || range ? 'Filtrlarni o‘zgartirib ko‘ring.' : 'Sotuvchilar botda /start bosgach shu yerda paydo bo‘ladi.'}
        onRowClick={setPreview}
        footer={list.data ? <Pager page={page} total={list.data.total} size={SIZE} onPage={setPage} /> : null}
        toolbar={<>
          <Input size="sm" icon="search" placeholder="Telegram ID" inputMode="numeric" value={q} style={{ width: 200 }} aria-label="Telegram ID bo‘yicha qidirish"
            onChange={(e) => setQ(e.target.value.replace(/\D/g, ''))} onKeyDown={(e) => { if (e.key === 'Enter') reset(() => setTg(q)) }} />
          <Select size="sm" aria-label="Kanal" value={channel} onChange={(e) => reset(() => setChannel(e.target.value))}
            options={[{ value: '', label: 'Barcha kanallar' }, ...CHANNEL_OPTIONS]} />
          <PeriodSelect value={range} onChange={(v) => reset(() => setRange(v))} />
          {(tg || channel || range) && <Button size="sm" variant="ghost" icon="x" onClick={() => reset(() => { setQ(''); setTg(''); setChannel(''); setRange('') })}>Tozalash</Button>}
        </>}
        rowActions={(r) => [
          { label: 'Tafsilotlarni ochish', icon: 'panel-right-open', onSelect: () => setPreview(r) },
          { label: 'Sotuvchi kartasi', icon: 'arrow-up-right', onSelect: () => nav(`/sellers/${r.telegramUserId}`) },
          { label: 'Telegram ID nusxalash', icon: 'copy', onSelect: () => { void navigator.clipboard?.writeText(String(r.telegramUserId)) } },
          ...(superadmin && (r.status === 'ACTIVE' || r.status === 'SUSPENDED' || r.status === 'DELETION_REQUESTED')
            ? [{ separator: true as const }, { label: r.status === 'ACTIVE' ? 'Bloklash' : 'Qayta faollashtirish', icon: 'ban', danger: r.status === 'ACTIVE',
                onSelect: async () => { if (await statusAction(r.telegramUserId, r.status)) list.reload() } }] : []),
        ]} />
      <SellerDrawer row={preview} me={me} onClose={() => setPreview(null)} onChanged={() => { setPreview(null); list.reload() }} />
    </div>
  )
}

function SellerDrawer({ row, me, onClose, onChanged }: { row: SellerRow | null; me: Me; onClose: () => void; onChanged: () => void }) {
  const nav = useNavigate()
  const statusAction = useSellerStatusAction()
  const card = useLoad((signal) => (row ? api<SellerCard>(`/sellers/${row.telegramUserId}`, { signal }) : Promise.resolve(null)), [row?.telegramUserId])
  const c = card.data
  const canToggle = me.role === 'SUPERADMIN' && row && ['ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED'].includes(row.status)
  return (
    <Drawer open={!!row} onClose={onClose} title={c ? sellerName(c) : row ? `Sotuvchi ${row.telegramUserId}` : ''}
      subtitle={row && <span className="sf-row"><AccountBadge status={row.status} /><span className="sf-id">{row.telegramUserId}</span></span>}
      footer={row && <>
        <Button variant="primary" iconRight="arrow-up-right" onClick={() => nav(`/sellers/${row.telegramUserId}`)}>Kartani ochish</Button>
        {canToggle && <Button variant={row.status === 'ACTIVE' ? 'danger' : 'secondary'} icon="ban"
          onClick={async () => { if (await statusAction(row.telegramUserId, row.status)) onChanged() }}>
          {row.status === 'ACTIVE' ? 'Bloklash' : 'Qayta faollashtirish'}</Button>}
      </>}>
      {!c ? (card.error ? <p className="sf-cell-sub">{card.error}</p> : <SkeletonText lines={7} gap={14} />) : (
        <div className="sf-stack">
          <dl className="sf-dl">
            <dt>Tarif</dt><dd>{c.planCode ? <Badge tone="brand" dot={false}>{c.planCode}</Badge> : '—'}</dd>
            <dt>Asosiy kanal</dt><dd>{c.primaryChannel ? <ChannelTag channel={channelId(c.primaryChannel)} /> : '—'}</dd>
            <dt>Ro‘yxatdan o‘tgan</dt><dd className="sf-num">{formatDateTime(c.createdAt)}</dd>
            <dt>Oxirgi faollik</dt><dd className="sf-num">{c.lastActivityAt ? formatDateTime(c.lastActivityAt) : '—'}</dd>
            <dt>Mahsulotlar</dt><dd className="sf-num">{formatNumber(c.productCount)}</dd>
            <dt>Saqlangan hisoblashlar</dt><dd className="sf-num">{formatNumber(c.savedCalculationCount)}</dd>
            <dt>Shartlar versiyasi</dt><dd className="sf-id">{c.terms?.version ?? '—'}</dd>
          </dl>
          <PrivacyGuard fields={['Tannarx', 'Reja narxi', 'Foyda', 'Hisoblashlar']} canRequest={c.status === 'ACTIVE'}
            onAction={() => nav(`/sellers/${c.telegramUserId}?tab=data`)} actionLabel="Kirish so‘rash" />
        </div>
      )}
    </Drawer>
  )
}
