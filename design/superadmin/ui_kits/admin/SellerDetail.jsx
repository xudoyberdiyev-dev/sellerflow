import React, { useState } from 'react';

function RequestDialog({ open, seller, onClose, onSend, admin }) {
  const [dur, setDur] = useState('30');
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  if (!open) return null;
  const bad = reason.trim().length < 10;
  return (
    <Modal open onClose={onClose} tone="privacy" width={640} title="Tijoriy ma’lumotga kirish so‘rash"
      description={`${seller.name} Telegram botda so‘rovni ko‘radi va ruxsat berishi yoki rad etishi mumkin.`}
      footer={<>
        <span className="sf-modal__audit"><Icon name="scroll-text" size={14} />So‘rov va har bir ko‘rish auditga yoziladi</span>
        <Button onClick={onClose}>Bekor qilish</Button>
        <Button variant="privacy" icon="send" loading={loading} onClick={() => { setTouched(true); if (bad) return; setLoading(true); setTimeout(() => { setLoading(false); onSend({ duration: +dur, reason }); }, 700); }}>So‘rov yuborish</Button>
      </>}>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 20 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Field label="Sabab" hint="Sotuvchi bu matnni Telegramda ko‘radi." error={touched && bad ? 'Sababni yozing — kamida 10 ta belgi kerak.' : null}>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Masalan: hisoblashdagi xatoni tekshirish" maxLength={200} rows={3} />
          </Field>
          <div className="sf-field">
            <span className="sf-field__label">Muddat</span>
            <SegmentedControl label="Muddat" value={dur} onChange={setDur} options={[{ value: '30', label: '30 daqiqa' }, { value: '45', label: '45 daqiqa' }, { value: '60', label: '60 daqiqa' }]} />
          </div>
          <InlineAlert tone="privacy" title="Faqat o‘qish">Ruxsat berilsa ham ma’lumotni o‘zgartirib, eksport qilib bo‘lmaydi.</InlineAlert>
        </div>
        <TelegramPreview time="14:27" text={`Kirish so‘rovi\n\nSellerFlow xodimi ${admin} hisobingizdagi tijoriy ma’lumotni ko‘rish uchun ruxsat so‘ramoqda.\n\nSabab: ${reason.trim() || '—'}\nMuddat: ${dur} daqiqa · faqat o‘qish`}
          keyboard={[[{ text: 'Ruxsat berish' }, { text: 'Rad etish' }]]} />
      </div>
    </Modal>
  );
}

