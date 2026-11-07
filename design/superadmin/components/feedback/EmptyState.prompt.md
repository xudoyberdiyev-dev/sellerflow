Empty, no-results, error and 403 states for any page or panel.
```jsx
<EmptyState kind="search" title="Hech narsa topilmadi" description="Filtrlarni o‘zgartiring yoki tozalang." actions={<Button>Filtrlarni tozalash</Button>} />
<EmptyState kind="forbidden" code="403" title="Bu bo‘limga ruxsat yo‘q" description="Faqat SUPERADMIN ko‘ra oladi." />
```
