import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, errorText, type Me } from '../api'
import {
  Badge, Button, Card, ChannelTag, CountdownChip, DataTable, EmptyState, Field, Icon, InlineAlert, Input, Modal,
  ReadOnlyBanner, SegmentedControl, SkeletonText, Tabs, TelegramPreview, Textarea,
  formatDateTime, formatSom, type Column,
} from '../components'
import { useShell } from '../shell'
import { GrantBadge, LoadError, PageHeader, Pager } from './common'
import { GRANT_STATUS, channelId } from './labels'
import { useBusy } from './useBusy'
import { useLoad } from './useLoad'

export interface Grant {
  id: number; telegramUserId: number; adminEmail: string; purpose: string; minutes: number; status: string
  requestedAt: string; decidedAt: string | null; expiresAt: string | null; revokedBy: string | null
}
interface PageInfo { page: number; size: number; total: number; totalPages: number }
interface GrantData {
  grant: Grant
  productPage: PageInfo; historyPage: PageInfo
  products: { id: number; name: string; unitCost: number | string; plannedSalePrice: number | string | null; sku: string | null }[]
  history: { id: number; type: string; productName: string | null; channel: string; accuracy: string; savedAt: string; output: Record<string, unknown> | null }[]
}

const money = (v: unknown) => {
  const n = Number(v)
  return v === null || v === undefined || v === '' || !Number.isFinite(n) ? '—' : formatSom(n)
}

// ------------------------------------------------------------------ so'rov dialogi

/** D-6: kirish so'rovi — sabab (sotuvchiga aynan shu matn), muddat 30/45/60, Telegram ko'rinishi. */
export function RequestAccessModal({ open, telegramUserId, sellerLabel, onClose, onSent }:
  { open: boolean; telegramUserId?: number; sellerLabel?: string; onClose: () => void; onSent: (g: Grant) => void }) {
  const [tg, setTg] = useState('')
  const [reason, setReason] = useState('')
  const [dur, setDur] = useState('30')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  useEffect(() => { if (open) { setReason(''); setDur('30'); setTouched(false); setError(null); setTg(telegramUserId ? String(telegramUserId) : '') } }, [open, telegramUserId])
  const bad = reason.trim().length < 10
  const badTg = !/^\d{1,19}$/.test(tg)
  const send = () => run(async () => {
    setTouched(true)
    if (bad || badTg) return
    try {
      const g = await api<Grant>('/grants', { method: 'POST', body: { telegramUserId: Number(tg), purpose: reason.trim(), minutes: Number(dur) } })
      onSent(g)
    } catch (e) {
      setError(errorText(e))
    }
  })
  const preview = `🔐 Qo‘llab-quvvatlash so‘rovi\n\nSellerFlow administratori sizning mahsulotlaringiz va saqlangan hisoblashlaringizni faqat ko‘rish (o‘zgartirmasdan) uchun ${dur} daqiqaga ruxsat so‘ramoqda.\n\nSabab: ${reason.trim() || '—'}\n\nRuxsat bermasangiz, hech narsa ko‘rsatilmaydi. Ruxsatni istalgan payt bekor qilishingiz mumkin.`
  return (
    <Modal open={open} onClose={onClose} tone="privacy" icon="lock" width={680} title="Tijoriy ma’lumotga kirish so‘rash"
      description={`${sellerLabel ?? 'Sotuvchi'} Telegram botda so‘rovni ko‘radi va ruxsat berishi yoki rad etishi mumkin.`}
      footer={<>
        <span className="sf-modal__audit"><Icon name="scroll-text" size={14} />So‘rov va har bir ko‘rish auditga yoziladi</span>
        <Button onClick={onClose}>Bekor qilish</Button>
        <Button variant="privacy" icon="send" loading={busy} onClick={send}>So‘rov yuborish</Button>
      </>}>
      <div className="sf-split">
        <div className="sf-form">
          {error && <InlineAlert tone="danger">{error}</InlineAlert>}
          {!telegramUserId && (
            <Field label="Sotuvchi Telegram ID" error={touched && badTg ? 'Telegram ID faqat raqamlardan iborat.' : null}>
              <Input inputMode="numeric" value={tg} onChange={(e) => setTg(e.target.value.replace(/\D/g, ''))} placeholder="Masalan: 123456789" />
            </Field>
          )}
          <Field label="Sabab" hint="Sotuvchi bu matnni Telegramda ko‘radi." error={touched && bad ? 'Sababni yozing — kamida 10 ta belgi kerak.' : null}>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Masalan: hisoblashdagi xatoni birga tekshirish" maxLength={300} rows={3} />
          </Field>
          <div className="sf-field">
            <span className="sf-field__label">Muddat</span>
            <SegmentedControl label="Muddat" value={dur} onChange={setDur}
              options={[{ value: '30', label: '30 daqiqa' }, { value: '45', label: '45 daqiqa' }, { value: '60', label: '60 daqiqa' }]} />
          </div>
          <InlineAlert tone="privacy" title="Faqat o‘qish">Ruxsat berilsa ham ma’lumotni o‘zgartirib yoki eksport qilib bo‘lmaydi. Ruxsat faqat sizga beriladi.</InlineAlert>
        </div>
        <TelegramPreview time="hozir" text={preview} keyboard={[[{ text: '✅ Ruxsat berish' }, { text: '❌ Rad etish' }]]} />
      </div>
    </Modal>
  )
}

