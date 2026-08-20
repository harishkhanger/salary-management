import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type ToastKind = 'info' | 'success' | 'error'

interface Toast {
  id: number
  kind: ToastKind
  text: string
}

const ToastContext = createContext<(kind: ToastKind, text: string) => void>(() => {})

export function ToasterProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const push = useCallback((kind: ToastKind, text: string) => {
    const id = nextId.current++
    setToasts((prev) => [...prev, { id, kind, text }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4200)
  }, [])

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toaster">
        {toasts.map((t) => (
          <div key={t.id} className={`toast${t.kind !== 'info' ? ` toast-${t.kind}` : ''}`}>
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const push = useContext(ToastContext)
  return {
    info: (text: string) => push('info', text),
    success: (text: string) => push('success', text),
    error: (text: string) => push('error', text),
  }
}
