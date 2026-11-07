import * as React from "react";
/** @startingPoint section="Layout" subtitle="Grouped collapsible sidebar" viewport="700x520" */
export interface NavItem { id: string; label: string; icon: string; count?: number; roles?: ("SUPERADMIN" | "SUPPORT")[]; }
export interface SidebarProps { groups: { label: string; items: NavItem[] }[]; active?: string; onNavigate?: (id: string) => void; collapsed?: boolean; onToggleCollapsed?: () => void; footer?: React.ReactNode; role?: "SUPERADMIN" | "SUPPORT"; }
export declare function Sidebar(props: SidebarProps): JSX.Element;
