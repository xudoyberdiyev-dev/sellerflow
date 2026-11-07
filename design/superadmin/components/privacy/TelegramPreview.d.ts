import * as React from "react";
export interface TelegramButton { text: string; onClick?: () => void; }
export interface TelegramPreviewProps { botName?: string; text: React.ReactNode; keyboard?: TelegramButton[][]; time?: string; caption?: string; }
export declare function TelegramPreview(props: TelegramPreviewProps): JSX.Element;
