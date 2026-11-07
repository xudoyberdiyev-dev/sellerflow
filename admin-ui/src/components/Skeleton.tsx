import type { CSSProperties } from 'react'

export interface SkeletonProps { width?: number | string; height?: number; radius?: number; circle?: boolean; style?: CSSProperties }

/** Yuklanish o'rinbosari. */
export function Skeleton({ width = '100%', height = 14, radius = 4, circle, style }: SkeletonProps) {
  return <span className="sf-skel" aria-hidden="true" style={{ width: circle ? height : width, height, borderRadius: circle ? '50%' : radius, ...style }} />
}

/** Ko'p qatorli matn o'rinbosari. */
export function SkeletonText({ lines = 3, gap = 8 }: { lines?: number; gap?: number }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap }} aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => <Skeleton key={i} width={i === lines - 1 ? '60%' : '100%'} />)}
    </div>
  )
}
