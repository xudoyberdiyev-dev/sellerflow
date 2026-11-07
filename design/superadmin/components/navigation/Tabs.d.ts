import * as React from "react";
export interface TabItem { value: string; label: React.ReactNode; count?: number; icon?: string; disabled?: boolean; }
export declare function Tabs(props: { items: TabItem[]; value: string; onChange?: (v: string) => void; label?: string }): JSX.Element;
export interface Crumb { label: React.ReactNode; href?: string; onClick?: (e: any) => void; }
export declare function Breadcrumbs(props: { items: Crumb[] }): JSX.Element;
