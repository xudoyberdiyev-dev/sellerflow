Status badges (text + dot, never color-only), neutral Tags, and channel tags with chart-color swatch.
```jsx
<StatusBadge status="Faol" />
<StatusBadge status="Muddati tugagan" />
<ChannelTag channel="uzum" />
<Tag onRemove={clear}>Holat: Faol</Tag>
```
- Mapping: Faol=success, Ro‘yxatdan o‘tmoqda=info, Bloklangan=warning, O‘chirish so‘ralgan=orange, Yopilgan=neutral; Kutilmoqda=info, Ruxsat berilgan=success, Rad etilgan/Bekor qilingan=danger, Muddati tugagan=neutral.
