import { useEffect, useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { Alert, Button, Card, Field, Input, PageTitle } from '../ui'
import { useBusy } from './useBusy'

interface Support { enabled: boolean; label: string | null; url: string | null }

export function SupportPage() {
  const [s, setS] = useState<Support>({ enabled: false, label: '', url: '' })
  const [msg, setMsg] = useState<{ kind: 'error' | 'success'; text: string } | null>(null)
  const { busy, run } = useBusy()

  useEffect(() => {
    api<Support>('/support').then((d) => setS({ enabled: d.enabled, label: d.label ?? '', url: d.url ?? '' })).catch((e) => setMsg({ kind: 'error', text: errorText(e) }))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    await run(async () => { try {
      const d = await api<Support>('/support', { method: 'PUT', body: s })
      setS({ enabled: d.enabled, label: d.label ?? '', url: d.url ?? '' })
      setMsg({ kind: 'success', text: 'Saqlandi. Botdagi /help da darhol ko‘rinadi.' })
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    } })
  }

  return (
    <div className="max-w-xl">
      <PageTitle title="Qo‘llab-quvvatlash kontakti" desc="Botdagi /help bo‘limida ko‘rsatiladi. O‘chiq bo‘lsa — hech qanday havola ko‘rsatilmaydi." />
      {msg && <div className="mb-3"><Alert kind={msg.kind}>{msg.text}</Alert></div>}
      <Card>
        <form onSubmit={save} className="space-y-4">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={s.enabled} onChange={(e) => setS({ ...s, enabled: e.target.checked })} className="h-4 w-4" />
            Kontakt yoqilgan
          </label>
          <Field label="Tugma matni"><Input value={s.label ?? ''} maxLength={80} onChange={(e) => setS({ ...s, label: e.target.value })} placeholder="Qo‘llab-quvvatlash" /></Field>
          <Field label="Havola" hint="@username yoki https:// bilan boshlanadigan havola">
            <Input value={s.url ?? ''} onChange={(e) => setS({ ...s, url: e.target.value })} placeholder="@sellerflow_support" />
          </Field>
          <Button type="submit" disabled={busy}>Saqlash</Button>
        </form>
      </Card>
    </div>
  )
}
