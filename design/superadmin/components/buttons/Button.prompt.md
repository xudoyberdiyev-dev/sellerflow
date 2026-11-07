Action buttons; labels start with a verb («Saqlash», «Bloklash»).
```jsx
<Button variant="primary" icon="plus">Admin qo‘shish</Button>
<Button variant="danger" icon="ban">Bloklash</Button>
<IconButton icon="ellipsis" label="Amallar" />
```
- One primary per view. `danger` only inside a ConfirmDialog footer or for an action that opens one. `privacy` = request seller consent.
- `loading` keeps width and shows a spinner. Sizes: md 36px, sm 32px.