// ------------------------------------------------------------------ READ-ONLY ko'rinish

/** D-6: READ-ONLY ko'rinish. Muddat tugasa yoki «Tugatish» bosilsa ma'lumot ekrandan darhol olib tashlanadi. */
export function GrantViewer({ grantId, sellerLabel, onClosed }: { grantId: number; sellerLabel?: string; onClosed: (reason: 'expired' | 'ended' | 'forbidden') => void }) {
  const [pp, setPp] = useState(0)
  const [hp, setHp] = useState(0)
  const [gone, setGone] = useState(false)
  const { confirm, toast } = useShell()
  const data = useLoad((signal) => api<GrantData>(`/grants/${grantId}/data?productPage=${pp}&historyPage=${hp}`, { signal }), [grantId, pp, hp])
  useEffect(() => {
    if (data.error && !gone) { setGone(true); onClosed('forbidden') }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data.error])
  if (gone) return null
  const d = data.data
  if (!d) return <Card><SkeletonText lines={6} gap={14} /></Card>

  const end = async () => {
    const ok = await confirm({ title: 'Kirishni tugatish', description: 'Ma’lumot darhol yashiriladi. Qayta ko‘rish uchun sotuvchidan yangi rozilik kerak bo‘ladi.', confirmLabel: 'Tugatish', tone: 'privacy' })
    if (ok === null) return
    try {
      await api(`/grants/${grantId}/revoke`, { method: 'POST' })
      toast({ tone: 'success', title: 'Kirish tugatildi', audit: true })
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    }
    setGone(true)
    onClosed('ended')
  }
  const expire = () => { setGone(true); onClosed('expired') }

  const products: Column<GrantData['products'][number]>[] = [
    { key: 'name', header: 'Mahsulot', hideable: false },
    { key: 'sku', header: 'SKU', render: (r) => <span className="sf-id">{r.sku ?? '—'}</span> },
    { key: 'unitCost', header: 'Tannarx', align: 'right', render: (r) => <span className="sf-num">{money(r.unitCost)}</span> },
    { key: 'plannedSalePrice', header: 'Reja narxi', align: 'right', render: (r) => <span className="sf-num">{money(r.plannedSalePrice)}</span> },
  ]
  const history: Column<GrantData['history'][number]>[] = [
    { key: 'savedAt', header: 'Sana', render: (r) => <span className="sf-num">{formatDateTime(r.savedAt)}</span> },
    { key: 'type', header: 'Tur', render: (r) => (r.type === 'PROFIT' ? 'Foyda hisobi' : 'Narx hisobi') },
    { key: 'productName', header: 'Mahsulot', render: (r) => r.productName ?? 'Nomsiz' },
    { key: 'channel', header: 'Kanal', render: (r) => <ChannelTag channel={channelId(r.channel)} /> },
    { key: 'result', header: 'Natija', align: 'right', render: (r) => {
      const o = r.output ?? {}
      if (r.type === 'PROFIT') {
        const p = Number(o.profit)
        return <span className={`sf-num${p < 0 ? ' sf-text-danger' : ''}`}>{money(o.profit)}{o.marginPct != null ? ` · ${String(o.marginPct).replace('.', ',').replace(/^-/, '−')}%` : ''}</span>
      }
      return <span className="sf-num">{money(o.recommendedPrice)}</span>
    } },
    { key: 'accuracy', header: 'Aniqlik', render: (r) => (r.accuracy === 'PARTIAL_ESTIMATE' ? <Badge tone="warning">Taxminiy</Badge> : <Badge tone="success">To‘liq</Badge>) },
  ]
  return (
    <div className="sf-stack">
      <ReadOnlyBanner sellerName={sellerLabel ?? `Sotuvchi ${d.grant.telegramUserId}`} expiresAt={d.grant.expiresAt} onEnd={end} onExpire={expire} />
      <Card title="Mahsulotlar" description="Faqat o‘qish · sotuvchi roziligi bilan ochilgan" padded={false}
        actions={<Badge tone="privacy" icon="eye">Faqat ko‘rish</Badge>}>
        <DataTable caption="Sotuvchi mahsulotlari" selectable={false} columns={products} rows={d.products} pageSize={50}
          footer={<MiniPager info={d.productPage} onPage={setPp} />}
          emptyTitle="Faol mahsulot yo‘q" emptyDescription="Sotuvchi hali mahsulot qo‘shmagan." />
      </Card>
      <Card title="Saqlangan hisoblashlar" padded={false}>
        <DataTable caption="Saqlangan hisoblashlar" selectable={false} columns={history} rows={d.history} pageSize={50}
          footer={<MiniPager info={d.historyPage} onPage={setHp} />}
          emptyTitle="Saqlangan hisoblash yo‘q" />
      </Card>
    </div>
  )
}