export function SellerDetailPage({ seller, access, onRequest, onCancel, onSimulateGrant, onEnd, onBlock, role, admin }) {
  const [tab, setTab] = useState('overview');
  const [dialog, setDialog] = useState(false);
  const session = access && access.status === 'Ruxsat berilgan';
  const guardState = !access ? 'locked' : access.status === 'Kutilmoqda' ? 'pending' : access.status === 'Muddati tugagan' ? 'expired' : access.status === 'Rad etilgan' ? 'denied' : 'locked';

  return (
    <>
      <PageHeader title={seller.name}
        meta={<div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8, flexWrap: 'wrap' }}><StatusBadge status={seller.status} /><span className="sf-id">{seller.tg}</span><span className="sf-cell-sub">{seller.username}</span></div>}
        actions={!session && <>
          {role === 'SUPERADMIN' && <Button variant="danger" icon="ban" onClick={() => onBlock(seller)}>{seller.status === 'Bloklangan' ? 'Blokdan chiqarish' : 'Bloklash'}</Button>}
        </>} />
      <Tabs label="Sotuvchi bo‘limlari" value={tab} onChange={setTab} items={[
        { value: 'overview', label: 'Umumiy' },
        { value: 'products', label: 'Mahsulotlar va hisoblashlar', icon: session ? 'lock-open' : 'lock' },
        { value: 'history', label: 'Kirish tarixi' },
      ]} />
      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 360px), 1fr))', gap: 12 }}>
          <Card title="Profil">
            <dl className="sf-dl">
              <dt>Telefon</dt><dd className="sf-num">{seller.phone}</dd>
              <dt>Tarif</dt><dd><Badge tone="brand" dot={false}>{seller.plan}</Badge></dd>
              <dt>Kanallar</dt><dd style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>{seller.channels.map((c) => <ChannelTag key={c} channel={c} />)}</dd>
              <dt>Ro‘yxatdan o‘tgan</dt><dd className="sf-num">{seller.registered}</dd>
              <dt>Oxirgi faollik</dt><dd className="sf-num">{seller.last}</dd>
            </dl>
          </Card>
          <Card title="Faollik" description="Tijoriy bo‘lmagan, agregat ko‘rsatkichlar">
            <dl className="sf-dl">
              <dt>Mahsulotlar soni</dt><dd className="sf-num">{seller.products}</dd>
              <dt>Hisoblashlar soni</dt><dd className="sf-num">{seller.calcs}</dd>
              <dt>O‘rtacha foyda</dt><dd>{session ? <span className="sf-num">{formatPercent(20.49)}</span> : <Redacted label="Yashirin" />}</dd>
            </dl>
          </Card>
        </div>
      )}
      {tab === 'products' && (session ? (
        <Card title="Mahsulotlar" description="Faqat o‘qish · sotuvchi roziligi bilan ochilgan" padded={false}
          actions={<Badge tone="privacy" icon="eye">Faqat ko‘rish</Badge>}>
          <div className="sf-table-scroll">
            <table className="sf-table">
              <thead><tr><th>Mahsulot</th><th>Kanal</th><th data-align="right">Tannarx</th><th data-align="right">Sotish narxi</th><th data-align="right">Komissiya</th><th data-align="right">Foyda (1 dona)</th><th data-align="right">Marja</th></tr></thead>
              <tbody>{SELLER_PRODUCTS.map((p) => (
                <tr key={p.id}><td>{p.name}</td><td><ChannelTag channel={p.channel} /></td>
                  <td data-align="right">{formatSom(p.cost)}</td><td data-align="right">{formatSom(p.price)}</td><td data-align="right">{formatSom(p.fee)}</td>
                  <td data-align="right" style={{ color: p.profit < 0 ? 'var(--danger-text)' : undefined, fontWeight: 500 }}>{formatSom(p.profit)}</td>
                  <td data-align="right">{formatPercent(p.margin)}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </Card>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <PrivacyGuard state={guardState} fields={['Tannarx', 'Foyda', 'Sotish narxi', 'Komissiya', 'Hisoblashlar']}
            onAction={guardState === 'pending' ? onCancel : () => setDialog(true)} />
          {guardState === 'pending' && (
            <InlineAlert tone="info" title="Prototip">Sotuvchi javobini simulyatsiya qiling.
              <div className="sf-alert__actions"><Button size="sm" variant="secondary" icon="check" onClick={onSimulateGrant}>Sotuvchi ruxsat berdi</Button></div>
            </InlineAlert>
          )}
        </div>
      ))}
      {tab === 'history' && (
        <DataTable selectable={false} caption="Kirish tarixi" rows={ACCESS_REQUESTS.filter((r) => r.sellerId === seller.id).concat(access ? [{ ...access, id: 'cur' }] : [])}
          emptyTitle="Kirish so‘rovlari bo‘lmagan" emptyDescription="Bu sotuvchidan hali tijoriy ma’lumotga ruxsat so‘ralmagan."
          columns={[{ key: 'created', header: 'Sana', sortable: true }, { key: 'admin', header: 'Admin' }, { key: 'reason', header: 'Sabab' }, { key: 'duration', header: 'Muddat', render: (r) => r.duration + ' daqiqa' }, { key: 'status', header: 'Holat', render: (r) => <StatusBadge status={r.status} /> }]} />
      )}
      <RequestDialog open={dialog} seller={seller} admin={admin} onClose={() => setDialog(false)} onSend={(v) => { setDialog(false); onRequest(seller, v); }} />
    </>
  );
}
