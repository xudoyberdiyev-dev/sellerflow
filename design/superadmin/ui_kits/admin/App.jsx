import React, { useState, useEffect } from 'react';

const ADMIN = { name: 'Aziz Rahimov' };
const DANGER = {
  block: (s) => ({ title: 'Sotuvchini bloklash', description: `${s.name} botdan foydalana olmaydi. Ma’lumotlari saqlanadi va keyin blokdan chiqarish mumkin.`, confirmLabel: 'Bloklash', toast: 'Sotuvchi bloklandi' }),
  unblock: (s) => ({ tone: 'warning', title: 'Blokdan chiqarish', description: `${s.name} botdan yana foydalana oladi.`, confirmLabel: 'Blokdan chiqarish', toast: 'Sotuvchi blokdan chiqarildi' }),
  '2fa': (a) => ({ title: '2FA’ni qayta o‘rnatish', description: `${a.name} keyingi kirishda 2FA’ni yangidan sozlashi kerak bo‘ladi. Joriy sessiyalar yopiladi.`, confirmLabel: '2FA’ni qayta o‘rnatish', toast: '2FA qayta o‘rnatildi' }),
  'block-admin': (a) => ({ title: 'Adminni bloklash', description: `${a.name} panelga kira olmaydi. Barcha sessiyalari darhol yopiladi.`, confirmLabel: 'Bloklash', toast: 'Admin bloklandi' }),
  delete: (a) => ({ title: 'Adminni o‘chirish', description: `${a.name} hisobi butunlay o‘chiriladi. Audit yozuvlari saqlanib qoladi. Bu amalni bekor qilib bo‘lmaydi.`, confirmLabel: 'O‘chirish', toast: 'Admin o‘chirildi' }),
};

