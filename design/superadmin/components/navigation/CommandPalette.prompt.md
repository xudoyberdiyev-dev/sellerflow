⌘K / Ctrl+K palette for jumping to sellers, pages and actions.
```jsx
<CommandPalette open={o} onOpenChange={setO} items={[{group:'Sahifalar',label:'Sotuvchilar',icon:'users'},{group:'Sotuvchilar',label:'Dilnoza Karimova',hint:'482019377',hintMono:true}]} />
```
- Search results never show commercial data. Dangerous actions open their ConfirmDialog, never run directly.
