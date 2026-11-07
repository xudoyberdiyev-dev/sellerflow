Result notice after an action, bottom-right, auto-dismiss 5s.
```jsx
const { toasts, push, dismiss } = useToasts();
push({ tone: 'success', title: 'Sotuvchi bloklandi', description: 'Dilnoza Karimova', audit: true });
<ToastStack toasts={toasts} onDismiss={dismiss} />
```
- Titles in past tense («Saqlandi», «Bloklandi»). Set `audit` for every logged action.
