import * as React from "react";
export interface SelectOption { value: string; label: string; disabled?: boolean; }
export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "size"> { options: SelectOption[]; placeholder?: string; size?: "sm" | "md"; invalid?: boolean; icon?: string; }
export declare function Select(props: SelectProps): JSX.Element;
