import * as React from "react";
/** @startingPoint section="Components" subtitle="KPI card with delta and sparkline" viewport="700x180" */
export interface StatCardProps { label: React.ReactNode; value: React.ReactNode; delta?: number; deltaLabel?: string; trend?: number[]; icon?: string; loading?: boolean; invertDelta?: boolean; }
export declare function StatCard(props: StatCardProps): JSX.Element;
export interface SparklineProps { data: number[]; width?: number; height?: number; color?: string; fill?: boolean; label?: string; }
export declare function Sparkline(props: SparklineProps): JSX.Element | null;
