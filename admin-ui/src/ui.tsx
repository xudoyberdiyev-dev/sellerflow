import clsx from 'clsx'
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

// shadcn/ui uslubidagi minimal komponentlar (Tailwind)

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export function Button({ variant = 'primary', className, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...p}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium transition',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2',
        'disabled:pointer-events-none disabled:opacity-50',
        variant === 'primary' && 'bg-stone-900 text-white hover:bg-stone-800',
        variant === 'secondary' && 'border border-stone-300 bg-white text-stone-800 hover:bg-stone-100',
        variant === 'danger' && 'bg-red-600 text-white hover:bg-red-700',
        variant === 'ghost' && 'text-stone-600 hover:bg-stone-100 hover:text-stone-900',
        className,
      )}
    />
  )
}

export function Input(p: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...p}
      className={clsx(
        'h-9 w-full rounded-md border border-stone-300 bg-white px-3 text-sm placeholder:text-stone-400',
        'focus:border-stone-500 focus:outline-none focus:ring-2 focus:ring-stone-200',
        p.className,
      )}
    />
  )
}

export function Textarea(p: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...p}
      className={clsx(
        'min-h-[72px] w-full rounded-md border border-stone-300 bg-white px-3 py-2 text-sm',
        'focus:border-stone-500 focus:outline-none focus:ring-2 focus:ring-stone-200',
        p.className,
      )}
    />
  )
}

export function Select(p: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...p}
      className={clsx('h-9 rounded-md border border-stone-300 bg-white px-2 text-sm focus:outline-none focus:ring-2 focus:ring-stone-200', p.className)}
    />
  )
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-stone-700">{label}</span>
      {children}
      {hint && <span className="block text-xs text-stone-500">{hint}</span>}
    </label>
  )
}

export function Card({ title, actions, children, className }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={clsx('rounded-lg border border-stone-200 bg-white', className)}>
      {(title || actions) && (
        <header className="flex items-center justify-between gap-3 border-b border-stone-200 px-5 py-3">
          <h2 className="text-sm font-semibold text-stone-800">{title}</h2>
          <div className="flex items-center gap-2">{actions}</div>
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

const BADGE: Record<string, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  UP: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  ONBOARDING: 'bg-sky-50 text-sky-700 ring-sky-200',
  REQUESTED: 'bg-sky-50 text-sky-700 ring-sky-200',
  SUSPENDED: 'bg-amber-50 text-amber-800 ring-amber-200',
  DEGRADED: 'bg-amber-50 text-amber-800 ring-amber-200',
  DELETION_REQUESTED: 'bg-orange-50 text-orange-800 ring-orange-200',
  CLOSED: 'bg-stone-100 text-stone-600 ring-stone-200',
  EXPIRED: 'bg-stone-100 text-stone-600 ring-stone-200',
  DISABLED: 'bg-stone-100 text-stone-600 ring-stone-200',
  DECLINED: 'bg-red-50 text-red-700 ring-red-200',
  REVOKED: 'bg-red-50 text-red-700 ring-red-200',
  DOWN: 'bg-red-50 text-red-700 ring-red-200',
}

const LABEL: Record<string, string> = {
  ACTIVE: 'Faol', ONBOARDING: 'Ro‘yxatdan o‘tmoqda', SUSPENDED: 'Bloklangan', DELETION_REQUESTED: 'O‘chirish so‘ralgan',
  CLOSED: 'Yopilgan', REQUESTED: 'Kutilmoqda', APPROVED: 'Ruxsat berilgan', DECLINED: 'Rad etilgan', REVOKED: 'Bekor qilingan',
  EXPIRED: 'Muddati tugagan', DISABLED: 'O‘chirilgan', UP: 'Ishlayapti', DOWN: 'Ishlamayapti', DEGRADED: 'Muammo bor',
}

export function Badge({ value }: { value: string }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', BADGE[value] ?? 'bg-stone-100 text-stone-700 ring-stone-200')}>
      {LABEL[value] ?? value}
    </span>
  )
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'info' | 'success'; children: ReactNode }) {
  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      className={clsx(
        'rounded-md border px-3 py-2 text-sm',
        kind === 'error' && 'border-red-200 bg-red-50 text-red-800',
        kind === 'info' && 'border-sky-200 bg-sky-50 text-sky-900',
        kind === 'success' && 'border-emerald-200 bg-emerald-50 text-emerald-800',
      )}
    >
      {children}
    </div>
  )
}

export function Table({ head, children, empty }: { head: string[]; children: ReactNode; empty?: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-stone-200 text-xs uppercase tracking-wide text-stone-500">
            {head.map((h) => (
              <th key={h} className="whitespace-nowrap px-3 py-2 font-medium">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">{children}</tbody>
      </table>
      {empty && <p className="px-3 py-8 text-center text-sm text-stone-500">Ma’lumot yo‘q.</p>}
    </div>
  )
}

export function Td({ children, className }: { children: ReactNode; className?: string }) {
  return <td className={clsx('whitespace-nowrap px-3 py-2.5 align-middle', className)}>{children}</td>
}

export function Stat({ label, value, sub }: { label: string; value: ReactNode; sub?: string }) {
  return (
    <div className="rounded-lg border border-stone-200 bg-white px-4 py-3">
      <div className="text-xs font-medium text-stone-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums text-stone-900">{value}</div>
      {sub && <div className="mt-0.5 text-xs text-stone-500">{sub}</div>}
    </div>
  )
}

export function PageTitle({ title, desc }: { title: string; desc?: string }) {
  return (
    <div className="mb-5">
      <h1 className="text-xl font-semibold tracking-tight text-stone-900">{title}</h1>
      {desc && <p className="mt-1 text-sm text-stone-500">{desc}</p>}
    </div>
  )
}
