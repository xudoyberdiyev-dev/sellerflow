import { useEffect, useRef, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { ApiError, api, errorText, setToken, takeSessionExpired, type Me } from './api'
import { Button, Field, InlineAlert, Input, LogoMark, OtpInput } from './components'

interface LoginResponse {
  status: 'OK' | 'TOTP_REQUIRED' | 'TOTP_SETUP_REQUIRED'
  token?: string
  setupToken?: string
  totpSecret?: string
  otpauthUri?: string
  admin?: Me
  mustChangePassword?: boolean
}

type Stage =
  | { kind: 'password' }
  | { kind: 'code' }
  | { kind: 'setup'; step: 1 | 2; setupToken: string; secret: string; uri: string }
  | { kind: 'locked'; reason: 'LOCKED' | 'TOO_MANY_ATTEMPTS' }

const card: React.CSSProperties = { padding: 24, display: 'grid', gap: 16 }

/** D-4: Kirish — parol, 2FA ulash (QR → kod), OTP, bloklanish holati, sessiya tugashi xabari. */
export function Login({ onLogin }: { onLogin: (me: Me, mustChangePassword: boolean) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [stage, setStage] = useState<Stage>({ kind: 'password' })
  const [error, setError] = useState<string | null>(null)
  const [expired] = useState(() => takeSessionExpired())
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const [qr, setQr] = useState<string | null>(null)

  const setupUri = stage.kind === 'setup' ? stage.uri : null
  useEffect(() => {
    if (!setupUri) return
    QRCode.toDataURL(setupUri, { margin: 1, width: 200 }).then(setQr).catch(() => setQr(null))
  }, [setupUri])

  function finish(r: LoginResponse) {
    if (r.status === 'OK' && r.token && r.admin) {
      setToken(r.token)
      onLogin(r.admin, !!r.mustChangePassword)
    }
  }

  async function send(otp?: string) {
    if (lock.current) return // ikki marta yuborilmasin
    lock.current = true
    setError(null)
    setBusy(true)
    try {
      if (stage.kind === 'setup') {
        finish(await api<LoginResponse>('/auth/totp/confirm', { method: 'POST', body: { setupToken: stage.setupToken, code: otp ?? code } }))
        return
      }
      const r = await api<LoginResponse>('/auth/login', {
        method: 'POST',
        body: { email, password, code: stage.kind === 'code' ? otp ?? code : undefined },
      })
      if (r.status === 'TOTP_REQUIRED') {
        setStage({ kind: 'code' })
        setCode('')
      } else if (r.status === 'TOTP_SETUP_REQUIRED') {
        setStage({ kind: 'setup', step: 1, setupToken: r.setupToken!, secret: r.totpSecret!, uri: r.otpauthUri! })
        setCode('')
      } else {
        finish(r)
      }
    } catch (err) {
      if (err instanceof ApiError && (err.code === 'LOCKED' || err.code === 'TOO_MANY_ATTEMPTS')) {
        setStage({ kind: 'locked', reason: err.code })
      } else {
        setError(errorText(err))
        setCode('')
      }
    } finally {
      lock.current = false
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    void send()
  }

  const back = () => { setStage({ kind: 'password' }); setCode(''); setError(null) }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16, background: 'var(--bg-app)' }}>
      <div style={{ width: '100%', maxWidth: 400 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ display: 'inline-flex', marginBottom: 12 }}><LogoMark size={40} /></div>
          <h1 style={{ font: 'var(--type-h2, 600 20px/28px var(--font-sans))', margin: 0 }}>SellerFlow Admin</h1>
          <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)' }}>Faqat platforma administratorlari uchun</p>
        </div>
        <form onSubmit={submit} className="sf-card" style={card} aria-label="Kirish">
          {expired && stage.kind === 'password' && (
            <InlineAlert tone="warning" title="Sessiya tugadi">Xavfsizlik uchun qaytadan kiring.</InlineAlert>
          )}
          {error && <InlineAlert tone="danger">{error}</InlineAlert>}

          {stage.kind === 'password' && (
            <>
              <Field label="Email">
                <Input type="email" autoComplete="username" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
              </Field>
              <Field label="Parol">
                <Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
              </Field>
              <Button type="submit" variant="primary" loading={busy} disabled={busy} style={{ width: '100%' }}>Kirish</Button>
            </>
          )}

          {stage.kind === 'code' && (
            <>
              <div>
                <strong>Ikki bosqichli tasdiqlash</strong>
                <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)' }}>Authenticator ilovasidagi 6 xonali kodni kiriting.</p>
              </div>
              <OtpInput value={code} onChange={setCode} onComplete={(v) => void send(v)} disabled={busy} invalid={!!error} autoFocus />
              <Button type="submit" variant="primary" loading={busy} disabled={busy || code.length !== 6} style={{ width: '100%' }}>Tasdiqlash</Button>
              <Button variant="ghost" onClick={back}>Ortga</Button>
            </>
          )}

          {stage.kind === 'setup' && (
            <>
              <ol className="sf-steps" aria-label="2FA ulash bosqichlari" style={{ display: 'flex', gap: 12, margin: 0, padding: 0, listStyle: 'none', color: 'var(--text-secondary)', fontSize: 13 }}>
                <li style={{ fontWeight: stage.step === 1 ? 600 : 400, color: stage.step === 1 ? 'var(--text-primary)' : undefined }}>1. QR kodni skanerlash</li>
                <li style={{ fontWeight: stage.step === 2 ? 600 : 400, color: stage.step === 2 ? 'var(--text-primary)' : undefined }}>2. Kodni tasdiqlash</li>
              </ol>
              {stage.step === 1 ? (
                <>
                  <InlineAlert tone="info">Ikki bosqichli himoya majburiy. Google Authenticator yoki Authy ilovasida QR kodni skanerlang.</InlineAlert>
                  {qr && <img src={qr} alt="2FA QR kod" width={200} height={200} style={{ margin: '0 auto', display: 'block' }} />}
                  <div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Skanerlab bo'lmasa, kalitni qo'lda kiriting:</div>
                    <code className="sf-mono" data-testid="totp-secret" style={{ wordBreak: 'break-all', fontSize: 13 }}>{stage.secret}</code>
                  </div>
                  <Button variant="primary" onClick={() => setStage({ ...stage, step: 2 })} style={{ width: '100%' }}>Davom etish</Button>
                </>
              ) : (
                <>
                  <p style={{ margin: 0, color: 'var(--text-secondary)' }}>Ilovada paydo bo'lgan 6 xonali kodni kiriting.</p>
                  <OtpInput value={code} onChange={setCode} onComplete={(v) => void send(v)} disabled={busy} invalid={!!error} autoFocus />
                  <Button type="submit" variant="primary" loading={busy} disabled={busy || code.length !== 6} style={{ width: '100%' }}>2FA ni yoqish</Button>
                  <Button variant="ghost" onClick={() => setStage({ ...stage, step: 1 })}>QR kodga qaytish</Button>
                </>
              )}
            </>
          )}

          {stage.kind === 'locked' && (
            <>
              <InlineAlert tone="danger" title={stage.reason === 'LOCKED' ? 'Akkaunt vaqtincha bloklandi' : 'Juda ko‘p urinish'}>
                {stage.reason === 'LOCKED'
                  ? 'Ko‘p marta noto‘g‘ri parol yoki kod kiritildi. 15 daqiqadan keyin qayta urinib ko‘ring. Bu siz bo‘lmasangiz — SUPERADMIN’ga xabar bering.'
                  : 'Bu qurilmadan juda ko‘p xato urinish bo‘ldi. 15 daqiqadan keyin qayta urinib ko‘ring.'}
              </InlineAlert>
              <Button variant="secondary" onClick={back}>Kirish sahifasiga qaytish</Button>
            </>
          )}
        </form>
      </div>
    </div>
  )
}
