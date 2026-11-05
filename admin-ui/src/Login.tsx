import { useEffect, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { ShieldCheck } from 'lucide-react'
import { api, errorText, setToken, type Me } from './api'
import { Alert, Button, Field, Input } from './ui'

interface LoginResponse {
  status: 'OK' | 'TOTP_REQUIRED' | 'TOTP_SETUP_REQUIRED'
  token?: string
  setupToken?: string
  totpSecret?: string
  otpauthUri?: string
  admin?: Me
  mustChangePassword?: boolean
}

type Stage = { kind: 'password' } | { kind: 'code' } | { kind: 'setup'; setupToken: string; secret: string; uri: string }

export function Login({ onLogin }: { onLogin: (me: Me, mustChangePassword: boolean) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [stage, setStage] = useState<Stage>({ kind: 'password' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [qr, setQr] = useState<string | null>(null)

  useEffect(() => {
    if (stage.kind === 'setup') {
      QRCode.toDataURL(stage.uri, { margin: 1, width: 200 }).then(setQr).catch(() => setQr(null))
    }
  }, [stage])

  function finish(r: LoginResponse) {
    if (r.status === 'OK' && r.token && r.admin) {
      setToken(r.token)
      onLogin(r.admin, !!r.mustChangePassword)
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (stage.kind === 'setup') {
        finish(await api<LoginResponse>('/auth/totp/confirm', { method: 'POST', body: { setupToken: stage.setupToken, code } }))
        return
      }
      const r = await api<LoginResponse>('/auth/login', {
        method: 'POST',
        body: { email, password, code: stage.kind === 'code' ? code : undefined },
      })
      if (r.status === 'TOTP_REQUIRED') {
        setStage({ kind: 'code' })
        setCode('')
      } else if (r.status === 'TOTP_SETUP_REQUIRED') {
        setStage({ kind: 'setup', setupToken: r.setupToken!, secret: r.totpSecret!, uri: r.otpauthUri! })
        setCode('')
      } else {
        finish(r)
      }
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-stone-900 text-white">
            <ShieldCheck size={20} />
          </div>
          <h1 className="text-lg font-semibold">SellerFlow Admin</h1>
          <p className="text-sm text-stone-500">Faqat platforma administratorlari uchun</p>
        </div>
        <form onSubmit={submit} className="space-y-4 rounded-lg border border-stone-200 bg-white p-6">
          {error && <Alert>{error}</Alert>}
          {stage.kind === 'password' && (
            <>
              <Field label="Email">
                <Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
              </Field>
              <Field label="Parol">
                <Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
              </Field>
            </>
          )}
          {stage.kind === 'setup' && (
            <div className="space-y-3">
              <Alert kind="info">
                Ikki bosqichli himoya majburiy. Google Authenticator yoki Authy ilovasida QR kodni skanerlang va 6 xonali kodni kiriting.
              </Alert>
              {qr && <img src={qr} alt="2FA QR kod" className="mx-auto h-[200px] w-[200px]" />}
              <p className="break-all text-center font-mono text-xs text-stone-500">{stage.secret}</p>
            </div>
          )}
          {stage.kind !== 'password' && (
            <Field label="Tasdiqlash kodi" hint="Authenticator ilovasidagi 6 xonali kod">
              <Input
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="\d{6}"
                maxLength={6}
                required
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              />
            </Field>
          )}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? 'Tekshirilmoqda…' : stage.kind === 'password' ? 'Kirish' : 'Tasdiqlash'}
          </Button>
          {stage.kind !== 'password' && (
            <button type="button" className="w-full text-center text-xs text-stone-500 hover:text-stone-800" onClick={() => setStage({ kind: 'password' })}>
              Ortga
            </button>
          )}
        </form>
      </div>
    </div>
  )
}
