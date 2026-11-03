# Accessibility pass (Tier 3)

What was checked, what was broken, and what the site now does. The automated half is a script the team
can re-run; the manual half is the list a panel usually probes.

## How to re-run the automated check

```bash
pip install playwright && python3 -m playwright install chromium
npm install axe-core                  # the engine Lighthouse uses for its accessibility category
python3 scripts/a11y-check.py         # 16 pages, WCAG 2.1 A + AA rules
```

It prints every violation with the CSS selector of the element that breaks it and exits non-zero if any
violation is `serious` or `critical`.

**Result after the fixes below: 16 pages checked, 0 violations, 0 serious or critical.**

Pages covered: home, theatres, film page, about, contact, privacy, terms, customer login, customer
register, admin login, profile/wallet, my bookings, admin analytics, admin promo codes, admin bookings,
door check-in.

## What the check found, and the fix

| Page | Rule | Cause | Fix |
|---|---|---|---|
| Film page | `color-contrast` (serious) | The "Trailer — coming soon" card dimmed itself with `opacity: .55`, which pushed the small "Coming soon" label under 4.5:1 | The dimming moved from `opacity` to explicit colours: `css/studio.css` sets the label and heading colours in both themes and dims only the thumbnail |
| Admin bookings | `scrollable-region-focusable` (serious) | The table sits in a horizontally scrollable box, which a keyboard user could not reach | Every `table-wrap` is now `tabindex="0"` with `role="region"` and an `aria-label`, and it takes a visible coral focus ring |

Both fixes are in the shared layer, so all four admin tables and both themes are covered at once.

## What the site does by design

* **Semantic structure** — one `<h1>` per page, `<nav>`, `<main>`, `<footer>`, `<table>` with `<thead>`/`<th>`,
  and real `<button>`/`<a>` elements rather than clickable `<div>`s.
* **Forms** — every input has a `<label for=…>`; the two that cannot (the search boxes with a visible
  placeholder) carry an `aria-label`. Error messages are written into the page and read by screen readers.
* **The screen-reader-only helpers** — `.sr-only` for the ticket-code label on the door page, so the
  scanner input has a name without a visible duplicate.
* **Live regions** — the door check-in verdict is `role="status" aria-live="polite"`, the toasts are in an
  `aria-live` container, and the booking summary updates are announced rather than only coloured.
* **Keyboard** — the seat map is a grid of `<button class="seat">` elements, so seats are reachable with
  Tab and chosen with Enter/Space; the theme and language switchers are `<button>`s with `aria-pressed`;
  the admin table region, the pager and the modal close button all work from the keyboard.
* **Focus visible** — every focusable element has a `:focus-visible` ring (coral on dark, deep coral on
  light). Nothing removes the outline without replacing it.
* **Colour is never the only signal** — a sold-out show says "Sold out" and is disabled, a checked-in
  booking shows a badge with a tick and a time, a rejected scan shows a word (`Already used`) as well as a
  red panel, and the seat bands are named in the legend.
* **Contrast** — body text is `#f6f5fa` on `#08070d` (dark) and `#14131d` on light surfaces; the muted
  grey is `#a2a1b6` / `#63627a`, both above 4.5:1 on their backgrounds. axe checks every text node on all
  16 pages, which is what keeps this honest as the palette changes.
* **Language** — `<html lang>` is set by the language switcher (`en`, `si`, `ta`), so a screen reader
  pronounces Sinhala and Tamil text correctly.
* **Motion** — the hero and card animations are `transform`/`opacity` only, and
  `@media (prefers-reduced-motion: reduce)` turns the looping ones off.
* **Zoom and reflow** — the layout is a single column under 720 px, and the tables scroll inside their own
  box instead of forcing the page sideways.

## Lighthouse

Lighthouse needs a full Chrome and a network download, so it is not part of the automated run here.
The same pages were audited with axe-core (the engine behind Lighthouse's accessibility score), which is
the part of a Lighthouse run this project can control. To reproduce the full report locally:

```bash
npx lighthouse http://localhost:8080/index.html --only-categories=accessibility,performance --view
```

The performance category will always look worse than the real experience because the demo data is
generated: the home page loads twenty film posters from `picsum.photos` over the network. The pages
themselves ship no JavaScript framework, no webfont and no CDN request - one stylesheet pair, one small
`common.js`, and inline SVG everywhere else.
