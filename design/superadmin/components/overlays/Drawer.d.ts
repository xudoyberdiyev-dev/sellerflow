import * as React from "react";
export interface DrawerProps { open: boolean; onClose: () => void; title: React.ReactNode; subtitle?: React.ReactNode; actions?: React.ReactNode; children?: React.ReactNode; footer?: React.ReactNode; width?: number; contained?: boolean; }
export declare function Drawer(props: DrawerProps): JSX.Element | null;
