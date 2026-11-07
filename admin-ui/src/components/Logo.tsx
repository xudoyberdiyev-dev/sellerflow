const SHIELD = 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z'

export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <span className="sf-logo__mark" style={{ width: size, height: size }} aria-hidden="true">
      <svg width={size * 0.6} height={size * 0.6} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d={SHIELD} /><path d="m9 12 2 2 4-4" />
      </svg>
    </span>
  )
}

export interface LogoProps { variant?: 'full' | 'mark'; size?: number; href?: string }

/** SellerFlow Admin logotipi. variant="full" (belgi + yozuv) yoki "mark". */
export function Logo({ variant = 'full', size = 28, href }: LogoProps) {
  const inner = (
    <>
      <LogoMark size={size} />
      {variant === 'full' && <span className="sf-logo__word" style={{ fontSize: Math.round(size * 0.54) }}>SellerFlow <span>Admin</span></span>}
    </>
  )
  return href
    ? <a className="sf-logo" href={href} aria-label="SellerFlow Admin">{inner}</a>
    : <span className="sf-logo" aria-label="SellerFlow Admin">{inner}</span>
}
