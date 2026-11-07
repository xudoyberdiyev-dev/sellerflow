import * as React from "react";
export interface TooltipProps { content: React.ReactNode; shortcut?: string; placement?: "top" | "bottom"; defaultOpen?: boolean; children: React.ReactElement; }
export declare function Tooltip(props: TooltipProps): JSX.Element;
export declare function Kbd(props: { children: React.ReactNode }): JSX.Element;
