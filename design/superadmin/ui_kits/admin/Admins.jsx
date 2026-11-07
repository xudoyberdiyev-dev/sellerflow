import React, { useState } from 'react';

export function AuditPage({ demoState }) {
  return (
    <>
      <PageHeader title="Audit jurnali" description="Har bir kirish, ko‘rish va xavfli amal. Yozuvlarni o‘zgartirib yoki o‘chirib bo‘lmaydi."
        actions={<DateRangePicker today={new Date(2026, 10, 5)} />} />
      <DataTable caption="Audit jurnali" selectable={false} rows={demoState === 'empty' ? [] : AUDIT} loading={demoState === 'loading'}
        error={demoState === 'error' ? 'Jurnalni yuklab bo‘lmadi.' : null} emptyTitle="Tanlangan davrda yozuv yo‘q" emptyDescription="Boshqa sana oralig‘ini tanlang."
        columns={[
          { key: 'at', header: 'Vaqt', sortable: true, render: (r) => <span className="sf-num">{r.at}</span> },
          { key: 'actor', header: 'Kim', sortable: true, render: (r) => <div className="sf-cell-stack"><span>{r.actor}</span><span className="sf-cell-sub">{r.role}</span></div> },
          { key: 'action', header: 'Amal', render: (r) => <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>{r.action.includes('ko‘rdi') && <Icon name="eye" size={14} style={{ color: 'var(--privacy-icon)' }} />}{r.action}</span> },
          { key: 'target', header: 'Obyekt' },
          { key: 'meta', header: 'Tafsilot', render: (r) => <span className="sf-cell-sub">{r.meta}</span> },
        ]} />
    </>
  );
}

export function AdminsPage({ role, admins, onDanger }) {
  if (role !== 'SUPERADMIN') {
    return <Card><EmptyState kind="forbidden" code="403" title="Bu bo‘limga ruxsat yo‘q" description="Adminlarni faqat SUPERADMIN boshqaradi. Kirish kerak bo‘lsa, SUPERADMIN’ga murojaat qiling." /></Card>;
  }
  return (
    <>
      <PageHeader title="Adminlar" description="Panel xodimlari va ularning huquqlari." actions={<Button variant="primary" icon="user-plus">Admin qo‘shish</Button>} />
      <DataTable caption="Adminlar" selectable={false} rows={admins} columns={[
        { key: 'name', header: 'Ism', sortable: true, hideable: false, render: (r) => <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><Avatar name={r.name} size={28} /><div className="sf-cell-stack"><span>{r.name}</span><span className="sf-cell-sub">{r.email}</span></div></div> },
        { key: 'role', header: 'Rol', render: (r) => <Badge tone={r.role === 'SUPERADMIN' ? 'brand' : 'neutral'} dot={false}>{r.role}</Badge> },
        { key: 'twofa', header: '2FA', render: (r) => r.twofa ? <Badge tone="success" icon="shield-check">Yoqilgan</Badge> : <Badge tone="warning" icon="shield-alert">Yoqilmagan</Badge> },
        { key: 'status', header: 'Holat', render: (r) => <StatusBadge status={r.status} /> },
        { key: 'last', header: 'Oxirgi kirish', sortable: true },
      ]} rowActions={(r) => [
        { label: 'Rolni o‘zgartirish', icon: 'user-cog' },
        { separator: true },
        { label: '2FA’ni qayta o‘rnatish', icon: 'key-round', danger: true, onSelect: () => onDanger('2fa', r) },
        { label: r.status === 'Bloklangan' ? 'Blokdan chiqarish' : 'Bloklash', icon: 'ban', danger: true, onSelect: () => onDanger('block-admin', r) },
        { label: 'Adminni o‘chirish', icon: 'trash-2', danger: true, onSelect: () => onDanger('delete', r) },
      ]} />
    </>
  );
}
