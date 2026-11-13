import { api } from '../api'
import { Badge, Button, Card, InlineAlert, SkeletonText, formatDateTime, formatNumber, type Tone } from '../components'
import { Icon } from '../components'
import { LoadError, PageHeader } from './common'
import { useLoad } from './useLoad'

export interface Health {
  database: string; dbLatencyMs: number | null; bot: string; lastPollOkAt: string | null; lastErrorAt: string | null; lastError: string | null
  processedUpdates: number; activeDrafts: number; engineVersion: string; uptimeSeconds: number; serverTime: string
  backup?: { state: string; lastSuccessAt: string | null; lastSuccessFile: string | null; lastFailureAt: string | null; lastError: string | null; consecutiveFailures: number }
}

const STATE: Record<string, { label: string; tone: Tone }> = {
  UP: { label: 'Ishlayapti', tone: 'success' }, DOWN: { label: 'Ishlamayapti', tone: 'danger' },
  DEGRADED: { label: 'Muammo bor', tone: 'warning' }, DISABLED: { label: 'O‘chiq', tone: 'neutral' },
  OK: { label: 'Muvaffaqiyatli', tone: 'success' }, FAILED: { label: 'Xato', tone: 'danger' },
  STALE: { label: 'Eskirgan', tone: 'warning' }, MISSING: { label: 'Ishlamayapti', tone: 'danger' }, UNKNOWN: { label: 'Ma’lumot yo‘q', tone: 'neutral' },
}
const badge = (s: string) => <Badge tone={STATE[s]?.tone ?? 'neutral'}>{STATE[s]?.label ?? s}</Badge>
const dt = (s?: string | null) => (s ? formatDateTime(s) : '—')

function uptime(sec: number) {
  const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60)
  return `${d ? d + ' kun ' : ''}${h} soat ${m} daqiqa`
}

export function SystemStatus() {
  const health = useLoad((signal) => api<Health>('/health', { signal }))
  const h = health.data
  const problems = h ? [
    h.database !== 'UP' && 'Ma’lumotlar bazasi javob bermayapti.',
    h.bot === 'DEGRADED' && 'Telegram bot so‘nggi 2 daqiqada muvaffaqiyatli so‘rov qilmagan.',
    (h.backup?.state === 'FAILED' || h.backup?.state === 'STALE' || h.backup?.state === 'MISSING') && 'Zaxira nusxa muammosi — OPERATIONS.md dagi tiklash bo‘limini ko‘ring.',
  ].filter(Boolean) as string[] : []

  return (
    <div className="sf-page">
      <PageHeader title="Tizim holati" description="Server, ma’lumotlar bazasi, Telegram bot va zaxira nusxalar."
        actions={<Button icon="refresh-cw" loading={health.loading && !!h} onClick={health.reload}>Yangilash</Button>} />
      {health.error && !h ? <LoadError error={health.error} onRetry={health.reload} /> : !h ? <Card><SkeletonText lines={8} gap={14} /></Card> : (
        <>
          {problems.length > 0
            ? <InlineAlert tone="warning" title="Diqqat talab qilinadi">{problems.join(' ')}</InlineAlert>
            : <InlineAlert tone="success" title="Barcha xizmatlar ishlayapti">Oxirgi tekshiruv: {dt(h.serverTime)}</InlineAlert>}
          <div className="sf-grid-2">
            <Card title="Xizmatlar">
              <div>
                <StatusRow icon="database" title="Ma’lumotlar bazasi" state={h.database}
                  sub={h.dbLatencyMs != null ? `Javob vaqti: ${h.dbLatencyMs} ms` : undefined} />
                <StatusRow icon="bot" title="Telegram bot" state={h.bot}
                  sub={h.bot === 'DISABLED' ? 'Long polling o‘chiq (token berilmagan yoki o‘chirilgan)' : `Oxirgi muvaffaqiyatli so‘rov: ${dt(h.lastPollOkAt)}`} />
                <StatusRow icon="database-backup" title="Zaxira nusxa" state={h.backup?.state ?? 'UNKNOWN'}
                  sub={h.backup?.lastSuccessAt ? `Oxirgi muvaffaqiyatli: ${dt(h.backup.lastSuccessAt)}` : 'Hali muvaffaqiyatli nusxa yo‘q'} />
              </div>
            </Card>
            <Card title="Ko‘rsatkichlar">
              <dl className="sf-dl">
                <dt>Qayta ishlangan update’lar</dt><dd className="sf-num">{formatNumber(h.processedUpdates)}</dd>
                <dt>Faol qoralamalar</dt><dd className="sf-num">{formatNumber(h.activeDrafts)}</dd>
                <dt>Hisob dvigateli versiyasi</dt><dd className="sf-id">{h.engineVersion}</dd>
                <dt>Ishlash vaqti</dt><dd>{uptime(h.uptimeSeconds)}</dd>
                <dt>Server vaqti</dt><dd className="sf-num">{dt(h.serverTime)}</dd>
              </dl>
            </Card>
          </div>
          {(h.lastErrorAt || (h.backup && h.backup.consecutiveFailures > 0)) && (
            <Card title="So‘nggi xatolar">
              <dl className="sf-dl">
                {h.lastErrorAt && <><dt>Bot · {dt(h.lastErrorAt)}</dt><dd className="sf-id">{h.lastError ?? '—'}</dd></>}
                {h.backup && h.backup.consecutiveFailures > 0 && <>
                  <dt>Zaxira · {dt(h.backup.lastFailureAt)}</dt>
                  <dd><span className="sf-id">{h.backup.lastError ?? '—'}</span> <span className="sf-cell-sub">({h.backup.consecutiveFailures} marta ketma-ket)</span></dd>
                </>}
              </dl>
            </Card>
          )}
        </>
      )}
    </div>
  )
}

function StatusRow({ icon, title, state, sub }: { icon: string; title: string; state: string; sub?: string }) {
  return (
    <div className="sf-status-row">
      <span className="sf-status-row__icon"><Icon name={icon} size={18} /></span>
      <div className="sf-cell-stack" style={{ flex: 1 }}><span style={{ fontWeight: 500 }}>{title}</span>{sub && <span className="sf-cell-sub">{sub}</span>}</div>
      {badge(state)}
    </div>
  )
}
