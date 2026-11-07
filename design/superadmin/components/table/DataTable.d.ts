import * as React from "react";
/** @startingPoint section="Components" subtitle="Data table with sort, columns, selection, density, pagination" viewport="1100x520" */
export interface Column<R = any> { key: string; header: React.ReactNode; sortable?: boolean; align?: "left" | "right"; width?: number | string; render?: (row: R) => React.ReactNode; hideable?: boolean; defaultHidden?: boolean; }
export interface MenuItem { label?: React.ReactNode; icon?: string; onSelect?: () => void; danger?: boolean; disabled?: boolean; separator?: boolean; heading?: string; }
export interface DataTableProps<R = any> {
  columns: Column<R>[]; rows: R[]; rowKey?: string; selectable?: boolean; pageSize?: number; maxHeight?: number;
  density?: "comfortable" | "compact"; onDensityChange?: (d: "comfortable" | "compact") => void;
  rowActions?: (row: R) => MenuItem[]; onRowClick?: (row: R) => void;
  loading?: boolean; error?: string; onRetry?: () => void; emptyTitle?: string; emptyDescription?: string;
  toolbar?: React.ReactNode; bulkActions?: (selectedKeys: any[]) => React.ReactNode; caption?: string; }
export declare function DataTable<R = any>(props: DataTableProps<R>): JSX.Element;
