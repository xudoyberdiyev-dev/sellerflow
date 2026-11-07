import { RefreshCw } from 'lucide-react'
import { api, fmtDate } from '../api'
import { Alert, Badge, Button, Card, PageTitle, Stat } from '../ui'
import { useLoad } from './useLoad'

interface Stats {
  usersByStatus: Record<string, number>
  businesses: number
  newSellersLast7Days: number
  activeProducts: number
  savedCalculations: number
  savedCalculationsLast7Days: number
  partialEstimateShare: number
  channels: { channel: string; count: number }[]
}
interface Health {
  database: string; bot: string; lastPollOkAt: string | null; lastErrorAt: string | null; lastError: string | null
  processedUpdates: number; activeDrafts: number; engineVersion: string; uptimeSeconds: number; serverTime: string
  backup?: { state: string; lastSuccessAt: string | null; lastError: string | null; consecutiveFailures: number }
}

const CHANNEL: Record<string, string> = {
  UZUM: 'Uzum Market', WILDBERRIES: 'Wildberries', OZON: 'Ozon', INSTAGRAM: 'Instagram',
  TELEGRAM_SALES: 'Telegram savdo', OFFLINE_STORE: 'Offline do‘kon', OTHER: 'Boshqa',
}

export function Dashboard() {
  const stats = useLoad(() => api<Stats>('/stats'))
  const health = useLoad(() => api<Health>('/health'))
  const s = stats.data
  const h = health.data
  const maxCh = Math.max(1, ...(s?.channels.map((c) => c.count) ?? [1]))

  return (
    <div>
      <div className="flex items-start justify-between">
        <PageTitle title="Boshqaruv paneli" desc="Faqat agregat ko‘rsatkichlar — sotuvchilarning tijoriy qiymatlari bu yerda ko‘rsatilmaydi." />
        <Button variant="secondary" onClick={() => { stats.reload(); health.reload() }}><RefreshCw size={14} /> Yangilash</Button>
      </div>
      {stats.error && <Alert>{stats.error}</Alert>}
      {s && (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label="Faol sotuvchilar" value={s.usersByStatus.ACTIVE ?? 0} sub={`+${s.newSellersLast7Days} oxirgi 7 kunda`} />
          <Stat label="Ro‘yxatdan o‘tmoqda" value={s.usersByStatus.ONBOARDING ?? 0} />
          <Stat label="Saqlangan hisoblashlar" value={s.savedCalculations} sub={`${s.savedCalculationsLast7Days} oxirgi 7 kunda`} />
          <Stat label="Faol mahsulotlar" value={s.activeProducts} />
          <Stat label="Taxminiy natijalar ulushi" value={`${s.partialEstimateShare}%`} sub="UNKNOWN / NOT_SET xarajat bilan" />
          <Stat label="Bloklangan" value={s.usersByStatus.SUSPENDED ?? 0} />
          <Stat label="O‘chirish so‘ralgan" value={s.usersByStatus.DELETION_REQUESTED ?? 0} />
          <Stat label="Yopilgan" value={s.usersByStatus.CLOSED ?? 0} />
        </div>
      )}
      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card title="Asosiy savdo kanallari">
          {s && s.channels.length === 0 && <p className="text-sm text-stone-500">Hali sotuvchi yo‘q.</p>}
          <div className="space-y-2.5">
            {s?.channels.map((c) => (
              <div key={c.channel}>
                <div className="mb-1 flex justify-between text-sm">
                  <span>{CHANNEL[c.channel] ?? c.channel}</span>
                  <span className="tabular-nums text-stone-500">{c.count}</span>
                </div>
                <div className="h-2 rounded-full bg-stone-100">
                  <div className="h-2 rounded-full bg-brand-500" style={{ width: `${(c.count / maxCh) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </Card>
        <Card title="Tizim holati">
          {health.error && <Alert>{health.error}</Alert>}
          {h && (
            <dl className="grid grid-cols-2 gap-y-2.5 text-sm">
              <dt className="text-stone-500">Ma’lumotlar bazasi</dt><dd><Badge value={h.database} /></dd>
              <dt className="text-stone-500">Telegram bot</dt><dd>{h.bot === 'DISABLED' ? <span className="text-stone-500">o‘chiq (token yo‘q)</span> : <Badge value={h.bot} />}</dd>
              <dt className="text-stone-500">Oxirgi muvaffaqiyatli so‘rov</dt><dd>{fmtDate(h.lastPollOkAt)}</dd>
              <dt className="text-stone-500">Oxirgi xato</dt><dd>{h.lastErrorAt ? `${fmtDate(h.lastErrorAt)} — ${h.lastError}` : '—'}</dd>
              <dt className="text-stone-500">Backup</dt>
              <dd>
                {!h.backup || h.backup.state === 'UNKNOWN' ? <span className="text-stone-500">ma’lumot yo‘q</span> : <Badge value={h.backup.state === 'OK' ? 'UP' : 'DOWN'} />}
                {h.backup?.lastSuccessAt && <span className="ml-2 text-xs text-stone-500">oxirgi: {fmtDate(h.backup.lastSuccessAt)}</span>}
                {h.backup?.lastError && h.backup.consecutiveFailures > 0 && <div className="mt-0.5 text-xs text-red-700">{h.backup.lastError}</div>}
              </dd>
              <dt className="text-stone-500">Qayta ishlangan update’lar</dt><dd className="tabular-nums">{h.processedUpdates}</dd>
              <dt className="text-stone-500">Faol qoralamalar</dt><dd className="tabular-nums">{h.activeDrafts}</dd>
              <dt className="text-stone-500">Engine versiyasi</dt><dd className="font-mono">{h.engineVersion}</dd>
              <dt className="text-stone-500">Ishlash vaqti</dt><dd>{Math.floor(h.uptimeSeconds / 3600)} soat {Math.floor((h.uptimeSeconds % 3600) / 60)} daqiqa</dd>
            </dl>
          )}
        </Card>
      </div>
    </div>
  )
}
