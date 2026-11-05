import { useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { Alert, Button, Card, Field, Input, PageTitle } from '../ui'

export function Profile({ onChanged }: { onChanged: () => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [msg, setMsg] = useState<{ kind: 'error' | 'success'; text: string } | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next !== repeat) { setMsg({ kind: 'error', text: 'Yangi parollar mos emas.' }); return }
    try {
      await api('/auth/password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
      setMsg({ kind: 'success', text: 'Parol almashtirildi.' })
      setCurrent(''); setNext(''); setRepeat('')
      onChanged()
    } catch (err) {
      setMsg({ kind: 'error', text: errorText(err) })
    }
  }

  return (
    <div className="max-w-md">
      <PageTitle title="Profil" desc="Parolni almashtirish" />
      {msg && <div className="mb-3"><Alert kind={msg.kind}>{msg.text}</Alert></div>}
      <Card>
        <form onSubmit={submit} className="space-y-3">
          <Field label="Joriy parol"><Input type="password" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
          <Field label="Yangi parol" hint="Kamida 12 belgi"><Input type="password" autoComplete="new-password" required minLength={12} value={next} onChange={(e) => setNext(e.target.value)} /></Field>
          <Field label="Yangi parol (takror)"><Input type="password" autoComplete="new-password" required minLength={12} value={repeat} onChange={(e) => setRepeat(e.target.value)} /></Field>
          <Button type="submit">Saqlash</Button>
        </form>
      </Card>
    </div>
  )
}
