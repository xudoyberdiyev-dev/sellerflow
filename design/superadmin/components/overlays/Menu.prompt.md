Popover (anchored panel for filters/forms) and DropdownMenu (list of actions).
```jsx
<DropdownMenu align="end" trigger={p=><IconButton icon="ellipsis" label="Amallar" {...p}/>} items={[{label:'Ochish',icon:'eye'},{separator:true},{label:'Bloklash',icon:'ban',danger:true}]} />
```
- Danger items go last, after a separator.
