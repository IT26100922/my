/* ===== Shared front-end helpers: API calls, navigation, auth guard, UI utilities ===== */

const API = {
  async call(method, url, body) {
    const opts = { method, headers: {}, credentials: 'same-origin' };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    if (!res.ok) {
      const message = (data && data.message) || `Request failed (${res.status})`;
      const err = new Error(message);
      err.status = res.status;
      throw err;
    }
    return data;
  },
  get(url) { return this.call('GET', url); },
  post(url, body) { return this.call('POST', url, body ?? {}); },
  put(url, body) { return this.call('PUT', url, body); },
  del(url) { return this.call('DELETE', url); }
};

/** Shows a short notification in the corner. type: 'ok' | 'err' */
function toast(message, type = 'ok') {
  let wrap = document.querySelector('.toast-wrap');
  if (!wrap) {
    wrap = document.createElement('div');
    wrap.className = 'toast-wrap';
    document.body.appendChild(wrap);
  }
  const el = document.createElement('div');
  el.className = `toast ${type === 'err' ? 'err' : 'ok'}`;
  el.textContent = message;
  wrap.appendChild(el);
  setTimeout(() => el.remove(), 3800);
}

/* ---------- light / dark theme ---------- */
const THEME_KEY = 'cinemax-theme';

/** Current theme name: 'dark' (default) or 'light'. */
function currentTheme() {
  return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
}

function themeToggleHtml(extraClass = '') {
  return `<button class="btn btn-ghost btn-sm theme-toggle ${extraClass}" id="themeToggle" type="button"></button>`;
}

/** Sets the theme, remembers it in this browser, and updates every toggle button. */
function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  try { localStorage.setItem(THEME_KEY, theme); } catch (e) { /* storage may be blocked */ }
  const next = theme === 'light' ? 'Dark' : 'Light';
  const icon = theme === 'light' ? '🌙' : '☀️';
  document.querySelectorAll('#themeToggle').forEach(btn => {
    btn.innerHTML = `<span class="theme-icon" aria-hidden="true">${icon}</span><span>${next} mode</span>`;
    btn.setAttribute('aria-label', `Switch to ${next.toLowerCase()} mode`);
    btn.setAttribute('title', `Switch to ${next.toLowerCase()} mode`);
  });
}

/** Wires up the theme button(s) on the page. Safe to call more than once. */
function bindThemeToggle() {
  applyTheme(currentTheme());
  document.querySelectorAll('#themeToggle').forEach(btn => {
    if (btn.dataset.bound) return;
    btn.dataset.bound = '1';
    btn.addEventListener('click', () => applyTheme(currentTheme() === 'light' ? 'dark' : 'light'));
  });
}

document.addEventListener('DOMContentLoaded', () => {
  // Pages without a top bar (e.g. admin login) get a small floating toggle.
  if (!document.getElementById('topbar') && !document.getElementById('themeToggle')) {
    document.body.insertAdjacentHTML('beforeend', themeToggleHtml('theme-fab'));
  }
  bindThemeToggle();
});

/** Escapes text before putting it into innerHTML. */
/* Short id lookup used by most page scripts. It is a window property, not a const, so pages that
   declare their own `const $` keep working. */