function MiniPager({ info, onPage }: { info: PageInfo; onPage: (p: number) => void }) {
  return <Pager page={info.page} total={info.total} size={info.size} onPage={onPage} />
}

// ------------------------------------------------------------------ ro'yxat sahifasi

const TABS: Record<string, string> = { all: '', REQUESTED: 'REQUESTED', APPROVED: 'APPROVED', closed: 'DECLINED,REVOKED,EXPIRED' }

export function Grants({ me }: { me: Me }) {
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState('all')
  const [seller, setSeller] = useState(params.get('tg') ?? '')
  const [sellerQ, setSellerQ] = useState(params.get('tg') ?? '')
  const [dialog, setDialog] = useState(false)
  const nav = useNavigate()
  const { confirm, toast } = useShell()
  const superadmin = me.role === 'SUPERADMIN'
  const all = useLoad((signal) => api<Grant[]>(`/grants${sellerQ ? `?telegramUserId=${sellerQ}` : ''}`, { signal }), [sellerQ])
  const rows = (all.data ?? []).filter((g) => !TABS[tab] || TABS[tab].split(',').includes(g.status))
  const count = (k: string) => (all.data ?? []).filter((g) => !TABS[k] || TABS[k].split(',').includes(g.status)).length

  // kutilayotgan so'rov bo'lsa holat har 15 soniyada yangilanadi (sotuvchi javobi)
  useEffect(() => {
    if (!all.data?.some((g) => g.status === 'REQUESTED')) return
    const t = window.setInterval(all.reload, 15_000)
    return () => window.clearInterval(t)
  }, [all.data, all.reload])

  const revoke = async (g: Grant) => {
    const ok = await confirm({ title: g.status === 'REQUESTED' ? 'So‘rovni bekor qilish' : 'Ruxsatni bekor qilish',
      description: `Sotuvchi ${g.telegramUserId} uchun #${g.id} ruxsat darhol yopiladi.`, confirmLabel: 'Bekor qilish', tone: 'danger' })
    if (ok === null) return
    try {
      await api(`/grants/${g.id}/revoke`, { method: 'POST' })
      toast({ tone: 'success', title: 'Bekor qilindi', audit: true })
      all.reload()
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    }
  }
  const applySeller = () => {
    setSellerQ(seller)
    setParams(seller ? { tg: seller } : {})
  }

  const columns: Column<Grant>[] = [
    { key: 'id', header: '#', render: (g) => <span className="sf-id">{g.id}</span> },
    { key: 'telegramUserId', header: 'Sotuvchi', hideable: false, render: (g) => <span className="sf-id">{g.telegramUserId}</span> },
    { key: 'purpose', header: 'Sabab', render: (g) => <span className="sf-truncate" title={g.purpose}>{g.purpose}</span> },
    ...(superadmin ? [{ key: 'adminEmail', header: 'Admin', render: (g: Grant) => <span className="sf-truncate">{g.adminEmail}{g.adminEmail === me.email ? ' (siz)' : ''}</span> }] : []),
    { key: 'minutes', header: 'Muddat', align: 'right', render: (g) => <span className="sf-num">{g.minutes} daq.</span> },
    { key: 'requestedAt', header: 'So‘ralgan', sortable: true, render: (g) => <span className="sf-num">{formatDateTime(g.requestedAt)}</span> },
    { key: 'status', header: 'Holat', render: (g) => (
      <span className="sf-row">
        <GrantBadge status={g.status} />
        {g.status === 'APPROVED' && g.expiresAt && <CountdownChip expiresAt={g.expiresAt} onExpire={all.reload} />}
      </span>) },
  ]

  return (
    <div className="sf-page">
      <PageHeader title="Kirish so‘rovlari"
        description="Tijoriy ma’lumotni ko‘rish faqat sotuvchining botdagi aniq roziligi bilan, 30–60 daqiqaga, faqat o‘qish uchun. Har bir ko‘rish auditga yoziladi."
        actions={<Button variant="privacy" icon="send" onClick={() => setDialog(true)}>Yangi so‘rov</Button>} />
      <Tabs label="Holat bo‘yicha" value={tab} onChange={setTab} items={[
        { value: 'all', label: 'Hammasi', count: count('all') },
        { value: 'REQUESTED', label: GRANT_STATUS.REQUESTED.label, count: count('REQUESTED') },
        { value: 'APPROVED', label: 'Faol', count: count('APPROVED') },
        { value: 'closed', label: 'Yopilgan', count: count('closed') },
      ]} />
      <DataTable caption="Kirish so‘rovlari" selectable={false} columns={columns} rows={rows} pageSize={20}
        loading={all.loading && !all.data} error={all.error} onRetry={all.reload}
        emptyTitle={sellerQ ? 'Bu sotuvchi uchun so‘rov yo‘q' : 'Kirish so‘rovlari yo‘q'}
        emptyDescription="Sotuvchi kartasidan yoki «Yangi so‘rov» tugmasi orqali so‘rov yuboring."
        onRowClick={(g) => (g.status === 'APPROVED' && g.adminEmail === me.email ? nav(`/grants/${g.id}`) : nav(`/sellers/${g.telegramUserId}`))}
        toolbar={<>
          <Input size="sm" icon="search" placeholder="Sotuvchi Telegram ID" inputMode="numeric" value={seller} style={{ width: 220 }}
            onChange={(e) => setSeller(e.target.value.replace(/\D/g, ''))} onKeyDown={(e) => { if (e.key === 'Enter') applySeller() }} aria-label="Sotuvchi Telegram ID" />
          <Button size="sm" onClick={applySeller}>Qidirish</Button>
          {sellerQ && <Button size="sm" variant="ghost" icon="x" onClick={() => { setSeller(''); setSellerQ(''); setParams({}) }}>Tozalash</Button>}
        </>}
        rowActions={(g) => [
          ...(g.status === 'APPROVED' && g.adminEmail === me.email ? [{ label: 'Ma’lumotni ko‘rish', icon: 'eye', onSelect: () => nav(`/grants/${g.id}`) }] : []),
          { label: 'Sotuvchi kartasi', icon: 'arrow-up-right', onSelect: () => nav(`/sellers/${g.telegramUserId}`) },
          ...((g.status === 'APPROVED' || g.status === 'REQUESTED') && (superadmin || g.adminEmail === me.email)
            ? [{ separator: true as const }, { label: 'Bekor qilish', icon: 'circle-x', danger: true, onSelect: () => revoke(g) }] : []),
        ]} />
      <RequestAccessModal open={dialog} onClose={() => setDialog(false)}
        onSent={(g) => { setDialog(false); toast({ tone: 'success', title: 'So‘rov yuborildi', description: `Sotuvchi ${g.telegramUserId} botda tasdiqlashi kerak.`, audit: true }); all.reload() }} />
    </div>
  )
}

