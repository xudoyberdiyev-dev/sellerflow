import * as React from "react";
export interface EmptyStateProps { kind?: "empty" | "search" | "error" | "forbidden" | "privacy"; icon?: string; title: React.ReactNode; description?: React.ReactNode; actions?: React.ReactNode; code?: string; }
export declare function EmptyState(props: EmptyStateProps): JSX.Element;
