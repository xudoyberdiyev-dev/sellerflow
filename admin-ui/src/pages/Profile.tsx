import { useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { Button, Card, Field, InlineAlert, Input } from '../components'
import { useBusy } from './useBusy'

/** Profil va majburiy parol almashtirish (M-5: vaqtinchalik parol bilan faqat shu sahifa ochiq). D-4. */
export function Profile({ onChanged, forced = false }: { onChanged: () => void; forced?: boolean }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [msg, setMsg] = useState<{ kind: 'error' | 'success'; text: string } | null>(null)
  const { busy, run } = useBusy()

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next !== repeat) { setMsg({ kind: 'error', text: 'Yangi parollar mos emas.' }); return }
    await run(async () => { try {
      await api('/auth/password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
      setMsg({ kind: 'success', text: 'Parol almashtirildi.' })
      setCurrent(''); setNext(''); setRepeat('')
      onChanged()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    } })
  }

  return (
    <div style={{ maxWidth: 440 }}>
      <h1 style={{ font: 'var(--type-h2, 600 20px/28px var(--font-sans))', margin: '0 0 4px' }}>{forced ? 'Parolni almashtiring' : 'Profil'}</h1>
      <p style={{ margin: '0 0 16px', color: 'var(--text-secondary)' }}>
        {forced ? 'Vaqtinchalik parol bilan kirdingiz. Davom etish uchun yangi parol o‘rnating.' : 'Parolni almashtirish'}
      </p>
      {msg && <div style={{ marginBottom: 12 }}><InlineAlert tone={msg.kind === 'error' ? 'danger' : 'success'}>{msg.text}</InlineAlert></div>}
      <Card>
        <form onSubmit={submit} style={{ display: 'grid', gap: 12 }}>
          <Field label="Joriy parol"><Input type="password" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
          <Field label="Yangi parol" hint="12–72 belgi"><Input type="password" autoComplete="new-password" required minLength={12} value={next} onChange={(e) => setNext(e.target.value)} /></Field>
          <Field label="Yangi parol (takror)"><Input type="password" autoComplete="new-password" required minLength={12} value={repeat} onChange={(e) => setRepeat(e.target.value)} /></Field>
          <div><Button type="submit" variant="primary" loading={busy} disabled={busy}>Saqlash</Button></div>
        </form>
      </Card>
    </div>
  )
}
