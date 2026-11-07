#!/usr/bin/env python3
"""D-7: Superadmin UI vizual tekshiruvi (Playwright, Chromium).

Har sahifani yorug'/qorong'i mavzuda 1440, 768 va 390 px kenglikda ochadi, skrinshot oladi va tekshiradi:
  * konsol / sahifa xatolari yo'q;
  * gorizontal toshib chiqish yo'q (mobil);
  * Tab tartibi: birinchi 12 fokus elementining har biri ko'rinadi va nomga (accessible name) ega.
Xato bo'lsa — chiqish kodi 1.

Ishlatish (lokal backend + `npm run dev` ishlab turganda; token — mavjud admin sessiyasi, repo'ga yozilmaydi):
  SF_TOKEN=... python3 admin-ui/e2e/screens.py --base http://localhost:5173/admin/ --out /tmp/sf-shots
"""
import argparse, asyncio, os, sys
from playwright.async_api import async_playwright

PAGES = ['/', '/status', '/sellers', '/grants', '/support', '/audit', '/admins', '/profile']
SIZES = [(1440, 900), (768, 1024), (390, 844)]

FOCUS_JS = """() => {
  const el = document.activeElement
  if (!el || el === document.body) return null
  const r = el.getBoundingClientRect()
  const lbl = el.labels && el.labels[0] ? el.labels[0].innerText : ''
  const name = (el.getAttribute('aria-label') || lbl || el.innerText || el.getAttribute('title') || el.getAttribute('placeholder') || '').trim()
  return { tag: el.tagName, name: name.slice(0, 40), visible: r.width > 0 && r.height > 0 }
}"""

async def run(a):
    token = os.environ.get('SF_TOKEN')
    if not token:
        sys.exit('SF_TOKEN muhit o‘zgaruvchisi kerak')
    os.makedirs(a.out, exist_ok=True)
    problems = []
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=a.chrome or None, args=['--no-sandbox'])
        for theme in ('light', 'dark'):
            for w, h in SIZES:
                ctx = await browser.new_context(viewport={'width': w, 'height': h})
                await ctx.add_init_script(f"sessionStorage.setItem('sf.admin.token', {token!r}); localStorage.setItem('sf.theme', '{theme}')")
                page = await ctx.new_page()
                errs = []
                page.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
                page.on('pageerror', lambda e: errs.append(str(e)))
                for path in PAGES:
                    errs.clear()
                    await page.goto(a.base + '#' + path)
                    await page.wait_for_timeout(1200)
                    tag = f'{theme} {w}px {path}'
                    await page.screenshot(path=os.path.join(a.out, f"{theme}-{w}{path.replace('/', '_') or '_'}.png"))
                    if errs:
                        problems.append(f'{tag}: konsol xatosi: {errs[:2]}')
                    overflow = await page.evaluate('document.documentElement.scrollWidth - window.innerWidth')
                    if overflow > 1:
                        problems.append(f'{tag}: gorizontal toshish {overflow}px')
                    if w == 1440 and theme == 'light':
                        for i in range(12):
                            await page.keyboard.press('Tab')
                            f = await page.evaluate(FOCUS_JS)
                            if f and (not f['visible'] or not f['name']):
                                problems.append(f'{tag}: Tab #{i + 1} {f}')
                await ctx.close()
        await browser.close()
    for x in problems:
        print('XATO:', x)
    print(f'{len(problems)} ta muammo; skrinshotlar: {a.out}')
    return 1 if problems else 0

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://localhost:5173/admin/')
    ap.add_argument('--out', default='/tmp/sf-shots')
    ap.add_argument('--chrome', default='/usr/bin/google-chrome' if os.path.exists('/usr/bin/google-chrome') else '')
    sys.exit(asyncio.run(run(ap.parse_args())))
