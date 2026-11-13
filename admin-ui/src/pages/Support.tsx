import { useEffect, useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { Button, Card, Field, InlineAlert, Input, SkeletonText, Switch, TelegramPreview, formatDateTime } from '../components'
import { useShell } from '../shell'
import { LoadError, PageHeader } from './common'
import { useBusy } from './useBusy'
import { useLoad } from './useLoad'

interface Support { enabled: boolean; label: string | null; url: string | null; updatedBy?: string | null; updatedAt?: string | null }

const URL_OK = /^(https:\/\/\S+|@[A-Za-z0-9_]{5,32})$/

/** D-6: qo'llab-quvvatlash kontakti — bot /help ko'rinishi bilan; oxirgi o'zgartirgan admin va vaqt. */
export function SupportPage() {
  const loaded = useLoad((signal) => api<Support>('/support', { signal }))
  const [s, setS] = useState<Support | null>(null)
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { busy, run } = useBusy()
  const { toast } = useShell()
  useEffect(() => { if (loaded.data) setS({ ...loaded.data, label: loaded.data.label ?? '', url: loaded.data.url ?? '' }) }, [loaded.data])

  if (loaded.error && !s) return <LoadError error={loaded.error} onRetry={loaded.reload} />
  if (!s) return <Card><SkeletonText lines={5} gap={14} /></Card>

  const urlBad = s.enabled && !URL_OK.test((s.url ?? '').trim())
  const dirty = !!loaded.data && (s.enabled !== loaded.data.enabled || (s.label ?? '') !== (loaded.data.label ?? '') || (s.url ?? '') !== (loaded.data.url ?? ''))
  const save = (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    if (urlBad) return
    void run(async () => {
      try {
        const d = await api<Support>('/support', { method: 'PUT', body: { enabled: s.enabled, label: (s.label ?? '').trim() || null, url: (s.url ?? '').trim() || null } })
        setS({ ...d, label: d.label ?? '', url: d.url ?? '' })
        setError(null)
        setTouched(false)
        loaded.reload()
        toast({ tone: 'success', title: 'Saqlandi', description: 'Botdagi /help bo‘limida darhol ko‘rinadi.', audit: true })
      } catch (err) {
        setError(errorText(err))
      }
    })
  }
  const label = (s.label ?? '').trim() || 'Qo‘llab-quvvatlash'
  const ok = s.enabled && !urlBad
  // bot InfoHandler bilan bir xil: HELP matni + (yoqilgan bo'lsa) «📞 Qo'llab-quvvatlash: <label>» va URL tugma
  const helpText = '❓ Yordam\n\nSavolni tanlang. Asosiy bo‘limlar pastdagi menyuda.\nBuyruqlar: /menu — bosh menyu, /cancel — jarayonni bekor qilish, /settings — sozlamalar, /delete — akkaunt va ma’lumotlarni o‘chirish.'
    + (ok ? `\n\n📞 Qo‘llab-quvvatlash: ${label}` : '')
  const faq = [[{ text: 'Foyda qanday hisoblanadi?' }], [{ text: 'Marja nima?' }]]

  return (
    <div className="sf-page">
      <PageHeader title="Qo‘llab-quvvatlash" description="Botdagi /help bo‘limida ko‘rsatiladigan kontakt. O‘chiq bo‘lsa — hech qanday havola ko‘rsatilmaydi."
        meta={loaded.data?.updatedAt && <div className="sf-page-header__meta"><span className="sf-cell-sub">
          Oxirgi o‘zgartirish: {loaded.data.updatedBy ?? '—'} · {formatDateTime(loaded.data.updatedAt)}</span></div>} />
      <div className="sf-grid-side">
        <Card title="Kontakt sozlamalari">
          <form className="sf-form" onSubmit={save}>
            {error && <InlineAlert tone="danger">{error}</InlineAlert>}
            <Switch label="Kontakt yoqilgan" description="Yoqilsa, /help ostida tugma paydo bo‘ladi." checked={s.enabled}
              onChange={(e) => setS({ ...s, enabled: e.target.checked })} />
            <Field label="Tugma matni" optional hint="Ko‘pi bilan 80 belgi.">
              <Input value={s.label ?? ''} maxLength={80} onChange={(e) => setS({ ...s, label: e.target.value })} placeholder="Qo‘llab-quvvatlash" />
            </Field>
            <Field label="Havola" hint="@username yoki https:// bilan boshlanadigan havola"
              error={touched && urlBad ? 'Havola https:// bilan boshlanishi yoki @username (5–32 belgi) bo‘lishi kerak.' : null}>
              <Input icon="link" value={s.url ?? ''} onChange={(e) => setS({ ...s, url: e.target.value })} placeholder="@sellerflow_support" />
            </Field>
            <div className="sf-form__actions">
              <Button disabled={!dirty || busy} onClick={() => loaded.data && setS({ ...loaded.data, label: loaded.data.label ?? '', url: loaded.data.url ?? '' })}>Bekor qilish</Button>
              <Button type="submit" variant="primary" icon="save" loading={busy} disabled={!dirty}>Saqlash</Button>
            </div>
          </form>
        </Card>
        <TelegramPreview caption="Botdagi /help ko‘rinishi" text={helpText} time="hozir"
          keyboard={ok ? [...faq, [{ text: `📞 ${label}` }]] : faq} />
      </div>
    </div>
  )
}
