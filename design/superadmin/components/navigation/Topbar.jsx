import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Kbd } from '../display/Tooltip.jsx';
import { Avatar } from '../display/Avatar.jsx';
import { IconButton } from '../buttons/Button.jsx';
import { Breadcrumbs } from './Tabs.jsx';

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || '');

/** App top bar: breadcrumbs, ⌘K search trigger, actions, user. */
export function Topbar({ breadcrumbs, onOpenSearch, user, actions, onMenu, onUserClick }) {
  return (
    <header className="sf-topbar">
      {onMenu && <IconButton icon="menu" label="Menyuni ochish" onClick={onMenu} />}
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
      <div className="sf-topbar__right">
        {onOpenSearch && (
          <button type="button" className="sf-topbar__search" onClick={onOpenSearch} aria-label="Qidirish va buyruqlar" aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}>
            <Icon name="search" size={16} /><span>Qidirish yoki buyruq…</span>
            <span style={{ display: 'flex', gap: 2 }}><Kbd>{isMac ? '⌘' : 'Ctrl'}</Kbd><Kbd>K</Kbd></span>
          </button>
        )}
        {actions}
        {user && (
          <button type="button" className="sf-user" onClick={onUserClick} aria-label={`Profil: ${user.name}`}>
            <Avatar name={user.name} size={28} />
            <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
              <span className="sf-user__name">{user.name}</span>
              <span className="sf-user__role">{user.role}</span>
            </span>
          </button>
        )}
      </div>
    </header>
  );
}
