import * as React from "react";
export type Tone = "success" | "warning" | "danger" | "info" | "orange" | "neutral" | "privacy" | "brand";
export interface BadgeProps { tone?: Tone; dot?: boolean; icon?: string; children: React.ReactNode; }
export declare function Badge(props: BadgeProps): JSX.Element;
export type AccountStatus = "Faol" | "Ro‘yxatdan o‘tmoqda" | "Bloklangan" | "O‘chirish so‘ralgan" | "Yopilgan";
export type AccessStatus = "Kutilmoqda" | "Ruxsat berilgan" | "Rad etilgan" | "Bekor qilingan" | "Muddati tugagan";
export declare function StatusBadge(props: { status: AccountStatus | AccessStatus }): JSX.Element;
export interface TagProps { children: React.ReactNode; color?: string; icon?: string; onRemove?: () => void; }
export declare function Tag(props: TagProps): JSX.Element;
export declare function ChannelTag(props: { channel: string }): JSX.Element;
export declare const CHANNELS: { id: string; label: string; color: string }[];
export declare const STATUS_TONES: Record<string, Tone>;
