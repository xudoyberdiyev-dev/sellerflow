import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api, getToken, setToken, type Me } from './api'
import { Login } from './Login'
import { Dashboard } from './pages/Dashboard'
import { SystemStatus } from './pages/SystemStatus'
import { Sellers } from './pages/Sellers'
import { SellerDetail } from './pages/SellerDetail'
import { AccessViewer, Grants } from './pages/Grants'
import { ErrorBoundary } from './pages/common'
import { Notifications } from './Notifications'
import { SupportPage } from './pages/Support'
import { AuditPage } from './pages/Audit'
import { Admins } from './pages/Admins'
import { Profile } from './pages/Profile'
import {
  Badge, CommandPalette, Icon, IconButton, InlineAlert, Logo, Sidebar, SkeletonText, Topbar,
  type CommandItem, type Crumb, type NavGroup,
} from './components'
import { ShellProvider } from './shell'
import { applyTheme, setTheme, storedTheme, systemTheme, type Theme } from './theme'

/** D-3: sahifalar reestri — Sidebar, breadcrumbs va ⌘K shu ro'yxatdan. `superOnly` — SUPPORT'dan yashiriladi. */
interface PageDef { id: string; path: string; label: string; icon: string; group: string; superOnly?: boolean }
const PAGES: PageDef[] = [
  { id: 'dashboard', path: '/', label: 'Boshqaruv paneli', icon: 'layout-dashboard', group: 'Asosiy' },
  { id: 'sellers', path: '/sellers', label: 'Sotuvchilar', icon: 'users', group: 'Asosiy' },
  { id: 'grants', path: '/grants', label: 'Kirish so‘rovlari', icon: 'key-round', group: 'Asosiy' },
  { id: 'status', path: '/status', label: 'Tizim holati', icon: 'activity', group: 'Asosiy' },
  { id: 'support', path: '/support', label: 'Qo‘llab-quvvatlash', icon: 'life-buoy', group: 'Boshqaruv', superOnly: true },
  { id: 'audit', path: '/audit', label: 'Audit jurnali', icon: 'scroll-text', group: 'Boshqaruv', superOnly: true },
  { id: 'admins', path: '/admins', label: 'Administratorlar', icon: 'user-cog', group: 'Boshqaruv', superOnly: true },
  { id: 'profile', path: '/profile', label: 'Profil va xavfsizlik', icon: 'user-round', group: 'Hisob' },
]

export default function App() {
  const [me, setMe] = useState<Me | null>(null)
  const [mustChange, setMustChange] = useState(false)
  const [loading, setLoading] = useState(!!getToken())

  useEffect(() => {
    if (!getToken()) return
    api<Me>('/auth/me')
      .then((m) => { setMe(m); setMustChange(!!m.mustChangePassword) })
      .catch(() => setMe(null))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onLogout = () => setMe(null)
    window.addEventListener('sf:logout', onLogout)
    return () => window.removeEventListener('sf:logout', onLogout)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      /* baribir chiqamiz */
    }
    setToken(null)
    setMe(null)
  }, [])

  if (loading) {
    return (
      <div className="sf-boot" aria-busy="true" aria-label="Yuklanmoqda">
        <Logo />
        <div style={{ width: 240 }}><SkeletonText lines={3} /></div>
      </div>
    )
  }
  if (!me) return <Login onLogin={(m, mc) => { setMe(m); setMustChange(mc) }} />

  return (
    <HashRouter>
      <ShellProvider>
        <Shell me={me} mustChange={mustChange} onPasswordChanged={() => setMustChange(false)} onLogout={logout} />
      </ShellProvider>
    </HashRouter>
  )
}

