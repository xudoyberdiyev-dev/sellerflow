import { useState } from 'react'
import { api, fmtDate } from '../api'
import { Alert, Button, Card, PageTitle, Table, Td } from '../ui'
import { useLoad } from './useLoad'

interface Event { id: number; at: string; actorType: string; actorId: string | null; businessId: number | null; action: string; entityType: string | null; entityId: string | null; summary: string | null; sourceIp?: string | null }
interface Page { total: number; page: number; items: Event[] }

export function AuditPage() {
  const [page, setPage] = useState(0)
  const list = useLoad((signal) => api<Page>(`/audit?page=${page}&size=50`, { signal }), [page])
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / 50)) : 1
  return (
    <div>
      <PageTitle title="Audit jurnali" desc="Muhim amallar: kim, qachon, nima. Tijoriy qiymatlar yozilmaydi." />
      <Card>
        {list.error && <Alert>{list.error}</Alert>}
        <Table head={['Vaqt', 'Kim', 'Amal', 'Obyekt', 'Biznes', 'IP', 'Izoh']} empty={!!list.data && list.data.items.length === 0}>
          {list.data?.items.map((e) => (
            <tr key={e.id} className="hover:bg-stone-50">
              <Td className="text-stone-500">{fmtDate(e.at)}</Td>
              <Td>{e.actorType}{e.actorId ? <span className="text-stone-500"> · {e.actorId}</span> : null}</Td>
              <Td className="font-mono text-xs">{e.action}</Td>
              <Td className="text-stone-500">{e.entityType ? `${e.entityType} #${e.entityId}` : '—'}</Td>
              <Td className="text-stone-500">{e.businessId ?? '—'}</Td>
              <Td className="font-mono text-xs text-stone-500">{e.sourceIp ?? '—'}</Td>
              <Td className="max-w-[280px] truncate text-stone-500" >{e.summary ?? ''}</Td>
            </tr>
          ))}
        </Table>
        <div className="mt-4 flex items-center justify-between text-sm text-stone-500">
          <span>Sahifa {page + 1} / {pages}</span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Oldingi</Button>
            <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Keyingi</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
