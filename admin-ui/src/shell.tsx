// D-3: ilova bo'ylab toast va tasdiqlash dialogi (sabab bilan) konteksti.
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { ConfirmDialog, ToastStack, useToasts, type ModalTone, type ToastProps } from './components'

export interface ConfirmOptions {
  title: ReactNode
  description?: ReactNode
  confirmLabel?: ReactNode
  tone?: ModalTone
  /** Sabab majburiy (kamida 10 belgi) — xavfli amallar uchun. */
  requireReason?: boolean
}

interface ShellApi {
  toast: (t: ToastProps) => void
  /** Tasdiqlansa — sabab (requireReason bo'lmasa bo'sh satr), bekor qilinsa — null. */
  confirm: (o: ConfirmOptions) => Promise<string | null>
}

const Ctx = createContext<ShellApi | null>(null)

export function ShellProvider({ children }: { children: ReactNode }) {
  const { toasts, push, dismiss } = useToasts()
  const [dlg, setDlg] = useState<ConfirmOptions | null>(null)
  const resolver = useRef<((v: string | null) => void) | null>(null)

  const confirm = useCallback((o: ConfirmOptions) => new Promise<string | null>((resolve) => {
    resolver.current?.(null)
    resolver.current = resolve
    setDlg(o)
  }), [])

  const close = (v: string | null) => {
    resolver.current?.(v)
    resolver.current = null
    setDlg(null)
  }

  return (
    <Ctx.Provider value={{ toast: push, confirm }}>
      {children}
      <ConfirmDialog
        open={!!dlg}
        title={dlg?.title ?? ''}
        description={dlg?.description}
        confirmLabel={dlg?.confirmLabel}
        tone={dlg?.tone ?? 'danger'}
        requireReason={dlg?.requireReason}
        minReasonLength={10}
        onClose={() => close(null)}
        onConfirm={(reason) => close(reason)}
      />
      <ToastStack toasts={toasts} onDismiss={dismiss} />
    </Ctx.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useShell(): ShellApi {
  const v = useContext(Ctx)
  if (!v) throw new Error('useShell must be used inside ShellProvider')
  return v
}
