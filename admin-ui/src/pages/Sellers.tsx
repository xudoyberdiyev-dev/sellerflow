import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Ban, KeyRound, RotateCcw, Search } from 'lucide-react'
import { api, errorText, fmtDate, type Me } from '../api'
import { Alert, Badge, Button, Card, Input, PageTitle, Select, Table, Td } from '../ui'
import { useLoad } from './useLoad'
import { useBusy } from './useBusy'
import { useShell } from '../shell'

interface Seller {
  telegramUserId: number; status: string; createdAt: string; deletionRequestedAt: string | null
  businessId: number | null; primaryChannel: string | null; planCode: string | null; activatedAt: string | null
}
interface Page { total: number; page: number; size: number; items: Seller[] }

const SIZE = 25

export function Sellers({ me }: { me: Me }) {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [query, setQuery] = useState('')
  const [tgId, setTgId] = useState('')
  const [msg, setMsg] = useState<{ kind: 'error' | 'success'; text: string } | null>(null)
  const nav = useNavigate()
  const { run } = useBusy()
  const { confirm, toast } = useShell()
  const list = useLoad((signal) => {
    const q = new URLSearchParams({ page: String(page), size: String(SIZE) })
    if (status) q.set('status', status)
    if (tgId) q.set('telegramUserId', tgId)
    return api<Page>(`/sellers?${q}`, { signal })
  }, [page, status, tgId])
  const superadmin = me.role === 'SUPERADMIN'

  async function act(id: number, action: 'suspend' | 'reactivate') {
    // D-3: window.prompt o'rniga ConfirmDialog — sabab majburiy (kamida 10 belgi), auditga yoziladi
    const reason = await confirm(action === 'suspend'
      ? { title: 'Sotuvchini bloklash', description: `Sotuvchi ${id} botdan foydalana olmaydi. Ma’lumotlari saqlanadi, faol kirish ruxsatlari bekor qilinadi.`, confirmLabel: 'Bloklash', tone: 'danger', requireReason: true }
      : { title: 'Qayta faollashtirish', description: `Sotuvchi ${id} botdan yana foydalana oladi.`, confirmLabel: 'Faollashtirish', tone: 'warning', requireReason: true })
    if (reason === null) return
    await run(async () => { try {
      await api(`/sellers/${id}/${action}`, { method: 'POST', body: { reason } })
      toast({ tone: 'success', title: action === 'suspend' ? 'Sotuvchi bloklandi' : 'Sotuvchi qayta faollashtirildi', audit: true })
      setMsg(null)
      list.reload()
    } catch (e) {
      setMsg({ kind: 'error', text: errorText(e) })
    } })
  }

  const d = list.data
  const pages = d ? Math.max(1, Math.ceil(d.total / SIZE)) : 1
  return (
    <div>
      <PageTitle title="Sotuvchilar" desc="Faqat metadata: holat, kanal, tarif va sanalar. Tannarx, foyda va tarix ko‘rsatilmaydi." />
      {msg && <div className="mb-3"><Alert kind={msg.kind}>{msg.text}</Alert></div>}
      <Card
        title={d ? `Jami: ${d.total}` : 'Ro‘yxat'}
        actions={
          <form className="flex items-center gap-2" onSubmit={(e) => { e.preventDefault(); setPage(0); setTgId(query.trim()) }}>
            <Select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value) }} aria-label="Holat">
              <option value="">Barcha holatlar</option>
              <option value="ACTIVE">Faol</option>
              <option value="ONBOARDING">Ro‘yxatdan o‘tmoqda</option>
              <option value="SUSPENDED">Bloklangan</option>
              <option value="DELETION_REQUESTED">O‘chirish so‘ralgan</option>
              <option value="CLOSED">Yopilgan</option>
            </Select>
            <Input placeholder="Telegram ID" value={query} onChange={(e) => setQuery(e.target.value.replace(/\D/g, ''))} className="w-36" />
            <Button variant="secondary" type="submit" aria-label="Qidirish"><Search size={14} /></Button>
          </form>
        }
      >
        {list.error && <Alert>{list.error}</Alert>}
        <Table head={['Telegram ID', 'Holat', 'Kanal', 'Tarif', 'Ro‘yxatdan o‘tgan', 'Amallar']} empty={!!d && d.items.length === 0}>
          {d?.items.map((s) => (
            <tr key={s.telegramUserId} className="hover:bg-stone-50">
              <Td className="font-mono">{s.telegramUserId}</Td>
              <Td>
                <Badge value={s.status} />
                {s.deletionRequestedAt && <div className="mt-0.5 text-xs text-stone-500">so‘rov: {fmtDate(s.deletionRequestedAt)}</div>}
              </Td>
              <Td>{s.primaryChannel ?? '—'}</Td>
              <Td>{s.planCode ?? '—'}</Td>
              <Td>{fmtDate(s.activatedAt ?? s.createdAt)}</Td>
              <Td>
                <div className="flex gap-1">
                  {s.status === 'ACTIVE' && (
                    <Button variant="ghost" title="Kirish so‘rovi" onClick={() => nav(`/grants?tg=${s.telegramUserId}`)}><KeyRound size={14} /></Button>
                  )}
                  {superadmin && s.status === 'ACTIVE' && (
                    <Button variant="ghost" title="Bloklash" onClick={() => act(s.telegramUserId, 'suspend')}><Ban size={14} /></Button>
                  )}
                  {superadmin && (s.status === 'SUSPENDED' || s.status === 'DELETION_REQUESTED') && (
                    <Button variant="ghost" title="Qayta faollashtirish" onClick={() => act(s.telegramUserId, 'reactivate')}><RotateCcw size={14} /></Button>
                  )}
                </div>
              </Td>
            </tr>
          ))}
        </Table>
        <div className="mt-4 flex items-center justify-between text-sm text-stone-500">
          <span>Sahifa {page + 1} / {pages}</span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Oldingi</Button>
            <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Keyingi</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
