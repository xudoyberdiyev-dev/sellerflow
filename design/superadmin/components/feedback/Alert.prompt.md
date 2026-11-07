InlineAlert for section-level messages; Banner for page-top system notices (full width).
```jsx
<InlineAlert tone="warning" title="2FA yoqilmagan">Hisobingizni himoyalash uchun 2FA’ni yoqing.</InlineAlert>
<Banner tone="info" action={<Button size="sm">Batafsil</Button>}>Bugun 23:00–23:30 texnik ishlar.</Banner>
```
- For the consent session use ReadOnlyBanner, not Banner.
