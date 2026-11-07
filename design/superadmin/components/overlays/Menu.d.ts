import * as React from "react";
export interface TriggerProps { onClick: () => void; "aria-expanded": boolean; "aria-haspopup": string; "aria-controls": string; }
export interface PopoverProps { trigger: (p: TriggerProps) => React.ReactNode; children: React.ReactNode; align?: "start" | "end"; width?: number; defaultOpen?: boolean; label?: string; }
export declare function Popover(props: PopoverProps): JSX.Element;
export interface DropdownItem { label?: React.ReactNode; icon?: string; onSelect?: () => void; danger?: boolean; disabled?: boolean; shortcut?: string[]; keepOpen?: boolean; separator?: boolean; heading?: string; }
export interface DropdownMenuProps { trigger: (p: TriggerProps) => React.ReactNode; items: DropdownItem[]; align?: "start" | "end"; label?: string; defaultOpen?: boolean; width?: number; }
export declare function DropdownMenu(props: DropdownMenuProps): JSX.Element;
