import { useCallback, useEffect, useState } from 'react'
import { HashRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom'
import clsx from 'clsx'
import { Activity, FileClock, KeyRound, LayoutDashboard, LifeBuoy, LogOut, ShieldCheck, Users, UserCog } from 'lucide-react'
import { api, getToken, setToken, type Me } from './api'
import { Login } from './Login'
import { Dashboard } from './pages/Dashboard'
import { Sellers } from './pages/Sellers'
import { Grants } from './pages/Grants'
import { SupportPage } from './pages/Support'
import { AuditPage } from './pages/Audit'
import { Admins } from './pages/Admins'
import { Profile } from './pages/Profile'

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

  if (loading) return <div className="p-10 text-sm text-stone-500">Yuklanmoqda…</div>
  if (!me) return <Login onLogin={(m, mc) => { setMe(m); setMustChange(mc) }} />

  const superadmin = me.role === 'SUPERADMIN'
  // M-5: vaqtinchalik parol almashtirilmaguncha server boshqa API'ni yopadi — UI ham faqat Profil sahifasini ko'rsatadi.
  const nav = mustChange ? [] : [
    { to: '/', label: 'Boshqaruv paneli', icon: LayoutDashboard, show: true },
    { to: '/sellers', label: 'Sotuvchilar', icon: Users, show: true },
    { to: '/grants', label: 'Kirish so‘rovlari', icon: KeyRound, show: true },
    { to: '/support', label: 'Qo‘llab-quvvatlash', icon: LifeBuoy, show: superadmin },
    { to: '/audit', label: 'Audit jurnali', icon: FileClock, show: superadmin },
    { to: '/admins', label: 'Administratorlar', icon: UserCog, show: superadmin },
  ]

  return (
    <HashRouter>
      <div className="flex min-h-screen">
        <aside className="hidden w-60 shrink-0 flex-col border-r border-stone-200 bg-white md:flex">
          <div className="flex items-center gap-2 px-5 py-4">
            <div className="flex h-7 w-7 items-center justify-center rounded-md bg-stone-900 text-white"><ShieldCheck size={15} /></div>
            <span className="text-sm font-semibold">SellerFlow Admin</span>
          </div>
          <nav className="flex-1 space-y-0.5 px-3">
            {nav.filter((n) => n.show).map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.to === '/'}
                className={({ isActive }) =>
                  clsx('flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm',
                    isActive ? 'bg-stone-100 font-medium text-stone-900' : 'text-stone-600 hover:bg-stone-50 hover:text-stone-900')}
              >
                <n.icon size={16} />
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="border-t border-stone-200 p-3">
            <NavLink to="/profile" className="block truncate rounded-md px-2.5 py-1.5 text-xs text-stone-600 hover:bg-stone-50">
              {me.email}
              <span className="ml-1 text-stone-400">· {me.role}</span>
            </NavLink>
            <button onClick={logout} className="mt-1 flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm text-stone-600 hover:bg-stone-50">
              <LogOut size={15} /> Chiqish
            </button>
          </div>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center justify-between border-b border-stone-200 bg-white px-4 py-3 md:hidden">
            <span className="text-sm font-semibold">SellerFlow Admin</span>
            <div className="flex gap-3 text-sm">
              {nav.filter((n) => n.show).map((n) => (
                <NavLink key={n.to} to={n.to} end={n.to === '/'} className={({ isActive }) => (isActive ? 'font-medium' : 'text-stone-500')}>
                  <n.icon size={16} aria-label={n.label} />
                </NavLink>
              ))}
              <button onClick={logout} aria-label="Chiqish"><LogOut size={16} /></button>
            </div>
          </header>
          <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 md:px-8">
            {mustChange && (
              <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                Vaqtinchalik parol bilan kirdingiz. Davom etish uchun quyida parolni almashtiring.
              </div>
            )}
            {mustChange ? (
              <Routes>
                <Route path="*" element={<Profile onChanged={() => setMustChange(false)} />} />
              </Routes>
            ) : (
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/sellers" element={<Sellers me={me} />} />
              <Route path="/grants" element={<Grants me={me} />} />
              {superadmin && <Route path="/support" element={<SupportPage />} />}
              {superadmin && <Route path="/audit" element={<AuditPage />} />}
              {superadmin && <Route path="/admins" element={<Admins me={me} />} />}
              <Route path="/profile" element={<Profile onChanged={() => setMustChange(false)} />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            )}
          </main>
          <footer className="px-8 pb-4 text-xs text-stone-400"><Activity size={11} className="mr-1 inline" />Tijoriy ma’lumotlar faqat sotuvchi roziligi bilan ko‘rsatiladi.</footer>
        </div>
      </div>
    </HashRouter>
  )
}
