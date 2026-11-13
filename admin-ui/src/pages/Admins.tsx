import { useState } from 'react'
import { api, errorText, type Me } from '../api'
import {
  Avatar, Badge, Button, DataTable, Field, Icon, InlineAlert, Input, Modal, SegmentedControl, formatDateTime, type Column,
} from '../components'
import { useShell } from '../shell'
import { PageHeader } from './common'
import { ADMIN_STATUS, ROLE_LABEL } from './labels'
import { useBusy } from './useBusy'
import { useLoad } from './useLoad'

interface Admin { id: number; email: string; role: string; status: string; totpEnabled: boolean; mustChangePassword: boolean; createdAt: string; lastLoginAt: string | null }

/** D-6: administratorlar — «o'chirish» = DISABLED (qattiq o'chirish yo'q, audit izi saqlanadi). */
export function Admins({ me }: { me: Me }) {
  const list = useLoad((signal) => api<Admin[]>('/admins', { signal }))
  const [dialog, setDialog] = useState(false)
  const { confirm, toast } = useShell()

  const [stepUp, setStepUp] = useState<Admin | null>(null)
  const act = async (a: Admin, action: 'disable' | 'enable' | 'reset-2fa') => {
    if (action === 'reset-2fa') { setStepUp(a); return } // step-up: joriy parol so'raladi
    const ok = await confirm(action === 'disable'
      ? { title: 'Adminni o‘chirib qo‘yish', description: `${a.email} panelga kira olmaydi, barcha sessiyalari darhol yopiladi. Hisob va audit izi saqlanadi — keyin qayta yoqish mumkin.`, confirmLabel: 'O‘chirib qo‘yish', tone: 'danger' }
      : { title: 'Adminni yoqish', description: `${a.email} yana panelga kira oladi.`, confirmLabel: 'Yoqish', tone: 'info' })
    if (ok === null) return
    try {
      await api(`/admins/${a.id}/${action}`, { method: 'POST' })
      toast({ tone: 'success', title: action === 'disable' ? 'Admin o‘chirib qo‘yildi' : 'Admin yoqildi', audit: true })
      list.reload()
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    }
  }

  const columns: Column<Admin>[] = [
    { key: 'email', header: 'Administrator', hideable: false, sortable: true, render: (a) => (
      <span className="sf-row" style={{ flexWrap: 'nowrap' }}><Avatar name={a.email} size={28} />
        <span className="sf-cell-stack"><span style={{ fontWeight: 500 }}>{a.email}{a.id === me.id ? ' (siz)' : ''}</span>
          {a.mustChangePassword && <span className="sf-cell-sub">Vaqtinchalik parol</span>}</span></span>) },
    { key: 'role', header: 'Rol', sortable: true, render: (a) => <Badge tone={a.role === 'SUPERADMIN' ? 'brand' : 'info'} dot={false}>{ROLE_LABEL[a.role] ?? a.role}</Badge> },
    { key: 'status', header: 'Holat', render: (a) => <Badge tone={ADMIN_STATUS[a.status]?.tone ?? 'neutral'}>{ADMIN_STATUS[a.status]?.label ?? a.status}</Badge> },
    { key: 'totpEnabled', header: '2FA', render: (a) => (a.totpEnabled
      ? <span className="sf-row" style={{ color: 'var(--success-text)' }}><Icon name="shield-check" size={14} />Ulangan</span>
      : <span className="sf-row" style={{ color: 'var(--warning-text)' }}><Icon name="shield-alert" size={14} />Ulanmagan</span>) },
    { key: 'lastLoginAt', header: 'Oxirgi kirish', sortable: true, render: (a) => <span className="sf-num">{a.lastLoginAt ? formatDateTime(a.lastLoginAt) : '—'}</span> },
    { key: 'createdAt', header: 'Yaratilgan', defaultHidden: true, render: (a) => <span className="sf-num">{formatDateTime(a.createdAt)}</span> },
  ]

  return (
    <div className="sf-page">
      <PageHeader title="Administratorlar" description="Superadmin — barcha amallar. Support — statistika, sotuvchilar ro‘yxati va o‘z kirish so‘rovlari."
        actions={<Button variant="primary" icon="user-plus" onClick={() => setDialog(true)}>Admin qo‘shish</Button>} />
      <DataTable caption="Administratorlar" selectable={false} columns={columns} rows={list.data ?? []} pageSize={25}
        loading={list.loading && !list.data} error={list.error} onRetry={list.reload} emptyTitle="Administrator yo‘q"
        rowActions={(a) => (a.id === me.id ? [{ label: 'Bu sizning hisobingiz', icon: 'user-round', disabled: true }] : [
          a.status === 'ACTIVE'
            ? { label: 'O‘chirib qo‘yish', icon: 'user-x', danger: true, onSelect: () => act(a, 'disable') }
            : { label: 'Yoqish', icon: 'user-check', onSelect: () => act(a, 'enable') },
          { label: '2FA’ni qayta o‘rnatish', icon: 'rotate-ccw-key', onSelect: () => act(a, 'reset-2fa') },
        ])} />
      <StepUpModal admin={stepUp} onClose={() => setStepUp(null)} onDone={() => {
        setStepUp(null)
        toast({ tone: 'success', title: '2FA qayta o‘rnatildi', audit: true })
        list.reload()
      }} />
      <CreateAdminModal open={dialog} onClose={() => setDialog(false)} onCreated={(email) => {
        setDialog(false)
        toast({ tone: 'success', title: 'Admin yaratildi', description: `Vaqtinchalik parolni ${email} ga xavfsiz kanal orqali bering. Birinchi kirishda parol almashtiriladi va 2FA ulanadi.`, audit: true })
        list.reload()
      }} />
    </div>
  )
}

function CreateAdminModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (email: string) => void }) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('SUPPORT')
  const [pass, setPass] = useState('')
  const [mine, setMine] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  const emailBad = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
  const passBad = pass.length < 12 || new TextEncoder().encode(pass).length > 72
  const close = () => { setEmail(''); setPass(''); setMine(''); setRole('SUPPORT'); setTouched(false); setError(null); onClose() }
  const submit = () => run(async () => {
    setTouched(true)
    if (emailBad || passBad || !mine) return
    try {
      await api('/admins', { method: 'POST', body: { email: email.trim(), role, tempPassword: pass, confirmPassword: mine } })
      const e = email.trim()
      setEmail(''); setPass(''); setMine(''); setRole('SUPPORT'); setTouched(false); setError(null)
      onCreated(e)
    } catch (err) {
      setError(errorText(err))
    }
  })
  return (
    <Modal open={open} onClose={close} title="Yangi administrator" icon="user-plus" tone="info" width={520}
      footer={<><Button onClick={close}>Bekor qilish</Button><Button variant="primary" loading={busy} onClick={submit}>Yaratish</Button></>}>
      <form className="sf-form" onSubmit={(e) => { e.preventDefault(); void submit() }}>
        {error && <InlineAlert tone="danger">{error}</InlineAlert>}
        <Field label="Email" error={touched && emailBad ? 'To‘g‘ri email kiriting.' : null}>
          <Input type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="ism@kompaniya.uz" />
        </Field>
        <div className="sf-field">
          <span className="sf-field__label">Rol</span>
          <SegmentedControl label="Rol" value={role} onChange={setRole} options={[{ value: 'SUPPORT', label: 'Support' }, { value: 'SUPERADMIN', label: 'Superadmin' }]} />
          <div className="sf-field__hint">{role === 'SUPERADMIN' ? 'Barcha amallar, jumladan adminlar va audit.' : 'Statistika, sotuvchilar ro‘yxati va o‘z kirish so‘rovlari.'}</div>
        </div>
        <Field label="Vaqtinchalik parol" hint="12–72 belgi. Admin birinchi kirishda almashtiradi."
          error={touched && passBad ? 'Parol 12–72 belgi bo‘lishi kerak.' : null}>
          <Input type="password" autoComplete="new-password" value={pass} onChange={(e) => setPass(e.target.value)} />
        </Field>
        <Field label="Sizning joriy parolingiz" hint="Xavfsizlik uchun: yangi admin yaratishni tasdiqlang." error={touched && !mine ? 'Joriy parolingizni kiriting.' : null}>
          <Input type="password" autoComplete="current-password" value={mine} onChange={(e) => setMine(e.target.value)} />
        </Field>
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}

/** Step-up: boshqa adminning 2FA'sini qayta o'rnatishdan oldin joriy parol. */
function StepUpModal({ admin, onClose, onDone }: { admin: Admin | null; onClose: () => void; onDone: () => void }) {
  const [mine, setMine] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  const close = () => { setMine(''); setError(null); onClose() }
  const submit = () => run(async () => {
    if (!admin || !mine) { setError('Joriy parolingizni kiriting.'); return }
    try {
      await api(`/admins/${admin.id}/reset-2fa`, { method: 'POST', body: { confirmPassword: mine } })
      setMine(''); setError(null)
      onDone()
    } catch (e) {
      setError(errorText(e))
    }
  })
  return (
    <Modal open={!!admin} onClose={close} title="2FA’ni qayta o‘rnatish" icon="rotate-ccw-key" tone="warning" width={480}
      description={admin ? `${admin.email} keyingi kirishda yangi QR kodni skanerlaydi. Joriy sessiyalari yopiladi.` : undefined}
      footer={<><Button onClick={close}>Bekor qilish</Button><Button variant="primary" loading={busy} onClick={submit}>Tasdiqlash</Button></>}>
      <form className="sf-form" onSubmit={(e) => { e.preventDefault(); void submit() }}>
        {error && <InlineAlert tone="danger">{error}</InlineAlert>}
        <Field label="Sizning joriy parolingiz">
          <Input type="password" autoComplete="current-password" value={mine} onChange={(e) => setMine(e.target.value)} autoFocus />
        </Field>
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}
