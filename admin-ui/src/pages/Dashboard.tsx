import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Me } from '../api'
import {
  Avatar, Button, CHANNELS, Card, CountdownChip, EmptyState, SegmentedControl, Skeleton, SkeletonText, StatCard, Tooltip,
  formatDate, formatNumber, formatPercent,
} from '../components'
import { GrantBadge, LoadError, PageHeader } from './common'
import { channelId } from './labels'
import { useLoad } from './useLoad'
import type { Grant } from './Grants'

interface Kpi { value: number; previous?: number; changePct?: number | null }
interface DashboardData {
  period: number; from: string; to: string
  kpis: { newSellers: Kpi; activeSellers: Kpi; savedCalculations: Kpi; activeProducts: Kpi; totalSellers: Kpi }
  series: { day: string; newSellers: number; activeSellers: number; savedCalculations: number }[]
  channels: { channel: string; count: number }[]
  funnel: { step: string; count: number }[]
  retention7d: { cohort: number; retained: number; pct: number | null }
}

const FUNNEL_LABEL: Record<string, string> = {
  STARTED: '/start bosgan', TERMS_ACCEPTED: 'Shartlarni qabul qilgan', BUSINESS_CREATED: 'Profilni yaratgan',
  FIRST_PRODUCT: 'Birinchi mahsulot', FIRST_CALCULATION: 'Birinchi saqlangan hisob',
}

/** Uzun davrda sparkline 14 nuqtaga siqiladi (yig'indi bo'yicha). */
function trendOf(values: number[], points = 14): number[] {
  if (values.length <= points) return values
  const size = Math.ceil(values.length / points)
  const out: number[] = []
  for (let i = 0; i < values.length; i += size) out.push(values.slice(i, i + size).reduce((a, b) => a + b, 0))
  return out
}

export function Dashboard({ me }: { me: Me }) {
  const [period, setPeriod] = useState('30')
  const nav = useNavigate()
  const dash = useLoad((signal) => api<DashboardData>(`/dashboard?period=${period}`, { signal }), [period])
  const grants = useLoad((signal) => api<Grant[]>('/grants', { signal }))
  const d = dash.data
  const loading = dash.loading && !d
  const openGrants = grants.data?.filter((g) => g.status === 'REQUESTED' || g.status === 'APPROVED').length

  const series = useMemo(() => ({
    newSellers: d?.series.map((x) => x.newSellers) ?? [],
    active: d?.series.map((x) => x.activeSellers) ?? [],
    calcs: d?.series.map((x) => x.savedCalculations) ?? [],
  }), [d])

  return (
    <div className="sf-page">
      <PageHeader title="Boshqaruv paneli"
        description="Platforma bo‘yicha umumiy ko‘rsatkichlar. Sotuvchilarning tijoriy ma’lumoti bu yerda ko‘rsatilmaydi."
        actions={<>
          <SegmentedControl label="Davr" value={period} onChange={setPeriod}
            options={[{ value: '7', label: '7 kun' }, { value: '30', label: '30 kun' }, { value: '90', label: '90 kun' }]} />
          <Button icon="refresh-cw" onClick={() => { dash.reload(); grants.reload() }} loading={dash.loading && !!d}>Yangilash</Button>
        </>} />

      {dash.error && !d ? <LoadError error={dash.error} onRetry={dash.reload} title="Ko‘rsatkichlarni yuklab bo‘lmadi" /> : (
        <>
          <div className="sf-grid-stats">
            <StatCard loading={loading} label="Faol sotuvchilar" icon="users" value={d && formatNumber(d.kpis.activeSellers.value)}
              delta={d?.kpis.activeSellers.changePct} trend={trendOf(series.active)} />
            <StatCard loading={loading} label="Yangi ro‘yxatdan o‘tganlar" icon="user-plus" value={d && formatNumber(d.kpis.newSellers.value)}
              delta={d?.kpis.newSellers.changePct} trend={trendOf(series.newSellers)} />
            <StatCard loading={loading} label="Saqlangan hisoblashlar" icon="calculator" value={d && formatNumber(d.kpis.savedCalculations.value)}
              delta={d?.kpis.savedCalculations.changePct} trend={trendOf(series.calcs)} />
            <StatCard loading={loading || (grants.loading && !grants.data)} label="Ochiq kirish so‘rovlari" icon="key-round"
              value={openGrants === undefined ? '—' : formatNumber(openGrants)} />
          </div>

          <div className="sf-grid-2">
            <Card title="Ro‘yxatdan o‘tishlar" description={d ? `Kunlik · ${formatDate(d.from)} – ${formatDate(d.to)}` : 'Kunlik'}>
              {loading ? <Skeleton height={168} radius={6} /> : d && <SignupChart series={d.series} />}
            </Card>
            <Card title="Asosiy savdo kanali" description="Sotuvchilar soni bo‘yicha (yopilgan akkauntlarsiz)">
              {loading ? <SkeletonText lines={5} /> : d && (d.channels.length === 0
                ? <EmptyState title="Hali sotuvchi yo‘q" description="Sotuvchilar botda ro‘yxatdan o‘tgach shu yerda ko‘rinadi." />
                : <ChannelBars data={d.channels} />)}
            </Card>
          </div>

          <div className="sf-grid-2">
            <Card title="Onboarding voronkasi" description="Shu davrda /start bosganlar qaysi bosqichgacha yetgan">
              {loading ? <SkeletonText lines={5} gap={14} /> : d && <Funnel steps={d.funnel} />}
            </Card>
            <Card title="7 kunlik qaytish" description="Ro‘yxatdan o‘tgandan 7+ kun keyin botga qaytganlar ulushi">
              {loading ? <SkeletonText lines={3} /> : d && (d.retention7d.cohort === 0
                ? <EmptyState icon="hourglass" title="Hali yetarli ma’lumot yo‘q"
                    description="Bu davrda kamida 7 kun oldin ro‘yxatdan o‘tgan foydalanuvchi bo‘lishi kerak." />
                : (
                  <div className="sf-stack">
                    <span className="sf-big sf-num">{formatPercent(d.retention7d.pct ?? 0, 1)}</span>
                    <span className="sf-cell-sub">{formatNumber(d.retention7d.cohort)} ta foydalanuvchidan {formatNumber(d.retention7d.retained)} tasi qaytgan</span>
                    <dl className="sf-dl">
                      <dt>Faol mahsulotlar</dt><dd className="sf-num">{formatNumber(d.kpis.activeProducts.value)}</dd>
                      <dt>Faol akkauntlar (jami)</dt><dd className="sf-num">{formatNumber(d.kpis.totalSellers.value)}</dd>
                    </dl>
                  </div>
                ))}
            </Card>
          </div>

          <Card title={me.role === 'SUPERADMIN' ? 'So‘nggi kirish so‘rovlari' : 'Mening so‘nggi so‘rovlarim'} padded={false}
            actions={<Button size="sm" variant="ghost" iconRight="arrow-right" onClick={() => nav('/grants')}>Hammasi</Button>}>
            {grants.loading && !grants.data ? <div style={{ padding: 16 }}><SkeletonText lines={4} gap={14} /></div>
              : grants.error ? <EmptyState kind="error" title="So‘rovlarni yuklab bo‘lmadi" description={grants.error} />
              : !grants.data?.length ? <EmptyState icon="key-round" title="Kirish so‘rovlari yo‘q" description="Sotuvchi kartasidan tijoriy ma’lumotga kirish so‘rashingiz mumkin." />
              : (
                <div className="sf-list">
                  {grants.data.slice(0, 5).map((g) => (
                    <div key={g.id} className="sf-list__item">
                      <Avatar name={`Sotuvchi ${g.telegramUserId}`} size={28} />
                      <div className="sf-cell-stack sf-list__main">
                        <span><span className="sf-id">{g.telegramUserId}</span></span>
                        <span className="sf-cell-sub sf-truncate">{g.purpose}</span>
                      </div>
                      <span className="sf-cell-sub sf-num">{formatDate(g.requestedAt)}</span>
                      {g.status === 'APPROVED' && g.expiresAt && <CountdownChip expiresAt={g.expiresAt} onExpire={grants.reload} />}
                      <GrantBadge status={g.status} />
                    </div>
                  ))}
                </div>
              )}
          </Card>
        </>
      )}
    </div>
  )
}

