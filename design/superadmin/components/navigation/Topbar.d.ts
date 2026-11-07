import * as React from "react";
export interface TopbarProps { breadcrumbs?: { label: React.ReactNode; href?: string }[]; onOpenSearch?: () => void; user?: { name: string; role: string }; actions?: React.ReactNode; onMenu?: () => void; onUserClick?: () => void; }
export declare function Topbar(props: TopbarProps): JSX.Element;