export function KitApp() {
  const [authed, setAuthed] = useState(() => localStorage.getItem('sf-kit-authed') === '1');
  const [page, setPage] = useState('dashboard');
  const [role, setRole] = useState('SUPERADMIN');
  const [theme, setTheme] = useState('light');
  const [collapsed, setCollapsed] = useState(false);
  const [demo, setDemo] = useState('normal');
  const [sellers, setSellers] = useState(SELLERS);
  const [admins, setAdmins] = useState(ADMINS);
  const [requests, setRequests] = useState(() => ACCESS_REQUESTS.map((r) => r.expiresIn ? { ...r, expiresAt: Date.now() + r.expiresIn * 1000 } : r));
  const [access, setAccess] = useState({});
  const [sellerId, setSellerId] = useState(null);
  const [cmd, setCmd] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const { toasts, push, dismiss } = useToasts();

  useEffect(() => { document.documentElement.dataset.theme = theme; }, [theme]);
  useEffect(() => { localStorage.setItem('sf-kit-authed', authed ? '1' : '0'); }, [authed]);

  if (!authed) return <LoginScreen onDone={() => setAuthed(true)} />;

  const seller = sellers.find((s) => s.id === sellerId);
  const go = (p) => { setPage(p); setSellerId(null); };
  const openSeller = (s) => { setSellerId(typeof s === 'string' ? s : s.id); setPage('seller'); };
  const curAccess = seller && access[seller.id];
  const session = curAccess && curAccess.status === 'Ruxsat berilgan';

  const askDanger = (kind, target) => {
    const k = kind === 'block' && target.status === 'Bloklangan' ? 'unblock' : kind;
    setConfirm({ kind: k, target, ...DANGER[k](target) });
  };
  const doDanger = (reason) => new Promise((res) => setTimeout(() => {
    const { kind, target, toast } = confirm;
    if (kind === 'block' || kind === 'unblock') setSellers((ss) => ss.map((s) => s.id === target.id ? { ...s, status: kind === 'block' ? 'Bloklangan' : 'Faol' } : s));
    if (kind === 'block-admin') setAdmins((as) => as.map((a) => a.id === target.id ? { ...a, status: a.status === 'Bloklangan' ? 'Faol' : 'Bloklangan' } : a));
    if (kind === 'delete') setAdmins((as) => as.filter((a) => a.id !== target.id));
    if (kind === '2fa') setAdmins((as) => as.map((a) => a.id === target.id ? { ...a, twofa: false } : a));
    setConfirm(null);
    push({ tone: 'success', title: toast, description: `${target.name} · Sabab: ${reason}`, audit: true });
    res();
  }, 700));

  const requestAccess = (s, v) => {
    const r = { id: 'req_' + Math.random().toString(16).slice(2, 10), seller: s.name, sellerId: s.id, admin: ADMIN.name, reason: v.reason, duration: v.duration, status: 'Kutilmoqda', created: '05.11.2026 14:27' };
    setAccess((a) => ({ ...a, [s.id]: r }));
    setRequests((rs) => [r, ...rs]);
    push({ tone: 'info', title: 'So‘rov yuborildi', description: `${s.name} Telegram botda javob beradi.`, audit: true });
  };
  const setReqStatus = (sid, patch) => {
    setAccess((a) => ({ ...a, [sid]: { ...a[sid], ...patch } }));
    setRequests((rs) => rs.map((r) => (access[sid] && r.id === access[sid].id ? { ...r, ...patch } : r)));
  };
  const grant = () => { setReqStatus(seller.id, { status: 'Ruxsat berilgan', expiresAt: Date.now() + curAccess.duration * 60 * 1000 }); push({ tone: 'success', title: 'Sotuvchi ruxsat berdi', description: `${curAccess.duration} daqiqa, faqat o‘qish.`, audit: true }); };
  const endSession = (expired) => { setReqStatus(seller.id, { status: 'Muddati tugagan' }); push({ tone: expired ? 'warning' : 'info', title: expired ? 'Kirish muddati tugadi' : 'Ko‘rish yakunlandi', description: 'Tijoriy ma’lumot yana yashirildi.', audit: true }); };
  const cancelReq = (r) => {
    const sid = r.sellerId || (seller && seller.id);
    setRequests((rs) => rs.map((x) => x.id === r.id ? { ...x, status: 'Bekor qilingan' } : x));
    if (access[sid]) setAccess((a) => ({ ...a, [sid]: { ...a[sid], status: 'Bekor qilingan' } }));
    push({ tone: 'info', title: 'So‘rov bekor qilindi', audit: true });
  };

  const crumbs = page === 'seller' && seller ? [{ label: 'Sotuvchilar', href: '#', onClick: (e) => { e.preventDefault(); go('sellers'); } }, { label: seller.name }]
    : [{ label: { dashboard: 'Boshqaruv paneli', sellers: 'Sotuvchilar', access: 'Kirish so‘rovlari', audit: 'Audit jurnali', admins: 'Adminlar' }[page] }];

  const cmdItems = [
    ...NAV.flatMap((g) => g.items).map((i) => ({ group: 'Sahifalar', label: i.label, icon: i.icon, onSelect: () => go(i.id) })),
    ...sellers.slice(0, 8).map((s) => ({ group: 'Sotuvchilar', label: s.name, icon: 'user', hint: s.tg, hintMono: true, onSelect: () => openSeller(s) })),
    { group: 'Amallar', label: theme === 'dark' ? 'Yorug‘ mavzu' : 'Qorong‘i mavzu', icon: theme === 'dark' ? 'sun' : 'moon', onSelect: () => setTheme(theme === 'dark' ? 'light' : 'dark') },
    { group: 'Amallar', label: 'Tizimdan chiqish', icon: 'log-out', onSelect: () => setAuthed(false) },
  ];

  const userMenu = (
    <DropdownMenu align="end" label="Profil menyusi" width={240} items={[
      { heading: role },
      { label: role === 'SUPERADMIN' ? 'SUPPORT sifatida ko‘rish' : 'SUPERADMIN sifatida ko‘rish', icon: 'repeat', onSelect: () => setRole(role === 'SUPERADMIN' ? 'SUPPORT' : 'SUPERADMIN') },
      { label: theme === 'dark' ? 'Yorug‘ mavzu' : 'Qorong‘i mavzu', icon: theme === 'dark' ? 'sun' : 'moon', onSelect: () => setTheme(theme === 'dark' ? 'light' : 'dark') },
      { separator: true },
      { label: 'Tizimdan chiqish', icon: 'log-out', onSelect: () => setAuthed(false) },
    ]} trigger={(p) => (
      <button type="button" className="sf-user" {...p} aria-label={`Profil: ${ADMIN.name}`}>
        <Avatar name={ADMIN.name} size={28} />
        <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}><span className="sf-user__name">{ADMIN.name}</span><span className="sf-user__role">{role}</span></span>
        <Icon name="chevron-down" size={14} style={{ color: 'var(--text-tertiary)' }} />
      </button>
    )} />
  );

  return (
    <>
      <AppShell page={page === 'seller' ? 'sellers' : page} onNavigate={go} role={role} crumbs={crumbs} onOpenSearch={() => setCmd(true)}
        collapsed={collapsed} onToggleCollapsed={() => setCollapsed(!collapsed)} userMenu={userMenu}
        banner={page === 'seller' && session ? <ReadOnlyBanner sellerName={seller.name} expiresAt={curAccess.expiresAt} onEnd={() => endSession(false)} onExpire={() => endSession(true)} /> : null}>
        {page === 'dashboard' && <DashboardPage demoState={demo} onNavigate={go} />}
        {page === 'sellers' && <SellersPage sellers={sellers} demoState={demo} role={role} onOpen={openSeller} onBlock={(s) => askDanger('block', s)} onRequest={(s) => openSeller(s)} />}
        {page === 'seller' && seller && <SellerDetailPage key={seller.id} seller={seller} access={curAccess} role={role} admin={ADMIN.name}
          onRequest={requestAccess} onCancel={() => cancelReq(curAccess)} onSimulateGrant={grant} onBlock={(s) => askDanger('block', s)} />}
        {page === 'access' && <AccessRequestsPage requests={requests} demoState={demo} onCancel={cancelReq} onOpenSeller={openSeller} />}
        {page === 'audit' && <AuditPage demoState={demo} />}
        {page === 'admins' && <AdminsPage role={role} admins={admins} onDanger={askDanger} />}
      </AppShell>
      <CommandPalette open={cmd} onOpenChange={setCmd} items={cmdItems} />
      <ConfirmDialog open={!!confirm} onClose={() => setConfirm(null)} onConfirm={doDanger} requireReason
        tone={confirm && confirm.tone || 'danger'} title={confirm && confirm.title} description={confirm && confirm.description} confirmLabel={confirm && confirm.confirmLabel} />
      <ToastStack toasts={toasts} onDismiss={dismiss} />
      <div style={{ position: 'fixed', left: '50%', bottom: 16, transform: 'translateX(-50%)', zIndex: 30, display: 'flex', alignItems: 'center', gap: 8, padding: '6px 6px 6px 12px', background: 'var(--bg-surface)', border: '1px solid var(--border-default)', borderRadius: 8, boxShadow: 'var(--shadow-popover)' }}>
        <span style={{ font: 'var(--type-caption)', color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>Prototip holati</span>
        <SegmentedControl size="sm" label="Sahifa holati" value={demo} onChange={setDemo} options={[{ value: 'normal', label: 'Oddiy' }, { value: 'loading', label: 'Yuklanmoqda' }, { value: 'empty', label: 'Bo‘sh' }, { value: 'error', label: 'Xato' }]} />
      </div>
    </>
  );
}
