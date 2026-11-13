import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, errorText, type Me } from '../api'
import {
  Badge, Button, Card, ChannelTag, CountdownChip, DataTable, Field, InlineAlert, Input, Modal, PrivacyGuard, Redacted, SkeletonText, Tabs, Textarea,
  formatDateTime, formatNumber, type Column, type GuardState,
} from '../components'
import { useShell } from '../shell'
import { AccountBadge, GrantBadge, LoadError, PageHeader } from './common'
import { channelId, sellerName } from './labels'
import { GrantViewer, RequestAccessModal, type Grant } from './Grants'
import type { SellerCard } from './Sellers'
import { useSellerStatusAction } from './useSellerStatusAction'
import { useLoad } from './useLoad'
import { useBusy } from './useBusy'

/** /sellers/:tg — sotuvchi kartasi: metadata, PrivacyGuard → so'rov → READ-ONLY → qayta yopilish, kirish tarixi. */
export function SellerDetail({ me }: { me: Me }) {
  const { tg } = useParams()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') ?? 'overview'
  const setTab = (t: string) => setParams(t === 'overview' ? {} : { tab: t }, { replace: true })
  const [dialog, setDialog] = useState(false)
  const [purgeOpen, setPurgeOpen] = useState(false)
  const nav = useNavigate()
  const { toast } = useShell()
  const statusAction = useSellerStatusAction()
  const id = Number(tg)
  const card = useLoad((signal) => api<SellerCard>(`/sellers/${id}`, { signal }), [id])
  const grants = useLoad((signal) => api<Grant[]>(`/grants?telegramUserId=${id}`, { signal }), [id])
  const s = card.data

  const mine = (grants.data ?? []).filter((g) => g.adminEmail === me.email)
  const active = mine.find((g) => g.status === 'APPROVED')
  const latest = mine[0]
  const guard: GuardState = !latest ? 'locked' : latest.status === 'REQUESTED' ? 'pending'
    : latest.status === 'DECLINED' ? 'denied' : latest.status === 'APPROVED' ? 'locked' : 'expired'

  // sotuvchi javobini kutayotganda har 10 soniyada tekshiramiz
  useEffect(() => {
    if (guard !== 'pending') return
    const t = window.setInterval(grants.reload, 10_000)
    return () => window.clearInterval(t)
  }, [guard, grants.reload])

  if (card.error && !s) return <LoadError error={card.error} onRetry={card.reload} title="Sotuvchi topilmadi" />
  if (!s) return <Card><SkeletonText lines={6} gap={14} /></Card>

  const label = sellerName(s)
  const canToggle = me.role === 'SUPERADMIN' && ['ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED'].includes(s.status)
  const cancelPending = async () => {
    if (!latest) return
    await api(`/grants/${latest.id}/revoke`, { method: 'POST' }).catch(() => undefined)
    toast({ tone: 'success', title: 'So‘rov bekor qilindi', audit: true })
    grants.reload()
  }

  const history: Column<Grant>[] = [
    { key: 'requestedAt', header: 'Sana', sortable: true, render: (g) => <span className="sf-num">{formatDateTime(g.requestedAt)}</span> },
    { key: 'adminEmail', header: 'Admin' },
    { key: 'purpose', header: 'Sabab', render: (g) => <span className="sf-truncate" title={g.purpose}>{g.purpose}</span> },
    { key: 'minutes', header: 'Muddat', align: 'right', render: (g) => <span className="sf-num">{g.minutes} daqiqa</span> },
    { key: 'status', header: 'Holat', render: (g) => <span className="sf-row"><GrantBadge status={g.status} />
      {g.status === 'APPROVED' && g.expiresAt && <CountdownChip expiresAt={g.expiresAt} onExpire={grants.reload} />}</span> },
  ]

  return (
    <div className="sf-page">
      <PageHeader title={label}
        meta={<div className="sf-page-header__meta"><AccountBadge status={s.status} /><span className="sf-id">{s.telegramUserId}</span>
          {s.displayName && s.businessName && <span className="sf-cell-sub">{s.displayName}</span>}</div>}
        actions={<>
          <Button icon="arrow-left" onClick={() => nav('/sellers')}>Sotuvchilar</Button>
          {me.role === 'SUPERADMIN' && (s.status === 'CLOSED' || s.status === 'DELETION_REQUESTED') &&
            <Button variant="danger" icon="trash-2" onClick={() => setPurgeOpen(true)}>To‘liq o‘chirish</Button>}
          {canToggle && !active && <Button variant={s.status === 'ACTIVE' ? 'danger' : 'secondary'} icon="ban"
            onClick={async () => { if (await statusAction(s.telegramUserId, s.status)) { card.reload(); grants.reload() } }}>
            {s.status === 'ACTIVE' ? 'Bloklash' : 'Qayta faollashtirish'}</Button>}
        </>} />
      {s.lastStatusChange && s.status !== 'ACTIVE' && <InlineAlert tone="warning" title="Holat o‘zgartirilgan">{s.lastStatusChange}</InlineAlert>}
      <Tabs label="Sotuvchi bo‘limlari" value={tab} onChange={setTab} items={[
        { value: 'overview', label: 'Umumiy' },
        { value: 'data', label: 'Mahsulotlar va hisoblashlar', icon: active ? 'lock-open' : 'lock' },
        { value: 'history', label: 'Kirish tarixi', count: grants.data?.length },
      ]} />

      {tab === 'overview' && (
        <div className="sf-grid-2 sf-grid-2--narrow">
          <Card title="Profil">
            <dl className="sf-dl">
              <dt>Biznes nomi</dt><dd>{s.businessName ?? '—'}</dd>
              <dt>Ko‘rinadigan ism</dt><dd>{s.displayName ?? '—'}</dd>
              <dt>Tarif</dt><dd>{s.planCode ? <Badge tone="brand" dot={false}>{s.planCode}</Badge> : '—'}</dd>
              <dt>Asosiy kanal</dt><dd>{s.primaryChannel ? <ChannelTag channel={channelId(s.primaryChannel)} /> : '—'}</dd>
              <dt>Ro‘yxatdan o‘tgan</dt><dd className="sf-num">{formatDateTime(s.createdAt)}</dd>
              <dt>Profil yaratilgan</dt><dd className="sf-num">{s.activatedAt ? formatDateTime(s.activatedAt) : '—'}</dd>
              <dt>Shartlar</dt><dd>{s.terms ? <><span className="sf-id">{s.terms.version}</span> <span className="sf-cell-sub">· {formatDateTime(s.terms.acceptedAt)}</span></> : '—'}</dd>
              {s.deletionRequestedAt && <><dt>O‘chirish so‘ralgan</dt><dd className="sf-num">{formatDateTime(s.deletionRequestedAt)}</dd></>}
            </dl>
          </Card>
          <Card title="Faollik" description="Tijoriy bo‘lmagan, agregat ko‘rsatkichlar">
            <dl className="sf-dl">
              <dt>Faol mahsulotlar</dt><dd className="sf-num">{formatNumber(s.productCount)}</dd>
              <dt>Saqlangan hisoblashlar</dt><dd className="sf-num">{formatNumber(s.savedCalculationCount)}</dd>
              <dt>Oxirgi faollik</dt><dd className="sf-num">{s.lastActivityAt ? formatDateTime(s.lastActivityAt) : '—'}</dd>
              <dt>Tannarx va foyda</dt><dd><Redacted label="Yashirin" /></dd>
            </dl>
          </Card>
        </div>
      )}

      {tab === 'data' && (active
        ? <GrantViewer key={active.id} grantId={active.id} sellerLabel={label} onClosed={() => grants.reload()} />
        : (
          <div className="sf-stack">
            <PrivacyGuard state={guard} fields={['Tannarx', 'Reja narxi', 'Foyda', 'Marja', 'Hisoblashlar']}
              canRequest={s.status === 'ACTIVE'} loading={grants.loading && !grants.data}
              onAction={guard === 'pending' ? cancelPending : () => setDialog(true)}
              actionLabel={guard === 'pending' ? 'So‘rovni bekor qilish' : undefined} />
            {s.status !== 'ACTIVE' && <InlineAlert tone="info">Faqat faol akkauntdan kirish so‘rash mumkin.</InlineAlert>}
            {guard === 'pending' && latest && <InlineAlert tone="info" title="Sotuvchi javobi kutilmoqda">
              So‘rov {formatDateTime(latest.requestedAt)} da yuborildi. Sahifa avtomatik yangilanadi; javob berilmasa 24 soatda bekor bo‘ladi.</InlineAlert>}
          </div>
        ))}

      {tab === 'history' && (
        <DataTable caption="Kirish tarixi" selectable={false} columns={history} rows={grants.data ?? []} pageSize={20}
          loading={grants.loading && !grants.data} error={grants.error} onRetry={grants.reload}
          emptyTitle="Kirish so‘rovlari bo‘lmagan" emptyDescription="Bu sotuvchidan hali tijoriy ma’lumotga ruxsat so‘ralmagan." />
      )}

      <PurgeModal open={purgeOpen} tg={s.telegramUserId} onClose={() => setPurgeOpen(false)}
        onDone={() => { setPurgeOpen(false); toast({ tone: 'success', title: 'Ma’lumotlar o‘chirildi', audit: true }); nav('/sellers') }} />
      <RequestAccessModal open={dialog} telegramUserId={s.telegramUserId} sellerLabel={label} onClose={() => setDialog(false)}
        onSent={() => { setDialog(false); toast({ tone: 'success', title: 'So‘rov yuborildi', description: 'Sotuvchi botda tasdiqlashi kerak.', audit: true }); grants.reload(); setTab('data') }} />
    </div>
  )
}