function SignupChart({ series }: { series: DashboardData['series'] }) {
  const max = Math.max(1, ...series.map((x) => x.newSellers))
  const total = series.reduce((a, x) => a + x.newSellers, 0)
  return (
    <div>
      <div className="sf-bars" role="img" aria-label={`Kunlik ro‘yxatdan o‘tishlar, jami ${total} ta`}>
        {series.map((x, i) => (
          <div key={x.day} className="sf-bars__col">
            <Tooltip content={`${formatDate(x.day)} · ${x.newSellers} ta`}>
              <div className="sf-bars__bar" tabIndex={series.length <= 31 ? 0 : -1} data-last={i === series.length - 1}
                aria-label={`${formatDate(x.day)}: ${x.newSellers} ta`} style={{ height: `${(x.newSellers / max) * 100}%` }} />
            </Tooltip>
          </div>
        ))}
      </div>
      <div className="sf-bars__axis"><span>{formatDate(series[0].day)}</span><span>{formatDate(series[series.length - 1].day)}</span></div>
    </div>
  )
}

function ChannelBars({ data }: { data: { channel: string; count: number }[] }) {
  const total = data.reduce((a, b) => a + b.count, 0) || 1
  const meta = (c: string) => CHANNELS.find((x) => x.id === channelId(c)) ?? CHANNELS[6]
  return (
    <div className="sf-stack">
      <div className="sf-share" role="img" aria-label="Kanallar ulushi">
        {data.map((d) => <div key={d.channel} style={{ flex: d.count, background: meta(d.channel).color }} />)}
      </div>
      <div className="sf-legend">
        {data.map((d) => (
          <div key={d.channel} className="sf-legend__item">
            <span className="sf-legend__dot" style={{ background: meta(d.channel).color }} />
            <span style={{ flex: 1 }}>{meta(d.channel).label}</span>
            <span className="sf-num" style={{ fontWeight: 500 }}>{formatNumber(d.count)}</span>
            <span className="sf-num sf-legend__pct">{formatPercent((d.count / total) * 100, 1)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function Funnel({ steps }: { steps: { step: string; count: number }[] }) {
  const top = steps[0]?.count ?? 0
  if (top === 0) return <EmptyState icon="user-plus" title="Bu davrda yangi foydalanuvchi yo‘q" description="Davrni kengaytirib ko‘ring." />
  return (
    <div className="sf-funnel">
      {steps.map((s) => (
        <div key={s.step} className="sf-funnel__row">
          <span>{FUNNEL_LABEL[s.step] ?? s.step}</span>
          <div className="sf-funnel__track" aria-hidden="true"><div className="sf-funnel__fill" style={{ width: `${(s.count / top) * 100}%` }} /></div>
          <span className="sf-num sf-funnel__num">{formatNumber(s.count)} · {formatPercent((s.count / top) * 100, 0)}</span>
        </div>
      ))}
    </div>
  )
}
