import React from 'react';

/** Shimmering placeholder block. Compose to match the final layout. */
export function Skeleton({ width = '100%', height = 14, radius = 4, circle, style }) {
  return <span className="sf-skel" aria-hidden="true" style={{ width: circle ? height : width, height, borderRadius: circle ? '50%' : radius, ...style }} />;
}

/** Multi-line text skeleton. */
export function SkeletonText({ lines = 3, gap = 8 }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap }} aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => <Skeleton key={i} width={i === lines - 1 ? '60%' : '100%'} />)}
    </div>
  );
}
