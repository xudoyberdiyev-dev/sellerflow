import { useState, type FormEvent } from 'react'
import { api, errorText, type Me } from '../api'
import { Badge, Button, Card, EmptyState, Field, Icon, InlineAlert, Input, SkeletonText, formatDateTime } from '../components'
import { useShell } from '../shell'
import { PageHeader } from './common'
import { ROLE_LABEL } from './labels'
import { useBusy } from './useBusy'
import { useLoad } from './useLoad'

interface Session { createdAt: string; expiresAt: string; current: boolean }

/** D-6: profil va xavfsizlik — parol, 2FA holati, faol sessiyalar. M-5: vaqtinchalik parolda faqat parol formasi. */
export function Profile({ me, onChanged, forced = false }: { me: Me; onChanged: () => void; forced?: boolean }) {
  return (
    <div className="sf-page">
      <PageHeader title={forced ? 'Parolni almashtiring' : 'Profil va xavfsizlik'}
        description={forced ? 'Vaqtinchalik parol bilan kirdingiz. Davom etish uchun yangi parol o‘rnating.' : 'Hisobingiz, parol va faol sessiyalar.'} />
      <div className="sf-grid-2 sf-grid-2--narrow">
        {!forced && (
          <Card title="Hisob">
            <dl className="sf-dl">
              <dt>Email</dt><dd>{me.email}</dd>
              <dt>Rol</dt><dd><Badge tone={me.role === 'SUPERADMIN' ? 'brand' : 'info'} dot={false}>{ROLE_LABEL[me.role] ?? me.role}</Badge></dd>
              <dt>Ikki bosqichli himoya</dt><dd><span className="sf-row" style={{ color: 'var(--success-text)' }}><Icon name="shield-check" size={14} />Yoqilgan (TOTP)</span></dd>
            </dl>
          </Card>
        )}
        <PasswordCard onChanged={onChanged} />
      </div>
      {!forced && <SessionsCard />}
    </div>
  )
}

function PasswordCard({ onChanged }: { onChanged: () => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  const { toast } = useShell()
  const len = new TextEncoder().encode(next).length
  const nextBad = next.length < 12 || len > 72
  const repeatBad = repeat !== next
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    if (nextBad || repeatBad || !current) return
    void run(async () => {
      try {
        await api('/auth/password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
        setCurrent(''); setNext(''); setRepeat(''); setTouched(false); setError(null)
        toast({ tone: 'success', title: 'Parol almashtirildi', description: 'Boshqa qurilmalardagi sessiyalar yopildi.', audit: true })
        onChanged()
      } catch (err) {
        setError(errorText(err))
      }
    })
  }
  return (
    <Card title="Parolni almashtirish">
      <form className="sf-form" onSubmit={submit}>
        {error && <InlineAlert tone="danger">{error}</InlineAlert>}
        <Field label="Joriy parol" error={touched && !current ? 'Joriy parolni kiriting.' : null}>
          <Input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label="Yangi parol" hint="12–72 belgi. Uzun ibora (passphrase) tavsiya etiladi." error={touched && nextBad ? 'Parol 12–72 belgi bo‘lishi kerak.' : null}>
          <Input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="Yangi parol (takror)" error={touched && repeatBad ? 'Parollar mos emas.' : null}>
          <Input type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
        </Field>
        <div className="sf-form__actions"><Button type="submit" variant="primary" loading={busy}>Saqlash</Button></div>
      </form>
    </Card>
  )
}

function SessionsCard() {
  const list = useLoad((signal) => api<Session[]>('/auth/sessions', { signal }))
  const { confirm, toast } = useShell()
  const others = (list.data ?? []).filter((s) => !s.current).length
  const revoke = async () => {
    const ok = await confirm({ title: 'Boshqa sessiyalarni yopish', description: 'Bu qurilmadan tashqari barcha qurilmalarda paneldan chiqiladi.', confirmLabel: 'Yopish', tone: 'warning' })
    if (ok === null) return
    try {
      const r = await api<{ revoked: number }>('/auth/sessions/revoke-others', { method: 'POST' })
      toast({ tone: 'success', title: `${r.revoked} ta sessiya yopildi`, audit: true })
      list.reload()
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
    }
  }
  return (
    <Card title="Faol sessiyalar" description="Sessiya 8 soat amal qiladi. Qurilma ma’lumoti saqlanmaydi." padded={false}
      actions={<Button size="sm" icon="log-out" disabled={others === 0} onClick={revoke}>Boshqalarini yopish</Button>}>
      {list.loading && !list.data ? <div style={{ padding: 16 }}><SkeletonText lines={3} gap={14} /></div>
        : list.error ? <EmptyState kind="error" title="Sessiyalarni yuklab bo‘lmadi" description={list.error} actions={<Button icon="rotate-cw" onClick={list.reload}>Qayta urinish</Button>} />
        : (
          <div className="sf-list">
            {(list.data ?? []).map((s, i) => (
              <div key={i} className="sf-list__item">
                <Icon name={s.current ? 'monitor' : 'laptop'} size={18} />
                <div className="sf-cell-stack sf-list__main">
                  <span>{s.current ? 'Shu qurilma' : 'Boshqa qurilma'}</span>
                  <span className="sf-cell-sub sf-num">Kirgan: {formatDateTime(s.createdAt)} · tugaydi: {formatDateTime(s.expiresAt)}</span>
                </div>
                {s.current && <Badge tone="success">Joriy</Badge>}
              </div>
            ))}
          </div>
        )}
    </Card>
  )
}
