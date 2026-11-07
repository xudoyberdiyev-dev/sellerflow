import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/index.css'
import './index.css'
import App from './App'
import { applyTheme, storedTheme } from './theme'

// O'tish davri (D-1…D-6): eski Tailwind sahifalari faqat yorug' mavzuda — standart 'light'.
// D-7 da standart tizim sozlamasiga (prefers-color-scheme) o'tkaziladi.
applyTheme(storedTheme() ?? 'light')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