/** N-10: sotuvchi so'roviga ko'ra to'liq o'chirish — sabab va joriy parol (step-up), qaytarib bo'lmaydi. */
function PurgeModal({ open, tg, onClose, onDone }: { open: boolean; tg: number; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const [pass, setPass] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  const close = () => { setReason(''); setPass(''); setError(null); onClose() }
  const submit = () => run(async () => {
    if (reason.trim().length < 5) { setError('Sababni yozing (kamida 5 belgi).'); return }
    if (!pass) { setError('Joriy parolingizni kiriting.'); return }
    try {
      await api(`/sellers/${tg}/purge`, { method: 'POST', body: { reason: reason.trim(), confirmPassword: pass } })
      setReason(''); setPass(''); setError(null)
      onDone()
    } catch (e) {
      setError(errorText(e))
    }
  })
  return (
    <Modal open={open} onClose={close} title="Ma’lumotlarni to‘liq o‘chirish" icon="trash-2" tone="danger" width={520}
      description="Mahsulotlar, saqlangan hisoblashlar, profil va faollik izlari o‘chiriladi, Telegram ID ajratiladi. Bu amalni qaytarib bo‘lmaydi. Rozilik va audit yozuvlari dalil sifatida qoladi."
      footer={<><Button onClick={close}>Bekor qilish</Button><Button variant="danger" loading={busy} onClick={submit}>O‘chirish</Button></>}>
      <form className="sf-form" onSubmit={(e) => { e.preventDefault(); void submit() }}>
        {error && <InlineAlert tone="danger">{error}</InlineAlert>}
        <Field label="Sabab" hint="Masalan: sotuvchi qo‘llab-quvvatlash orqali to‘liq o‘chirishni so‘radi">
          <Textarea rows={2} value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Sizning joriy parolingiz">
          <Input type="password" autoComplete="current-password" value={pass} onChange={(e) => setPass(e.target.value)} />
        </Field>
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}
