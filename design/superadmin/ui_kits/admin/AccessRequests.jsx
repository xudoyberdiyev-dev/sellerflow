import React, { useState } from 'react';

export function AccessRequestsPage({ requests, demoState, onCancel, onOpenSeller }) {
  const [tab, setTab] = useState('all');
  const statuses = ['Kutilmoqda', 'Ruxsat berilgan', 'Rad etilgan', 'Bekor qilingan', 'Muddati tugagan'];
  const rows = demoState === 'empty' ? [] : requests.filter((r) => tab === 'all' || r.status === tab);
  return (
    <>
      <PageHeader title="Kirish so‘rovlari" description="Sotuvchilarning tijoriy ma’lumotiga vaqtinchalik, faqat o‘qish uchun ruxsatlar." />
      <Tabs label="So‘rov holati" value={tab} onChange={setTab} items={[{ value: 'all', label: 'Hammasi', count: requests.length },
        ...statuses.map((s) => ({ value: s, label: s, count: requests.filter((r) => r.status === s).length }))]} />
      <DataTable caption="Kirish so‘rovlari" selectable={false} rows={rows} loading={demoState === 'loading'}
        error={demoState === 'error' ? 'So‘rovlar ro‘yxatini olishda xato. Qayta urinib ko‘ring.' : null}
        emptyTitle="So‘rovlar yo‘q" emptyDescription="Sotuvchi profilidan «Kirish so‘rash» orqali yangi so‘rov yuboring."
        onRowClick={(r) => onOpenSeller(r.sellerId)}
        columns={[
          { key: 'id', header: 'ID', render: (r) => <span className="sf-id">{r.id}</span> },
          { key: 'seller', header: 'Sotuvchi', sortable: true, hideable: false, render: (r) => <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}><Avatar name={r.seller} size={24} />{r.seller}</div> },
          { key: 'admin', header: 'So‘ragan admin', sortable: true },
          { key: 'reason', header: 'Sabab' },
          { key: 'duration', header: 'Muddat', align: 'right', render: (r) => r.duration + ' daq' },
          { key: 'status', header: 'Holat', sortable: true, render: (r) => (
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}><StatusBadge status={r.status} />
              {r.status === 'Ruxsat berilgan' && r.expiresAt && <CountdownChip expiresAt={r.expiresAt} />}</div>) },
          { key: 'created', header: 'Yaratilgan', sortable: true },
        ]}
        rowActions={(r) => [
          { label: 'Sotuvchini ochish', icon: 'arrow-up-right', onSelect: () => onOpenSeller(r.sellerId) },
          { label: 'ID nusxalash', icon: 'copy' },
          ...(r.status === 'Kutilmoqda' ? [{ separator: true }, { label: 'So‘rovni bekor qilish', icon: 'x', danger: true, onSelect: () => onCancel(r) }] : []),
        ]} />
    </>
  );
}
