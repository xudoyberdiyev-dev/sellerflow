import React from 'react';

export const NAV = [
  { label: 'Asosiy', items: [
    { id: 'dashboard', label: 'Boshqaruv paneli', icon: 'layout-dashboard' },
    { id: 'sellers', label: 'Sotuvchilar', icon: 'users', count: 1284 },
    { id: 'access', label: 'Kirish so‘rovlari', icon: 'key-round', count: 3 },
  ] },
  { label: 'Nazorat', items: [
    { id: 'audit', label: 'Audit jurnali', icon: 'scroll-text' },
    { id: 'admins', label: 'Adminlar', icon: 'user-cog' },
  ] },
];

export function AppShell({ page, onNavigate, role, user, crumbs, onOpenSearch, collapsed, onToggleCollapsed, children, banner, userMenu }) {
  return (
    <div style={{ display: 'flex', height: '100vh', background: 'var(--bg-app)' }}>
      <Sidebar groups={NAV} active={page} onNavigate={onNavigate} role={role} collapsed={collapsed} onToggleCollapsed={onToggleCollapsed} />
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Topbar breadcrumbs={crumbs} onOpenSearch={onOpenSearch} actions={userMenu} />
        <main style={{ flex: 1, overflowY: 'auto', position: 'relative' }}>
          {banner}
          <div style={{ maxWidth: 1360, margin: '0 auto', padding: '24px 24px 48px', display: 'flex', flexDirection: 'column', gap: 20 }}>{children}</div>
        </main>
      </div>
    </div>
  );
}

export function PageHeader({ title, description, actions, meta }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
      <div style={{ flex: 1, minWidth: 240 }}>
        <h1 style={{ font: 'var(--type-h1)', letterSpacing: 'var(--tracking-tight)', margin: 0 }}>{title}</h1>
        {description && <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)' }}>{description}</p>}
        {meta}
      </div>
      {actions && <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>{actions}</div>}
    </div>
  );
}
