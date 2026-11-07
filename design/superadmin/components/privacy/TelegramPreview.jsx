import React from 'react';
import { Icon } from '../core/Icon.jsx';

/**
 * Preview of a SellerFlow Telegram bot message: text + inline keyboard.
 * keyboard: [[{text, onClick?}], …] — rows of buttons.
 */
export function TelegramPreview({ botName = 'SellerFlow bot', text, keyboard = [], time = '14:32', caption = 'Telegram xabar ko‘rinishi' }) {
  return (
    <div className="sf-tg" role="figure" aria-label={caption}>
      <div className="sf-tg__head"><Icon name="send" size={12} />{caption}</div>
      <div className="sf-tg__bubble">
        <span className="sf-tg__name">{botName}</span>
        {text}
        <span className="sf-tg__time sf-num">{time}</span>
      </div>
      {keyboard.length > 0 && (
        <div className="sf-tg__kb">
          {keyboard.map((row, i) => (
            <div key={i} className="sf-tg__kb-row">
              {row.map((b, j) => <button key={j} type="button" className="sf-tg__btn" onClick={b.onClick}>{b.text}</button>)}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
