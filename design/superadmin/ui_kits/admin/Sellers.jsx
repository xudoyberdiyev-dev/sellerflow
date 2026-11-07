import React, { useState } from 'react';

export function SellersPage({ sellers, demoState, onOpen, onBlock, onRequest, role }) {
  const [tab, setTab] = useState('all');
  const [q, setQ] = useState('');
  const [preview, setPreview] = useState(null);
  const counts = (s) => sellers.filter((x) => x.status === s).length;
  const filtered = sellers.filter((s) =>
    (tab === 'all' || s.status === tab) && (s.name.toLowerCase().includes(q.toLowerCase()) || s.tg.includes(q)));
  const rows = demoState === 'empty' ? [] : filtered;

  const columns = [
    { key: 'name', header: 'Sotuvchi', sortable: true, hideable: false, render: (r) => (
      <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><Avatar name={r.name} size={28} />
        <div className="sf-cell-stack"><span style={{ fontWeight: 500 }}>{r.name}</span><span className="sf-cell-sub">{r.username}</span></div></div>) },
    { key: 'tg', header: 'Telegram ID', render: (r) => <span className="sf-id">{r.tg}</span> },
    { key: 'status', header: 'Holat', sortable: true, render: (r) => <StatusBadge status={r.status} /> },
    { key: 'channel', header: 'Asosiy kanal', render: (r) => <ChannelTag channel={r.channel} /> },
    { key: 'calcs', header: 'Hisoblashlar', sortable: true, align: 'right' },
    { key: 'profit', header: 'Foyda', render: () => <Redacted label="Yashirin" />, defaultHidden: false },
    { key: 'registered', header: 'Ro‘yxatdan o‘tgan', sortable: true, defaultHidden: true },
    { key: 'last', header: 'Oxirgi faollik', sortable: true },
  ];

  return (
    <>
      <PageHeader title="Sotuvchilar" description="Bot foydalanuvchilari. Tijoriy ma’lumot faqat sotuvchi roziligi bilan ochiladi."
        actions={<Button icon="download">Eksport (CSV)</Button>} />
      <Tabs label="Holat bo‘yicha" value={tab} onChange={setTab} items={[
        { value: 'all', label: 'Hammasi', count: sellers.length },
        { value: 'Faol', label: 'Faol', count: counts('Faol') },
        { value: 'Ro‘yxatdan o‘tmoqda', label: 'Ro‘yxatdan o‘tmoqda', count: counts('Ro‘yxatdan o‘tmoqda') },
        { value: 'Bloklangan', label: 'Bloklangan', count: counts('Bloklangan') },
        { value: 'O‘chirish so‘ralgan', label: 'O‘chirish so‘ralgan', count: counts('O‘chirish so‘ralgan') },
      ]} />
      <DataTable caption="Sotuvchilar ro‘yxati" columns={columns} rows={rows} pageSize={10} maxHeight={560}
        loading={demoState === 'loading'} error={demoState === 'error' ? 'Server javob bermadi (504). Birozdan keyin qayta urinib ko‘ring.' : null} onRetry={() => {}}
        emptyTitle={q ? 'Hech narsa topilmadi' : 'Hali sotuvchi yo‘q'}
        emptyDescription={q ? `«${q}» bo‘yicha sotuvchi yo‘q. Ism yoki Telegram ID’ni tekshiring.` : 'Sotuvchilar botda /start bosgach shu yerda paydo bo‘ladi.'}
        onRowClick={setPreview}
        toolbar={<><Input size="sm" icon="search" placeholder="Ism yoki Telegram ID" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 260 }} />
          <Button size="sm" icon="filter">Kanal</Button></>}
        bulkActions={() => <><Button size="sm" icon="download">Eksport</Button>{role === 'SUPERADMIN' && <Button size="sm" variant="danger" icon="ban">Bloklash</Button>}</>}
        rowActions={(r) => [
          { label: 'Tafsilotlarni ochish', icon: 'panel-right-open', onSelect: () => setPreview(r) },
          { label: 'Profil sahifasi', icon: 'arrow-up-right', onSelect: () => onOpen(r) },
          { label: 'Kirish so‘rash', icon: 'send', onSelect: () => onRequest(r) },
          { label: 'Telegram ID nusxalash', icon: 'copy', onSelect: () => navigator.clipboard && navigator.clipboard.writeText(r.tg) },
          ...(role === 'SUPERADMIN' ? [{ separator: true }, { label: r.status === 'Bloklangan' ? 'Blokdan chiqarish' : 'Bloklash', icon: 'ban', danger: r.status !== 'Bloklangan', onSelect: () => onBlock(r) }] : []),
        ]} />
      <Drawer open={!!preview} onClose={() => setPreview(null)} title={preview && preview.name}
        subtitle={preview && <><StatusBadge status={preview.status} /><span className="sf-id">{preview.tg}</span></>}
        footer={preview && <><Button onClick={() => onOpen(preview)} iconRight="arrow-up-right">Profilni ochish</Button>
          {role === 'SUPERADMIN' && <Button variant="danger" icon="ban" onClick={() => onBlock(preview)}>{preview.status === 'Bloklangan' ? 'Blokdan chiqarish' : 'Bloklash'}</Button>}</>}>
        {preview && <>
          <dl className="sf-dl">
            <dt>Username</dt><dd>{preview.username}</dd>
            <dt>Telefon</dt><dd className="sf-num">{preview.phone}</dd>
            <dt>Tarif</dt><dd><Badge tone="brand" dot={false}>{preview.plan}</Badge></dd>
            <dt>Kanallar</dt><dd style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>{preview.channels.map((c) => <ChannelTag key={c} channel={c} />)}</dd>
            <dt>Ro‘yxatdan o‘tgan</dt><dd className="sf-num">{preview.registered}</dd>
            <dt>Oxirgi faollik</dt><dd className="sf-num">{preview.last}</dd>
            <dt>Hisoblashlar</dt><dd className="sf-num">{preview.calcs}</dd>
          </dl>
          <PrivacyGuard fields={['Tannarx', 'Foyda', 'Sotish narxi', 'Hisoblashlar']} onAction={() => onRequest(preview)} />
        </>}
      </Drawer>
    </>
  );
}
