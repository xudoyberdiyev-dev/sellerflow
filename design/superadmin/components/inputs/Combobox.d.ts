import * as React from "react";
export interface ComboboxOption { value: string; label: string; hint?: string; icon?: string; }
export interface ComboboxProps { options: ComboboxOption[]; value?: string; onChange?: (v: string) => void; placeholder?: string; emptyText?: string; invalid?: boolean; disabled?: boolean; id?: string; style?: React.CSSProperties; }
export declare function Combobox(props: ComboboxProps): JSX.Element;
