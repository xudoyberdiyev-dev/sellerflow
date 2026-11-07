import { useState, type FormEvent } from 'react'
import { api, errorText, fmtDate, type Me } from '../api'
import { Alert, Badge, Button, Card, Field, Input, PageTitle, Select, Table, Td } from '../ui'
import { useLoad } from './useLoad'
import { useBusy } from './useBusy'
import { useShell } from '../shell'

interface Admin { id: number; email: string; role: string; status: string; totpEnabled: boolean; mustChangePassword: boolean; createdAt: string; lastLoginAt: string | null }

export function Admins({ me }: { me: Me }) {
  const list = useLoad((signal) => api<Admin[]>('/admins', { signal }))
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('SUPPORT')
  const [pass, setPass] = useState('')
  const [msg, setMsg] = useState<{ kind: 'error' | 'success'; text: string } | null>(null)
  const { busy, run } = useBusy()
  const { confirm, toast } = useShell()

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(async () => { try {
      await api('/admins', { method: 'POST', body: { email, role, tempPassword: pass } })
      setMsg({ kind: 'success', text: `Admin yaratildi. Vaqtinchalik parolni ${email} ga xavfsiz kanal orqali bering — birinchi kirishda 2FA ulanadi.` })
      setEmail(''); setPass('')
      list.reload()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    } })
  }

  async function act(id: number, action: 'disable' | 'enable' | 'reset-2fa') {
    // D-3: window.confirm o'rniga ConfirmDialog
    const ok = await confirm(action === 'disable'
      ? { title: 'Adminni o‘chirib qo‘yish', description: 'Admin panelga kira olmaydi. Barcha sessiyalari darhol yopiladi.', confirmLabel: 'O‘chirib qo‘yish', tone: 'danger' }
      : action === 'reset-2fa'
        ? { title: '2FA’ni qayta o‘rnatish', description: 'Keyingi kirishda yangi QR kod beriladi. Joriy sessiyalar yopiladi.', confirmLabel: '2FA’ni qayta o‘rnatish', tone: 'warning' }
        : { title: 'Adminni yoqish', description: 'Admin yana panelga kira oladi.', confirmLabel: 'Yoqish', tone: 'info' })
    if (ok === null) return
    await run(async () => { try {
      await api(`/admins/${id}/${action}`, { method: 'POST' })
      toast({ tone: 'success', title: action === 'disable' ? 'Admin o‘chirib qo‘yildi' : action === 'reset-2fa' ? '2FA qayta o‘rnatildi' : 'Admin yoqildi', audit: true })
      list.reload()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    } })
  }

  return (
    <div>
      <PageTitle title="Administratorlar" desc="SUPERADMIN — hamma amallar. SUPPORT — statistika, sotuvchilar ro‘yxati va o‘z kirish so‘rovlari." />
      {msg && <div className="mb-3"><Alert kind={msg.kind}>{msg.text}</Alert></div>}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Yangi admin">
          <form onSubmit={create} className="space-y-3">
            <Field label="Email"><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
            <Field label="Rol">
              <Select value={role} onChange={(e) => setRole(e.target.value)} className="w-full">
                <option value="SUPPORT">SUPPORT</option>
                <option value="SUPERADMIN">SUPERADMIN</option>
              </Select>
            </Field>
            <Field label="Vaqtinchalik parol" hint="Kamida 12 belgi. Admin birinchi kirishda almashtiradi.">
              <Input type="password" required minLength={12} autoComplete="new-password" value={pass} onChange={(e) => setPass(e.target.value)} />
            </Field>
            <Button type="submit" className="w-full" disabled={busy}>Yaratish</Button>
          </form>
        </Card>
        <Card title="Ro‘yxat" className="lg:col-span-2">
          {list.error && <Alert>{list.error}</Alert>}
          <Table head={['Email', 'Rol', 'Holat', '2FA', 'Oxirgi kirish', '']}>
            {list.data?.map((a) => (
              <tr key={a.id} className="hover:bg-stone-50">
                <Td>{a.email}{a.id === me.id && <span className="ml-1 text-xs text-stone-400">(siz)</span>}</Td>
                <Td>{a.role}</Td>
                <Td><Badge value={a.status} /></Td>
                <Td>{a.totpEnabled ? 'ulangan' : <span className="text-amber-700">ulanmagan</span>}</Td>
                <Td className="text-stone-500">{fmtDate(a.lastLoginAt)}</Td>
                <Td>
                  {a.id !== me.id && (
                    <div className="flex gap-1">
                      {a.status === 'ACTIVE'
                        ? <Button variant="ghost" onClick={() => act(a.id, 'disable')}>O‘chirish</Button>
                        : <Button variant="ghost" onClick={() => act(a.id, 'enable')}>Yoqish</Button>}
                      <Button variant="ghost" onClick={() => act(a.id, 'reset-2fa')}>2FA reset</Button>
                    </div>
                  )}
                </Td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>
    </div>
  )
}