window.$ = window.$ || (id => document.getElementById(id));

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function money(value) {
  const n = Number(value || 0);
  return 'LKR ' + n.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDateTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fmtTime(iso) {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function dayKey(iso) {
  const d = new Date(iso);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function formatBadge(format) {
  const map = { STANDARD: ['badge-std', '2D'], IMAX: ['badge-imax', 'IMAX'], THREE_D: ['badge-3d', '3D'] };
  const [cls, label] = map[format] || ['badge-muted', format];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

function statusBadge(status) {
  const map = {
    PENDING_PAYMENT: ['badge-warn', 'Awaiting payment'],
    CONFIRMED: ['badge-ok', 'Confirmed'],
    CANCELLED: ['badge-bad', 'Cancelled'],
    SUCCESS: ['badge-ok', 'Paid'],
    REFUNDED: ['badge-muted', 'Refunded'],
    PARTIALLY_REFUNDED: ['badge-warn', 'Partly refunded']
  };
  const [cls, label] = map[status] || ['badge-muted', status];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

/** Query-string value of the current page, e.g. qs('showId'). */
function qs(name) {
  return new URLSearchParams(window.location.search).get(name);
}

/** Returns the logged-in user, or null. */
async function currentUser() {
  try { return await API.get('/api/auth/me'); } catch (e) { return null; }
}

/** Redirects to the right login page when the user does not have the required role. */
async function requireRole(role, loginPage) {
  const me = await currentUser();
  if (!me || me.role !== role) {
    const next = encodeURIComponent(window.location.pathname.replace(/^\//, '') + window.location.search);
    window.location.href = `${loginPage}?next=${next}`;
    return null;
  }
  return me;
}

/** Builds the top navigation bar into <header id="topbar"></header>. */
async function renderNav(active) {
  const header = document.getElementById('topbar');
  if (!header) return;
  const me = await currentUser();
  /* Labels go through the small translator when it is loaded (js/i18n.js). */
  const tr = (key, fallback) => (window.I18N ? I18N.t(key, fallback) : fallback);
  const links = [
    ['index.html', tr('nav.movies', 'Movies'), 'movies'],
    ['theatres.html', tr('nav.theatres', 'Theatres'), 'theatres']
  ];
  let right;
  if (me && me.role === 'CUSTOMER') {
    links.push(['my-bookings.html', tr('nav.bookings', 'My bookings'), 'bookings'],
               ['profile.html', tr('nav.profile', 'Profile'), 'profile']);
    right = `<div class="bell-wrap">
               <button class="bell-btn" id="bellBtn" type="button" aria-label="Notifications" aria-expanded="false">🔔<span class="bell-count hidden" id="bellCount">0</span></button>
               <div class="notif-panel hidden" id="notifPanel" role="dialog" aria-label="Notifications"></div>
             </div>
             <span class="pill">Hi, ${esc(me.fullName.split(' ')[0])} &middot; ${me.loyaltyPoints ?? 0} pts</span>
             <button class="btn btn-ghost btn-sm" id="logoutBtn">${tr('nav.logout', 'Log out')}</button>`;
  } else if (me && me.role === 'ADMIN') {
    links.push(['admin-dashboard.html', tr('nav.admin', 'Admin Dashboard'), 'admin']);
    right = `<span class="pill">Staff &middot; ${esc(me.fullName)}</span>
             <button class="btn btn-ghost btn-sm" id="logoutBtn">${tr('nav.logout', 'Log out')}</button>`;
  } else {
    right = `<a class="btn btn-ghost btn-sm" href="customer-login.html">${tr('nav.login', 'Log in')}</a>
             <a class="btn btn-primary btn-sm" href="customer-register.html">${tr('nav.signup', 'Sign up')}</a>`;
  }
  header.innerHTML = `
    <div class="topbar-inner">
      <a class="brand" href="index.html"><img class="logo-img" src="/img/logo.svg" alt=""><span>CineMax</span></a>
      <nav class="nav-links">
        ${links.map(([href, label, key]) =>
          `<a href="${href}" data-nav="${key}" class="${key === active ? 'active' : ''}">${label}</a>`).join('')}
      </nav>
      <div class="nav-right">${right}</div>
    </div>`;
  header.querySelector('.nav-right').insertAdjacentHTML('afterbegin', themeToggleHtml());
  bindThemeToggle();
  if (window.I18N) {
    header.querySelector('.nav-right').insertAdjacentHTML('afterbegin', I18N.switcherHtml());
    I18N.bindSwitcher(header);
  }
  if (me && me.role === 'CUSTOMER') initNotifications();
  const logout = document.getElementById('logoutBtn');
  if (logout) {
    logout.addEventListener('click', async () => {
      try { await API.post('/api/auth/logout', {}); } catch (e) { /* ignore */ }
      window.location.href = 'index.html';
    });
  }
}

/** Safe redirect target from ?next= (only same-site relative pages are allowed). */
function nextPage(fallback) {
  const next = qs('next');
  if (next && /^[a-z0-9-]+\.html(\?[^\s]*)?$/i.test(next)) return next;
  return fallback;
}

/* ---------- customer notifications (bell menu) ---------- */
const NOTIF_ICONS = { BOOKING_CONFIRMED: '✅', REMINDER_24H: '⏰', REMINDER_2H: '⏰', BOOKING_CANCELLED: '✖', HOLD_EXPIRED: '⌛', BOOKING_MOVED: '📅' };

function initNotifications() {
  const bell = document.getElementById('bellBtn');
  const panel = document.getElementById('notifPanel');
  if (!bell || !panel) return;
  bell.addEventListener('click', e => {
    e.stopPropagation();
    const open = panel.classList.toggle('hidden') === false;
    bell.setAttribute('aria-expanded', String(open));
    if (open) refreshNotifications();
  });
  document.addEventListener('click', e => {
    if (!panel.contains(e.target) && e.target !== bell) {
      panel.classList.add('hidden');
      bell.setAttribute('aria-expanded', 'false');
    }
  });
  panel.addEventListener('click', async e => {
    const readAll = e.target.closest('[data-read-all]');
    if (readAll) {
      try { await API.post('/api/customer/notifications/read-all', {}); } catch (err) { toast(err.message, 'err'); }
      return refreshNotifications();
    }
    const item = e.target.closest('[data-notif-id]');
    if (item) {
      try { await API.post('/api/customer/notifications/' + item.dataset.notifId + '/read', {}); } catch (err) { /* ignore */ }
      if (item.dataset.bookingId) { window.location.href = 'my-bookings.html'; return; }
      refreshNotifications();
    }
  });
  refreshNotifications();
  setInterval(refreshNotifications, 60000);
}

async function refreshNotifications() {
  const panel = document.getElementById('notifPanel');
  const count = document.getElementById('bellCount');
  if (!panel || !count) return;
  let inbox;
  try { inbox = await API.get('/api/customer/notifications'); } catch (err) { return; }
  count.textContent = inbox.unreadCount > 9 ? '9+' : String(inbox.unreadCount);
  count.classList.toggle('hidden', inbox.unreadCount === 0);
  panel.innerHTML = `
    <div class="notif-head"><strong>Notifications</strong>
      ${inbox.unreadCount ? '<button class="btn btn-ghost btn-sm" type="button" data-read-all>Mark all read</button>' : ''}</div>
    ${inbox.items.length ? inbox.items.map(n => `
      <button type="button" class="notif-item ${n.read ? '' : 'unread'}" data-notif-id="${n.id}"
        ${n.bookingId ? `data-booking-id="${n.bookingId}"` : ''}>
        <span class="notif-icon">${NOTIF_ICONS[n.type] || '•'}</span>
        <span class="notif-body"><span class="notif-msg">${esc(n.message)}</span>
          <small class="muted">${fmtDateTime(n.createdAt)}</small></span>
      </button>`).join('') : '<p class="notif-empty">No notifications yet.</p>'}`;
}

/* ---------- shared site footer (customer pages only; the admin shell has its own layout) ---------- */
function renderFooter() {
  if (!document.getElementById('topbar') || document.querySelector('.site-footer')) return;
  const tr = (key, fallback) => (window.I18N ? I18N.t(key, fallback) : fallback);
  document.body.insertAdjacentHTML('beforeend', `
    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <a class="brand" href="index.html"><img class="logo-img" src="/img/logo.svg" alt=""><span>CineMax</span></a>
          <p>${tr('footer.tagline', 'Movie tickets for Negombo and Colombo. Pick your seats, pay securely and show your ticket at the door.')}</p>
        </div>
        <nav aria-label="Explore">
          <h4>${tr('footer.explore', 'Explore')}</h4>
          <a href="index.html">Now showing</a>
          <a href="theatres.html">Theatres and screens</a>
          <a href="about.html">About us</a>
        </nav>
        <nav aria-label="Account">
          <h4>${tr('footer.account', 'Account')}</h4>
          <a href="customer-login.html">Log in</a>
          <a href="customer-register.html">Create an account</a>
          <a href="my-bookings.html">My bookings</a>
        </nav>
        <nav aria-label="Help">
          <h4>${tr('footer.help', 'Help')}</h4>
          <a href="contact.html">Contact us</a>
          <a href="terms.html#refunds">Refund policy</a>
          <a href="terms.html">Terms of use</a>
          <a href="privacy.html">Privacy</a>
        </nav>
      </div>
      <div class="footer-bottom">
        <span>© 2026 CineMax Studios (Pvt) Ltd. All rights reserved.</span>
        <span>Negombo · Colombo · Western Province, Sri Lanka</span>
      </div>
    </footer>`);
}
document.addEventListener('DOMContentLoaded', renderFooter);

/* ---------- styled dropdowns ----------
   A native <select> popup is painted by the operating system, so it stays a white
   sheet with a blue highlight whatever the page looks like. enhanceSelect() keeps
   the <select> in the page (hidden, still the single source of truth for value,
   options and the change event) and draws a themed listbox next to it instead.
   Pages keep reading `select.value` and keep listening for `change`; nothing else
   in the app has to know this component exists. */
const CM_CHEV_ICON = '<svg class="cm-select__chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
  + 'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
const CM_TICK_ICON = '<svg class="tick" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
  + 'stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m4 12.5 5 5L20 6.5"/></svg>';

let cmOpenWidget = null;
const cmWidgets = [];

/** Replaces the visible part of one <select> with a themed button + listbox. */
function enhanceSelect(select) {
  if (!select || select.dataset.cmReady === 'yes') return;
  select.dataset.cmReady = 'yes';
  select.classList.add('cm-select__native');
  select.tabIndex = -1;
  select.setAttribute('aria-hidden', 'true');

  const wrap = document.createElement('div');
  wrap.className = 'cm-select';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'cm-select__btn';
  btn.setAttribute('role', 'combobox');
  btn.setAttribute('aria-haspopup', 'listbox');
  btn.setAttribute('aria-expanded', 'false');
  const menu = document.createElement('ul');
  menu.className = 'cm-select__menu';
  menu.id = 'cmlist-' + (select.id || Math.random().toString(36).slice(2, 8));
  menu.setAttribute('role', 'listbox');
  menu.hidden = true;
  btn.setAttribute('aria-controls', menu.id);
  btn.innerHTML = '<span class="cm-select__value"></span>' + CM_CHEV_ICON;

  const name = select.getAttribute('aria-label') || select.id || 'Filter';
  btn.setAttribute('aria-label', name);
  menu.setAttribute('aria-label', name);
  if (select.id) select.closest('.field')?.querySelector('label')?.setAttribute('for', select.id);

  select.parentNode.insertBefore(wrap, select);
  wrap.appendChild(select);
  wrap.appendChild(btn);
  wrap.appendChild(menu);

  const valueEl = btn.querySelector('.cm-select__value');
  const bar = select.closest('.searchbar');
  let active = -1;

  function options() { return Array.from(menu.children); }

  /** Rebuilds the rows from the <select> options (they are loaded from the API). */
  function render() {
    menu.innerHTML = Array.from(select.options).map((opt, i) =>
      `<li class="cm-select__opt" role="option" id="${menu.id}-${i}" data-index="${i}"`
      + ` aria-selected="${i === select.selectedIndex}">${esc(opt.textContent)}${CM_TICK_ICON}</li>`
    ).join('');
  }

  /** Copies the selected option into the button and flags an active filter. */
  function sync() {
    const opt = select.options[select.selectedIndex];
    valueEl.textContent = opt ? opt.textContent : '';
    wrap.classList.toggle('is-set', !!opt && select.selectedIndex > 0);
    options().forEach((li, i) => li.setAttribute('aria-selected', String(i === select.selectedIndex)));
    if (!menu.hidden) markActive(select.selectedIndex);
  }

  function markActive(index) {
    const rows = options();
    if (!rows.length) return;
    active = Math.max(0, Math.min(index, rows.length - 1));
    rows.forEach((li, i) => li.classList.toggle('is-active', i === active));
    btn.setAttribute('aria-activedescendant', rows[active].id);
    rows[active].scrollIntoView({ block: 'nearest' });
  }

  function open() {
    if (cmOpenWidget && cmOpenWidget !== widget) cmOpenWidget.close();
    cmOpenWidget = widget;
    menu.hidden = false;
    btn.setAttribute('aria-expanded', 'true');
    if (bar) bar.classList.add('is-open');
    markActive(select.selectedIndex);
  }

  function close(refocus) {
    if (menu.hidden) return;
    menu.hidden = true;
    btn.setAttribute('aria-expanded', 'false');
    btn.removeAttribute('aria-activedescendant');
    if (bar) bar.classList.remove('is-open');
    if (cmOpenWidget === widget) cmOpenWidget = null;
    if (refocus) btn.focus();
  }

  function pick(index) {
    const opt = select.options[index];
    if (!opt) return;
    const changed = select.selectedIndex !== index;
    select.selectedIndex = index;
    sync();
    close(true);
    if (changed) select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  const widget = {
    select, wrap, open, close, sync,
    refresh() { render(); sync(); }
  };
  cmWidgets.push(widget);

  // page code sets `select.value = …` directly (URL filters, Clear button),
  // which fires no event: watch the setter so the button label follows
  const valueDescriptor = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value');
  try {
    Object.defineProperty(select, 'value', {
      configurable: true,
      enumerable: true,
      get() { return valueDescriptor.get.call(select); },
      set(next) { valueDescriptor.set.call(select, next); sync(); }
    });
  } catch (err) { /* the change listener below still keeps it in step */ }

  // the genre/format/language options arrive from /api/movies/filters after load
  new MutationObserver(() => { render(); sync(); close(); })
    .observe(select, { childList: true });

  select.addEventListener('change', sync);

  btn.addEventListener('click', e => {
    e.preventDefault();
    if (menu.hidden) open(); else close();
  });

  menu.addEventListener('click', e => {
    const row = e.target.closest('.cm-select__opt');
    if (row) pick(Number(row.dataset.index));
  });

  btn.addEventListener('keydown', e => {
    // the hero moves with the arrow keys; keep them inside the open list
    if (menu.hidden && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.stopPropagation(); return; }

    if (e.key === 'Escape') { if (!menu.hidden) { e.preventDefault(); close(true); } return; }
    if (e.key === 'Tab') { close(); return; }

    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      e.stopPropagation();
      if (menu.hidden) open();
      else markActive(active + (e.key === 'ArrowDown' ? 1 : -1));
      return;
    }
    if (e.key === 'Home' || e.key === 'End') {
      if (menu.hidden) return;
      e.preventDefault();
      markActive(e.key === 'Home' ? 0 : options().length - 1);
      return;
    }
    if (e.key === 'Enter' || e.key === ' ') {
      if (menu.hidden) return;      // let the button's own click open it
      e.preventDefault();
      pick(active);
      return;
    }
    // first-letter jump, the way a native select does it
    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const letter = e.key.toLowerCase();
      const rows = options();
      const from = menu.hidden ? select.selectedIndex : active;
      const found = rows.findIndex((row, i) =>
        i > from && row.textContent.trim().toLowerCase().startsWith(letter));
      const wrapped = found >= 0 ? found
        : rows.findIndex(row => row.textContent.trim().toLowerCase().startsWith(letter));
      if (wrapped >= 0) {
        if (menu.hidden) { open(); }
        markActive(wrapped);
      }
    }
  });

  // the menu is positioned inside the wrapper, so it travels with the page when it
  // scrolls; only a click somewhere else (or Escape) should dismiss it
  document.addEventListener('pointerdown', e => {
    if (!wrap.contains(e.target)) close();
  }, true);

  render();
  sync();
}

/** Upgrades every filter dropdown on the page (call again after adding markup). */
function enhanceFilterSelects(root) {
  (root || document).querySelectorAll('.searchbar select, select[data-cm-select]')
    .forEach(enhanceSelect);
}

/** Re-reads the options of every upgraded dropdown. */
function refreshFilterSelects() {
  cmWidgets.forEach(widget => widget.refresh());
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => enhanceFilterSelects());
} else {
  enhanceFilterSelects();
}
