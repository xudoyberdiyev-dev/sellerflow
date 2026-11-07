import React, { useState, useEffect } from 'react';

function ChannelBars({ data }) {
  const total = data.reduce((a, b) => a + b.n, 0);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', height: 12, borderRadius: 4, overflow: 'hidden', gap: 2 }} role="img" aria-label="Kanallar ulushi">
        {data.map((d) => <div key={d.id} style={{ flex: d.n, background: CHANNELS.find((c) => c.id === d.id).color }} />)}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '8px 16px' }}>
        {data.map((d) => {
          const c = CHANNELS.find((x) => x.id === d.id);
          return (
            <div key={d.id} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 8, height: 8, borderRadius: 2, background: c.color, flex: 'none' }} />
              <span style={{ flex: 1 }}>{c.label}</span>
              <span className="sf-num" style={{ fontWeight: 500 }}>{formatNumber(d.n)}</span>
              <span className="sf-num" style={{ color: 'var(--text-tertiary)', width: 56, textAlign: 'right' }}>{formatPercent((d.n / total) * 100, 1)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function SignupChart({ data }) {
  const max = Math.max(...data);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, height: 160, paddingTop: 8 }} role="img" aria-label="Kunlik ro‘yxatdan o‘tishlar">
      {data.map((v, i) => (
        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%', justifyContent: 'flex-end' }}>
          <Tooltip content={`${String(23 + i > 31 ? i - 8 : 23 + i).padStart(2, '0')}.${23 + i > 31 ? '11' : '10'}.2026 · ${v} ta`}>
            <div tabIndex={0} style={{ width: '100%', height: (v / max) * 130, background: i === data.length - 1 ? 'var(--chart-1)' : 'var(--brand-200)', borderRadius: '3px 3px 0 0', minWidth: 8 }} />
          </Tooltip>
        </div>
      ))}
    </div>
  );
}

export function DashboardPage({ demoState, onNavigate }) {
  const [range, setRange] = useState(undefined);
  const [loading, setLoading] = useState(false);
  useEffect(() => { setLoading(true); const t = setTimeout(() => setLoading(false), 600); return () => clearTimeout(t); }, [range]);
  const isLoading = loading || demoState === 'loading';

  return (
    <>
      <PageHeader title="Boshqaruv paneli" description="Platforma bo‘yicha umumiy ko‘rsatkichlar. Sotuvchilarning tijoriy ma’lumoti bu yerda ko‘rsatilmaydi."
        actions={<DateRangePicker value={range} onChange={setRange} today={new Date(2026, 10, 5)} />} />
      {demoState === 'error' ? (
        <Card><EmptyState kind="error" title="Ko‘rsatkichlarni yuklab bo‘lmadi" description="Server 30 soniya ichida javob bermadi. Sahifani yangilang yoki birozdan keyin urinib ko‘ring." actions={<Button icon="rotate-cw">Qayta urinish</Button>} /></Card>
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <StatCard loading={isLoading} label="Faol sotuvchilar" icon="users" value="1 284" delta={12.4} trend={[8, 9, 9, 11, 10, 12, 13, 14]} />
            <StatCard loading={isLoading} label="Yangi ro‘yxatdan o‘tganlar" icon="user-plus" value="86" delta={8.1} trend={[5, 6, 4, 7, 6, 8, 9, 9]} />
            <StatCard loading={isLoading} label="Bot hisoblashlari" icon="calculator" value="24 917" delta={-3.2} trend={[14, 13, 15, 12, 13, 12, 11, 12]} />
            <StatCard loading={isLoading} label="Ochiq kirish so‘rovlari" icon="key-round" value="3" delta={0} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 420px), 1fr))', gap: 12 }}>
            <Card title="Ro‘yxatdan o‘tishlar" description="Kunlik, oxirgi 14 kun">
              {isLoading ? <Skeleton height={160} radius={6} /> : <SignupChart data={SIGNUPS} />}
            </Card>
            <Card title="Asosiy savdo kanali" description="Sotuvchilar soni bo‘yicha">
              {isLoading ? <SkeletonText lines={5} /> : <ChannelBars data={CHANNEL_COUNTS} />}
            </Card>
          </div>
          <Card title="So‘nggi kirish so‘rovlari" padded={false} actions={<Button size="sm" variant="ghost" iconRight="arrow-right" onClick={() => onNavigate('access')}>Hammasi</Button>}>
            {isLoading ? <div style={{ padding: 16 }}><SkeletonText lines={4} gap={14} /></div> : (
              <div>
                {ACCESS_REQUESTS.slice(0, 4).map((r, i) => (
                  <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 16px', borderTop: i ? '1px solid var(--border-default)' : 0, flexWrap: 'wrap' }}>
                    <Avatar name={r.seller} size={28} />
                    <div className="sf-cell-stack" style={{ flex: 1, minWidth: 160 }}><span>{r.seller}</span><span className="sf-cell-sub">{r.reason}</span></div>
                    <span className="sf-cell-sub sf-num">{r.created}</span>
                    {r.status === 'Ruxsat berilgan' && r.expiresIn ? <CountdownChip expiresAt={Date.now() + r.expiresIn * 1000} /> : null}
                    <StatusBadge status={r.status} />
                  </div>
                ))}
              </div>
            )}
          </Card>
        </>
      )}
    </>
  );
}
