import React, { useState } from 'react';

export function LoginScreen({ onDone }) {
  const [step, setStep] = useState('creds');
  const [email, setEmail] = useState('aziz@sellerflow.uz');
  const [pw, setPw] = useState('••••••••••');
  const [code, setCode] = useState('');
  const [err, setErr] = useState(null);
  const [loading, setLoading] = useState(false);

  const submitCreds = (e) => {
    e.preventDefault();
    if (!email.includes('@')) { setErr('Email noto‘g‘ri. «nom@sellerflow.uz» ko‘rinishida kiriting.'); return; }
    setErr(null); setLoading(true);
    setTimeout(() => { setLoading(false); setStep('otp'); }, 600);
  };
  const verify = (v) => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      if (v === '000000') { setErr('Kod noto‘g‘ri. Autentifikator ilovasidagi yangi kodni kiriting.'); setCode(''); }
      else onDone();
    }, 700);
  };

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: 'var(--bg-app)' }}>
      <div style={{ width: '100%', maxWidth: 400, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Logo size={32} />
        <div className="sf-card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 20 }}>
          {step === 'creds' ? (
            <form onSubmit={submitCreds} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <h1 style={{ font: 'var(--type-h2)', margin: 0 }}>Tizimga kirish</h1>
                <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)' }}>Faqat SellerFlow xodimlari uchun.</p>
              </div>
              <Field label="Email" error={err}><Input icon="mail" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
              <Field label="Parol"><Input icon="key-round" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" /></Field>
              <Button type="submit" variant="primary" loading={loading} style={{ width: '100%' }}>Davom etish</Button>
            </form>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <h1 style={{ font: 'var(--type-h2)', margin: 0 }}>2FA tasdiqlash</h1>
                <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)' }}>Autentifikator ilovasidagi 6 xonali kodni kiriting. Xatoni ko‘rish uchun <span className="sf-mono">000000</span> kiriting.</p>
              </div>
              <OtpInput value={code} onChange={(v) => { setCode(v); setErr(null); }} onComplete={verify} invalid={!!err} disabled={loading} autoFocus />
              {err && <div className="sf-field__error" role="alert"><Icon name="circle-alert" size={14} />{err}</div>}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <Button variant="ghost" icon="arrow-left" onClick={() => { setStep('creds'); setErr(null); }}>Orqaga</Button>
                <div style={{ flex: 1 }} />
                <Button variant="primary" loading={loading} disabled={code.length < 6} onClick={() => verify(code)}>Tasdiqlash</Button>
              </div>
            </div>
          )}
        </div>
        <p style={{ margin: 0, font: 'var(--type-small)', color: 'var(--text-tertiary)', display: 'flex', gap: 6, alignItems: 'center' }}>
          <Icon name="scroll-text" size={14} />Har bir kirish auditga yoziladi.
        </p>
      </div>
    </div>
  );
}
