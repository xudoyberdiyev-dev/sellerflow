import * as React from "react";
export interface CommandItem { id?: string; label: string; group?: string; icon?: string; hint?: string; hintMono?: boolean; shortcut?: string[]; onSelect?: () => void; }
export interface CommandPaletteProps { open: boolean; onOpenChange: (o: boolean) => void; items: CommandItem[]; placeholder?: string; bindShortcut?: boolean; contained?: boolean; }
export declare function CommandPalette(props: CommandPaletteProps): JSX.Element | null;
