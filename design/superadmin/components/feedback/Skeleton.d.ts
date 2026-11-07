import * as React from "react";
export interface SkeletonProps { width?: number | string; height?: number; radius?: number; circle?: boolean; style?: React.CSSProperties; }
export declare function Skeleton(props: SkeletonProps): JSX.Element;
export declare function SkeletonText(props: { lines?: number; gap?: number }): JSX.Element;