/** /grants/:id — READ-ONLY ko'rish sahifasi. */
export function AccessViewer() {
  const { id } = useParams()
  const nav = useNavigate()
  const [closed, setClosed] = useState<null | 'expired' | 'ended' | 'forbidden'>(null)
  const grantId = Number(id)
  if (!Number.isFinite(grantId)) return <LoadError error="So‘rov topilmadi." />
  return (
    <div className="sf-page">
      <PageHeader title="Tijoriy ma’lumot · faqat ko‘rish" description={`Kirish ruxsati #${grantId}. Ma’lumotni nusxalamang va uchinchi shaxslarga bermang.`}
        actions={<Button icon="arrow-left" onClick={() => nav('/grants')}>Kirish so‘rovlari</Button>} />
      {closed ? (
        <Card>
          <EmptyState kind="privacy" title={closed === 'expired' ? 'Kirish muddati tugadi' : closed === 'ended' ? 'Kirish tugatildi' : 'Ruxsat faol emas'}
            description="Ma’lumot yana yashirildi. Davom etish uchun sotuvchidan yangi rozilik oling."
            actions={<Button onClick={() => nav('/grants')}>Ro‘yxatga qaytish</Button>} />
        </Card>
      ) : <GrantViewer grantId={grantId} onClosed={setClosed} />}
    </div>
  )
}

