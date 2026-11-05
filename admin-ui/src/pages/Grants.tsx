import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Eye, XCircle } from 'lucide-react'
import { api, errorText, fmtDate, fmtMoney, type Me } from '../api'
import { Alert, Badge, Button, Card, Field, Input, PageTitle, Select, Table, Td, Textarea } from '../ui'
import { useLoad } from './useLoad'

interface Grant {
  id: number; telegramUserId: number; adminEmail: string; purpose: string; minutes: number; status: string
  requestedAt: string; decidedAt: string | null; expiresAt: string | null; revokedBy: string | null
}
interface GrantData {
  grant: Grant
  products: { id: number; name: string; unitCost: number; plannedSalePrice: number | null; sku: string | null }[]
  history: { id: number; type: string; productName: string | null; channel: string; accuracy: string; savedAt: string; output: Record<string, string> }[]
}

export function Grants({ me }: { me: Me }) {
  const [params] = useSearchParams()
  const [tg, setTg] = useState(params.get('tg') ?? '')
  const [purpose, setPurpose] = useState('')
  const [minutes, setMinutes] = useState('30')
  const [msg, setMsg] = useState<{ kind: 'error' | 'success' | 'info'; text: string } | null>(null)
  const [view, setView] = useState<GrantData | null>(null)
  const list = useLoad(() => api<Grant[]>('/grants'))

  async function request(e: FormEvent) {
    e.preventDefault()
    setMsg(null)
    try {
      await api('/grants', { method: 'POST', body: { telegramUserId: Number(tg), purpose, minutes: Number(minutes) } })
      setMsg({ kind: 'success', text: 'So‘rov yuborildi. Sotuvchi botda tasdiqlashi kerak.' })
      setPurpose('')
      list.reload()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    }
  }

  async function open(id: number) {
    setMsg(null)
    try {
      setView(await api<GrantData>(`/grants/${id}/data`))
    } catch (err) {
      setView(null)
      setMsg({ kind: 'error', text: errorText(err) })
    }
  }

  async function revoke(id: number) {
    if (!window.confirm('Ruxsatni bekor qilasizmi?')) return
    try {
      await api(`/grants/${id}/revoke`, { method: 'POST' })
      if (view?.grant.id === id) setView(null)
      list.reload()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    }
  }

  return (
    <div>
      <PageTitle
        title="Kirish so‘rovlari"
        desc="Sotuvchining tijoriy ma’lumotlarini ko‘rish faqat uning botdagi aniq roziligi bilan, 30–60 daqiqaga, faqat o‘qish uchun. Har bir ko‘rish auditga yoziladi."
      />
      {msg && <div className="mb-3"><Alert kind={msg.kind}>{msg.text}</Alert></div>}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Yangi so‘rov" className="lg:col-span-1">
          <form onSubmit={request} className="space-y-3">
            <Field label="Sotuvchi Telegram ID">
              <Input required value={tg} onChange={(e) => setTg(e.target.value.replace(/\D/g, ''))} />
            </Field>
            <Field label="Sabab" hint="Sotuvchiga aynan shu matn ko‘rsatiladi (kamida 10 belgi).">
              <Textarea required minLength={10} maxLength={300} value={purpose} onChange={(e) => setPurpose(e.target.value)} />
            </Field>
            <Field label="Muddat">
              <Select value={minutes} onChange={(e) => setMinutes(e.target.value)} className="w-full">
                <option value="30">30 daqiqa</option>
                <option value="45">45 daqiqa</option>
                <option value="60">60 daqiqa</option>
              </Select>
            </Field>
            <Button type="submit" className="w-full">So‘rov yuborish</Button>
          </form>
        </Card>
        <Card title={me.role === 'SUPERADMIN' ? 'Barcha so‘rovlar' : 'Mening so‘rovlarim'} className="lg:col-span-2">
          {list.error && <Alert>{list.error}</Alert>}
          <Table head={['#', 'Sotuvchi', 'Holat', 'Muddat', 'Tugaydi', 'Admin', '']} empty={!!list.data && list.data.length === 0}>
            {list.data?.map((g) => (
              <tr key={g.id} className="hover:bg-stone-50">
                <Td className="text-stone-500">{g.id}</Td>
                <Td className="font-mono">{g.telegramUserId}</Td>
                <Td><Badge value={g.status} /></Td>
                <Td>{g.minutes} daq.</Td>
                <Td>{fmtDate(g.expiresAt)}</Td>
                <Td className="max-w-[160px] truncate text-stone-500">{g.adminEmail}</Td>
                <Td>
                  <div className="flex gap-1">
                    {g.status === 'APPROVED' && g.adminEmail === me.email && (
                      <Button variant="ghost" title="Ma’lumotni ko‘rish" onClick={() => open(g.id)}><Eye size={14} /></Button>
                    )}
                    {(g.status === 'APPROVED' || g.status === 'REQUESTED') && (
                      <Button variant="ghost" title="Bekor qilish" onClick={() => revoke(g.id)}><XCircle size={14} /></Button>
                    )}
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>

      {view && (
        <Card
          className="mt-4"
          title={`READ-ONLY · sotuvchi ${view.grant.telegramUserId} · ${fmtDate(view.grant.expiresAt)} gacha`}
          actions={<Button variant="secondary" onClick={() => setView(null)}>Yopish</Button>}
        >
          <Alert kind="info">Bu ko‘rinish auditga yozildi. Ma’lumotni nusxalamang va uchinchi shaxslarga bermang.</Alert>
          <h3 className="mb-2 mt-4 text-sm font-semibold">Mahsulotlar (oxirgi 10 ta)</h3>
          <Table head={['Nomi', 'Tannarx', 'Reja narxi', 'SKU']} empty={view.products.length === 0}>
            {view.products.map((p) => (
              <tr key={p.id}><Td>{p.name}</Td><Td>{fmtMoney(p.unitCost)}</Td><Td>{fmtMoney(p.plannedSalePrice)}</Td><Td>{p.sku ?? '—'}</Td></tr>
            ))}
          </Table>
          <h3 className="mb-2 mt-6 text-sm font-semibold">Saqlangan hisoblashlar (oxirgi 10 ta)</h3>
          <Table head={['Sana', 'Tur', 'Mahsulot', 'Natija', 'Aniqlik']} empty={view.history.length === 0}>
            {view.history.map((h) => (
              <tr key={h.id}>
                <Td>{fmtDate(h.savedAt)}</Td>
                <Td>{h.type === 'PROFIT' ? 'Foyda' : 'Narx'}</Td>
                <Td>{h.productName ?? 'Nomsiz'}</Td>
                <Td>{h.type === 'PROFIT' ? `foyda ${fmtMoney(h.output.profit)} · marja ${h.output.marginPct}%` : `narx ${fmtMoney(h.output.recommendedPrice)}`}</Td>
                <Td>{h.accuracy === 'PARTIAL_ESTIMATE' ? 'taxminiy' : 'to‘liq'}</Td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
    </div>
  )
}
