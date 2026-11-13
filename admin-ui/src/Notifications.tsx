// D-6: Topbar bildirishnomalari — audit asosidagi so'nggi muhim hodisalar (GET /notifications, har 60 s).
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api } from './api'
import { Button, EmptyState, Icon, IconButton, Popover, formatDateTime } from './components'

interface Item { id: number; at: string; action: string; actorType: string; actorId: string | null; entityType: string | null; entityId: string | null; summary: string | null }
interface Resp { items: Item[]; backupState: string }

const LABEL: Record<string, { text: string; icon: string; to?: string }> = {
  ACCESS_APPROVED: { text: 'Sotuvchi kirishga ruxsat berdi', icon: 'lock-open', to: '/grants' },
  ACCESS_DECLINED: { text: 'Sotuvchi so‘rovni rad etdi', icon: 'circle-x', to: '/grants' },
  ACCESS_REVOKED: { text: 'Kirish ruxsati bekor qilindi', icon: 'lock', to: '/grants' },
  ADMIN_LOCKED: { text: 'Admin hisobi qulflandi (ko‘p xato urinish)', icon: 'shield-alert', to: '/audit' },
  ADMIN_CREATED: { text: 'Yangi admin yaratildi', icon: 'user-plus', to: '/admins' },
  ADMIN_RECOVERED: { text: 'Admin hisobi tiklandi', icon: 'rotate-ccw-key', to: '/audit' },
  ADMIN_STATUS_CHANGE: { text: 'Sotuvchi holati o‘zgartirildi', icon: 'ban', to: '/sellers' },
  DELETION_REQUESTED: { text: 'Sotuvchi akkauntni o‘chirishni so‘radi', icon: 'trash-2', to: '/sellers' },
  ACCOUNT_CLOSED: { text: 'Akkaunt yopildi', icon: 'archive', to: '/audit' },
  SUPPORT_UPDATED: { text: 'Qo‘llab-quvvatlash kontakti o‘zgardi', icon: 'life-buoy', to: '/support' },
  EXPORT_SELLERS: { text: 'Sotuvchilar eksport qilindi', icon: 'file-down', to: '/audit' },
  EXPORT_AUDIT: { text: 'Audit eksport qilindi', icon: 'file-down', to: '/audit' },
}
const KEY = 'sf.notif.seen'
const readSeen = () => { try { return Number(localStorage.getItem(KEY) ?? 0) } catch { return 0 } }
const writeSeen = (id: number) => { try { localStorage.setItem(KEY, String(id)) } catch { /* storage yo'q */ } }

export function Notifications() {
  const [data, setData] = useState<Resp | null>(null)
  const [seen, setSeen] = useState(readSeen)
  const stopped = useRef(false)
  const nav = useNavigate()
  useEffect(() => {
    let timer: number | undefined
    const ctrl = new AbortController()
    const tick = async () => {
      if (stopped.current) return
      try {
        setData(await api<Resp>('/notifications', { signal: ctrl.signal }))
      } catch (e) {
        if (ctrl.signal.aborted) return
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) { stopped.current = true; return }
      }
      timer = window.setTimeout(tick, 60_000)
    }
    void tick()
    return () => { ctrl.abort(); window.clearTimeout(timer) }
  }, [])
  const items = data?.items ?? []
  const backupBad = ['FAILED', 'STALE', 'MISSING'].includes(data?.backupState ?? '')
  const unread = items.filter((i) => i.id > seen).length + (backupBad ? 1 : 0)
  const markRead = () => { const top = items[0]?.id ?? 0; writeSeen(top); setSeen(top) }

  return (
    <Popover align="end" width={380} label="Bildirishnomalar" trigger={(p) => (
      <span className="sf-bell">
        <IconButton icon="bell" label={unread ? `Bildirishnomalar: ${unread} ta yangi` : 'Bildirishnomalar'} {...p} />
        {unread > 0 && <span className="sf-bell__dot" aria-hidden="true" />}
      </span>
    )}>
      <div className="sf-row sf-row--between" style={{ marginBottom: 8 }}>
        <strong>Bildirishnomalar</strong>
        {items.some((i) => i.id > seen) && <Button size="sm" variant="ghost" icon="check" onClick={markRead}>O‘qildi</Button>}
      </div>
      <div className="sf-notif-panel">
        {backupBad && (
          <div className="sf-list__item" style={{ borderTop: 0 }}>
            <Icon name="server-crash" size={16} />
            <div className="sf-cell-stack sf-list__main"><span>Zaxira nusxa muammosi</span><span className="sf-cell-sub">Tizim holati sahifasini tekshiring</span></div>
            <Button size="sm" variant="ghost" onClick={() => nav('/status')}>Ochish</Button>
          </div>
        )}
        {items.length === 0 && !backupBad ? <EmptyState icon="bell" title="Yangi hodisa yo‘q" /> : items.map((i) => {
          const l = LABEL[i.action] ?? { text: i.action, icon: 'info' }
          return (
            <div key={i.id} className="sf-list__item" data-clickable={!!l.to || undefined}>
              <Icon name={l.icon} size={16} />
              <div className="sf-cell-stack sf-list__main">
                <span style={{ fontWeight: i.id > seen ? 600 : 400 }}>{l.text}</span>
                <span className="sf-cell-sub sf-num">{formatDateTime(i.at)}{i.entityType === 'ACCESS_GRANT' && i.entityId ? ` · so‘rov #${i.entityId}` : ''}</span>
              </div>
              {l.to && <IconButton icon="arrow-right" size="sm" label="Ochish" onClick={() => nav(l.to!)} />}
            </div>
          )
        })}
      </div>
    </Popover>
  )
}