function Shell({ me, mustChange, onPasswordChanged, onLogout }:
  { me: Me; mustChange: boolean; onPasswordChanged: () => void; onLogout: () => void }) {
  const loc = useLocation()
  const nav = useNavigate()
  const superadmin = me.role === 'SUPERADMIN'
  const [collapsed, setCollapsed] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [cmdOpen, setCmdOpen] = useState(false)
  const [theme, setThemeState] = useState<Theme>(() => storedTheme() ?? systemTheme())

  // M-5: vaqtinchalik parol bilan faqat Profil
  const visible = useMemo(() => PAGES.filter((p) => (mustChange ? p.id === 'profile' : superadmin || !p.superOnly)), [mustChange, superadmin])
  // ichki sahifalar (/sellers/123, /grants/5) ota bo'limni faol ko'rsatadi
  const current = PAGES.find((p) => p.path === loc.pathname)
    ?? PAGES.find((p) => p.path !== '/' && loc.pathname.startsWith(p.path + '/')) ?? PAGES[0]
  const detail = current.path !== loc.pathname && loc.pathname !== '/'

  const groups: NavGroup[] = useMemo(() => {
    const out: NavGroup[] = []
    for (const p of visible.filter((x) => x.id !== 'profile')) {
      let g = out.find((x) => x.label === p.group)
      if (!g) { g = { label: p.group, items: [] }; out.push(g) }
      g.items.push({ id: p.id, label: p.label, icon: p.icon })
    }
    return out
  }, [visible])

  const go = (id: string) => {
    const p = PAGES.find((x) => x.id === id)
    if (p) nav(p.path)
    setMenuOpen(false)
  }

  const crumbs: Crumb[] = mustChange ? [{ label: 'SellerFlow Admin' }, { label: 'Profil va xavfsizlik' }]
    : detail ? [{ label: 'SellerFlow Admin' }, { label: current.label, onClick: (e) => { e.preventDefault(); nav(current.path) }, href: '#' + current.path },
      { label: current.id === 'sellers' ? `Sotuvchi ${loc.pathname.split('/')[2] ?? ''}` : `So‘rov #${loc.pathname.split('/')[2] ?? ''}` }]
    : [{ label: 'SellerFlow Admin' }, { label: current.label }]

  const toggleTheme = () => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    setTheme(next)
    setThemeState(next)
  }
  useEffect(() => { applyTheme(theme) }, [theme])

  const commands: CommandItem[] = [
    ...visible.map((p) => ({ id: p.id, label: p.label, icon: p.icon, group: 'Sahifalar', onSelect: () => go(p.id) })),
    { id: 'theme', label: theme === 'dark' ? 'Yorug‘ mavzu' : 'Qorong‘i mavzu', icon: theme === 'dark' ? 'sun' : 'moon', group: 'Amallar', onSelect: toggleTheme },
    { id: 'logout', label: 'Chiqish', icon: 'log-out', group: 'Amallar', onSelect: onLogout },
  ]

  const footer = (
    <>
      <button type="button" className="sf-nav-item" aria-current={current.id === 'profile' ? 'page' : undefined}
        aria-label={collapsed ? 'Profil' : undefined} onClick={() => go('profile')}>
        <Icon name="user-round" size={16} /><span className="sf-nav-item__label">Profil</span>
      </button>
      <button type="button" className="sf-nav-item" aria-label={collapsed ? 'Chiqish' : undefined} onClick={onLogout}>
        <Icon name="log-out" size={16} /><span className="sf-nav-item__label">Chiqish</span>
      </button>
    </>
  )

  return (
    <div className="sf-shell" data-menu-open={menuOpen || undefined}>
      <div className="sf-shell__side">
        <Sidebar groups={groups} active={current.id} onNavigate={go} collapsed={collapsed}
          onToggleCollapsed={() => setCollapsed((c) => !c)} footer={footer} role={me.role} />
      </div>
      <div className="sf-shell__scrim" onClick={() => setMenuOpen(false)} aria-hidden="true" />
      <div className="sf-shell__body">
        <Topbar
          breadcrumbs={crumbs}
          onOpenSearch={mustChange ? undefined : () => setCmdOpen(true)}
          user={{ name: me.email, role: me.role }}
          onUserClick={() => go('profile')}
          actions={<>
            <span className="sf-shell__menu-btn"><IconButton icon="menu" label="Menyuni ochish" onClick={() => setMenuOpen(true)} /></span>
            {!mustChange && <HealthIndicator onOpen={() => go('status')} />}
            {!mustChange && <Notifications />}
            <IconButton icon={theme === 'dark' ? 'sun' : 'moon'} label={theme === 'dark' ? 'Yorug‘ mavzu' : 'Qorong‘i mavzu'} onClick={toggleTheme} />
          </>}
        />
        <main className="sf-shell__main">
          <div className="sf-shell__content">
            {mustChange ? (
              <>
                <div className="mb-4">
                  <InlineAlert tone="warning" title="Vaqtinchalik parol">Davom etish uchun quyida parolni almashtiring.</InlineAlert>
                </div>
                <Routes>
                  <Route path="*" element={<Profile me={me} forced onChanged={onPasswordChanged} />} />
                </Routes>
              </>
            ) : (
              <ErrorBoundary resetKey={loc.pathname}>
                <Routes>
                  <Route path="/" element={<Dashboard me={me} />} />
                  <Route path="/status" element={<SystemStatus />} />
                  <Route path="/sellers" element={<Sellers me={me} />} />
                  <Route path="/sellers/:tg" element={<SellerDetail me={me} />} />
                  <Route path="/grants" element={<Grants me={me} />} />
                  <Route path="/grants/:id" element={<AccessViewer />} />
                  {superadmin && <Route path="/support" element={<SupportPage />} />}
                  {superadmin && <Route path="/audit" element={<AuditPage />} />}
                  {superadmin && <Route path="/admins" element={<Admins me={me} />} />}
                  <Route path="/profile" element={<Profile me={me} onChanged={onPasswordChanged} />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </ErrorBoundary>
            )}
          </div>
        </main>
      </div>
      {!mustChange && <CommandPalette open={cmdOpen} onOpenChange={setCmdOpen} items={commands} placeholder="Sahifa yoki amalni qidiring…" />}
    </div>
  )
}

interface HealthLite { database: string; bot: string; backup?: { state: string } }

/** Topbar'dagi tizim holati: har 60 s da /health; 401/403 da so'rov to'xtaydi (spam yo'q). */
function HealthIndicator({ onOpen }: { onOpen: () => void }) {
  const [state, setState] = useState<'ok' | 'degraded' | 'unknown'>('unknown')
  const stopped = useRef(false)
  useEffect(() => {
    let timer: number | undefined
    const ctrl = new AbortController()
    const tick = async () => {
      if (stopped.current) return
      try {
        const h = await api<HealthLite>('/health', { signal: ctrl.signal })
        const bad = h.database !== 'UP' || h.bot === 'DEGRADED' || h.backup?.state === 'FAILED' || h.backup?.state === 'STALE'
        setState(bad ? 'degraded' : 'ok')
      } catch (e) {
        if (ctrl.signal.aborted) return
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) { stopped.current = true; return }
        setState('degraded')
      }
      timer = window.setTimeout(tick, 60_000)
    }
    tick()
    return () => { ctrl.abort(); window.clearTimeout(timer) }
  }, [])
  if (state === 'unknown') return null
  return (
    <button type="button" className="sf-health" title="Tizim holati (har 60 soniyada)" onClick={onOpen}>
      <Badge tone={state === 'ok' ? 'success' : 'warning'}>{state === 'ok' ? 'Tizim ishlayapti' : 'Tizimda muammo'}</Badge>
    </button>
  )
}
