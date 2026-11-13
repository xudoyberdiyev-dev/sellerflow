import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/index.css'
import App from './App'
import { ErrorBoundary } from './pages/common'
import { applyTheme, storedTheme, systemTheme } from './theme'

// D-7: mavzu — foydalanuvchi tanlovi (localStorage), bo'lmasa tizim sozlamasi (prefers-color-scheme)
applyTheme(storedTheme() ?? systemTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* 13-noyabr LOW: ildiz darajasida ham — Login yoki Topbar xatosi oq ekran bermaydi */}
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)
