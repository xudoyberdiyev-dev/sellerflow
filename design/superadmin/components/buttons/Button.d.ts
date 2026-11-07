import * as React from "react";
/** @startingPoint section="Components" subtitle="Buttons — primary, secondary, ghost, danger, icon" viewport="700x420" */
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger" | "privacy"; size?: "sm" | "md";
  icon?: string; iconRight?: string; loading?: boolean; disabled?: boolean; children?: React.ReactNode; }
export declare function Button(props: ButtonProps): JSX.Element;
export interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  icon: string; /** Required — becomes aria-label and title. */ label: string;
  variant?: "primary" | "secondary" | "ghost" | "danger"; size?: "sm" | "md"; loading?: boolean; disabled?: boolean; }
export declare function IconButton(props: IconButtonProps): JSX.Element;
