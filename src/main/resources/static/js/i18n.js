/* ===== Three-language interface: English, Sinhala, Tamil =====
   A local cinema sells to people who read all three, so the shared chrome (top bar, footer,
   home page) can be switched without reloading the page. A missing translation falls back to
   English instead of showing a blank.

   Use:   <h1 data-i18n="home.title">Now showing in Negombo & Colombo</h1>
          <input data-i18n-placeholder="finder.search">
   Two tiers work together:
     1. data-i18n keys for the small fixed set of chrome strings (nav, footer, home headings), and
     2. a phrase dictionary (lang/phrases-si.json, lang/phrases-ta.json) that maps a whole English
        sentence or label to its real meaning in Sinhala or Tamil. Every text node and the placeholder /
        title / aria-label attributes are looked up in it, so text that JavaScript writes AFTER the page
        has loaded (admin tables, the seat map, the door scanner, inline toasts) is translated too - a
        MutationObserver re-runs the pass whenever the page changes.
   Both are keyed by meaning, never by transliteration: "Negombo" becomes මීගමුව / நீர்கொழும்பு, and
   "Seat price" becomes "ආසනයක මිල" / "ஒரு இருக்கையின் விலை", not a letter-by-letter copy of the English.
   The language is remembered in localStorage under "cinemax-lang".

   The strings below are the copy that ships with the page, so the interface works even when the
   browser cannot fetch anything. The same keys are kept in lang/en.json, lang/si.json and
   lang/ta.json for translators; if one of those files is reachable it is loaded on top, so
   editing the JSON is enough to change a wording (see scripts/export-lang.py).

   Declared with var on purpose: a top-level const lives in the script scope and common.js would
   not see it as window.I18N. */

var I18N = (() => {
  const DICT = {
    /* English is kept here too, so lang/en.json can be generated as the reference catalogue. */
    en: {
      'nav.movies': 'Movies',
      'nav.theatres': 'Theatres',
      'nav.bookings': 'My bookings',
      'nav.profile': 'Profile',
      'nav.admin': 'Admin Dashboard',
      'nav.login': 'Log in',
      'nav.signup': 'Sign up',
      'nav.logout': 'Log out',
      'nav.light': 'Light mode',
      'nav.dark': 'Dark mode',
      'lang.label': 'Language',

      'home.title': 'Now showing in Negombo & Colombo',
      'home.lead': 'Find a film, pick a showtime and choose your seats. Results update as you type.',
      'finder.search': 'Search movies by title…',
      'finder.genre': 'All genres',
      'finder.format': 'All formats',
      'finder.language': 'Any language',
      'finder.sort': 'Title A–Z',
      'finder.submit': 'Search',
      'finder.clear': 'Clear',
      'section.nowShowing': 'In the cinema now',
      'section.movies': 'Movies',
      'section.why': 'Why book here',
      'cta.browse': 'Browse movies',
      'cta.screens': 'Theatres & screens',
      'footer.explore': 'Explore',
      'footer.account': 'Account',
      'footer.help': 'Help',
      'footer.tagline': 'Movie tickets for Negombo and Colombo. Pick your seats, pay securely and show your ticket at the door.'
    },
    si: {
      'nav.movies': 'චිත්‍රපට',
      'nav.theatres': 'සිනමා ශාලා',
      'nav.bookings': 'මගේ වෙන්කිරීම්',
      'nav.profile': 'මගේ ගිණුම',
      'nav.admin': 'පරිපාලන පැනලය',
      'nav.login': 'පිවිසෙන්න',
      'nav.signup': 'ලියාපදිංචි වන්න',
      'nav.logout': 'ඉවත් වන්න',
      'nav.light': 'දීප්ත තේමාව',
      'nav.dark': 'අඳුරු තේමාව',
      'lang.label': 'භාෂාව',

      'home.title': 'මීගමුව සහ කොළඹ දැන් තිරගත වේ',
      'home.lead': 'චිත්‍රපටයක් සොයා, ප්‍රදර්ශන වේලාවක් තෝරා, ඔබේ ආසන වෙන් කරගන්න. ලියන විට ප්‍රතිඵල යාවත්කාලීන වේ.',
      'finder.search': 'චිත්‍රපට නමින් සොයන්න…',
      'finder.genre': 'සියලු ප්‍රභේද',
      'finder.format': 'සියලු ආකෘති',
      'finder.language': 'සියලු භාෂා',
      'finder.sort': 'නම අනුව A–Z',
      'finder.submit': 'සොයන්න',
      'finder.clear': 'ඉවත් කරන්න',
      'section.nowShowing': 'දැන් තිරගත වේ',
      'section.movies': 'චිත්‍රපට',
      'section.why': 'මෙහි වෙන් කරන්නේ ඇයි',
      'cta.browse': 'චිත්‍රපට බලන්න',
      'cta.screens': 'සිනමා ශාලා බලන්න',
      'footer.explore': 'ගවේෂණය කරන්න',
      'footer.account': 'ගිණුම',
      'footer.help': 'උදව්',
      'footer.tagline': 'නෙගොම්බෝ සහ කොළඹ සඳහා චිත්‍රපට ටිකට්පත්. ආසන තෝරා, ආරක්ෂිතව ගෙවා, දොරේදී ටිකට්පත පෙන්වන්න.'
    },
    ta: {
      'nav.movies': 'திரைப்படங்கள்',
      'nav.theatres': 'திரையரங்குகள்',
      'nav.bookings': 'எனது முன்பதிவுகள்',
      'nav.profile': 'எனது கணக்கு',
      'nav.admin': 'நிர்வாகப் பலகை',
      'nav.login': 'உள்நுழைக',
      'nav.signup': 'பதிவு செய்க',
      'nav.logout': 'வெளியேறு',
      'nav.light': 'ஒளி தோற்றம்',
      'nav.dark': 'இருண்ட தோற்றம்',
      'lang.label': 'மொழி',

      'home.title': 'நீர்கொழும்பு மற்றும் கொழும்பில் இப்போது திரையிடப்படுகிறது',
      'home.lead': 'திரைப்படத்தைத் தேர்ந்தெடுத்து, நேரத்தைப் பார்த்து, இருக்கைகளை முன்பதிவு செய்யுங்கள். தட்டச்சு செய்யும்போதே முடிவுகள் புதுப்பிக்கப்படும்.',
      'finder.search': 'திரைப்படப் பெயரால் தேடுங்கள்…',
      'finder.genre': 'அனைத்து வகைகள்',
      'finder.format': 'அனைத்து வடிவங்கள்',
      'finder.language': 'அனைத்து மொழிகள்',
      'finder.sort': 'பெயர் A–Z',
      'finder.submit': 'தேடு',
      'finder.clear': 'அழி',
      'section.nowShowing': 'இப்போது திரையில்',
      'section.movies': 'திரைப்படங்கள்',
      'section.why': 'ஏன் இங்கே முன்பதிவு செய்ய வேண்டும்',
      'cta.browse': 'திரைப்படங்களைப் பார்',
      'cta.screens': 'திரையரங்குகளைப் பார்',
      'footer.explore': 'ஆராயுங்கள்',
      'footer.account': 'கணக்கு',
      'footer.help': 'உதவி',
      'footer.tagline': 'நெகொம்போ மற்றும் கொழும்புக்கான திரைப்பட டிக்கெட்டுகள். இருக்கையைத் தேர்ந்தெடுத்து, பாதுகாப்பாக பணம் செலுத்தி, வாசலில் டிக்கட்டைக் காட்டுங்கள்.'
    }
  };

  const LANGS = [
    { code: 'en', label: 'English', short: 'EN' },
    { code: 'si', label: 'සිංහල', short: 'සි' },
    { code: 'ta', label: 'தமிழ்', short: 'த' }
  ];
  const KEY = 'cinemax-lang';

  /* Which JSON files have already been asked for, so a switch does not refetch on every page. */
  const loaded = {};

  /** Loads lang/<code>.json when the page is served over http(s) and merges it over the built-in copy. */
  function loadFile(code) {
    if (loaded[code] || !/^https?:$/.test(window.location.protocol)) return;
    loaded[code] = true;
    fetch('lang/' + code + '.json', { cache: 'no-cache' })
      .then(r => (r.ok ? r.json() : null))
      .then(data => {
        if (!data) return;
        Object.assign(DICT[code] || (DICT[code] = {}), data);
        if (current() === code) apply();
      })
      .catch(() => { /* offline or file:// - the built-in copy is used */ });
  }

  function current() {
    try {
      const stored = localStorage.getItem(KEY);
      if (stored && DICT[stored]) return stored;
    } catch (e) { /* storage may be blocked */ }
    return 'en';
  }

  /** Translation of a key, or the fallback (usually the English text already in the page). */
  function t(key, fallback) {
    const lang = current();
    return (DICT[lang] && DICT[lang][key]) || fallback || (DICT.en && DICT.en[key]) || key;
  }

  /** Rewrites every [data-i18n] and [data-i18n-placeholder] element in the document. */
  function apply(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-i18n]').forEach(el => {
      if (!el.dataset.i18nFallback) el.dataset.i18nFallback = el.textContent.trim();
      el.textContent = t(el.dataset.i18n, el.dataset.i18nFallback);
    });
    scope.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      if (!el.dataset.i18nPlaceholderFallback) el.dataset.i18nPlaceholderFallback = el.placeholder;
      el.placeholder = t(el.dataset.i18nPlaceholder, el.dataset.i18nPlaceholderFallback);
    });
    if (PHRASES[current()]) translatePage(document.body);
    document.documentElement.lang = current();
  }

  /** Switches language, remembers it, and re-renders the chrome and the translated parts of the page. */
  function setLang(code) {
    try { localStorage.setItem(KEY, code); } catch (e) { /* ignore */ }
    loadFile(code);
    loadPhrases(code).then(() => {
      apply();
      if (typeof renderNav === 'function') {
        const active = document.querySelector('.nav-links a.active');
        renderNav(active ? (active.dataset.nav || 'movies') : 'movies');
        translatePage(document.body);
      }
    });
    apply();
    document.dispatchEvent(new CustomEvent('cinemax:lang', { detail: { lang: code } }));
    if (typeof renderNav === 'function') {
      const active = document.querySelector('.nav-links a.active');
      renderNav(active ? (active.dataset.nav || 'movies') : 'movies');
    }
  }

  /* ---------------------------------------------------------------- phrase engine
     A text node cannot be looked up by key - nobody wants to hand-annotate 400 sentences - so the
     dictionary is keyed by the English sentence itself. The original English of every node we touch is
     remembered in ORIGINALS, which is what makes switching back to English lossless. */

  const PHRASES = { en: {} };          // code -> { english: translation }
  const ORIGINALS = new WeakMap();     // text node -> the English it had
  const ATTR_ORIGINALS = new WeakMap();// element -> { attr: english }
  const SKIP = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'TEXTAREA', 'SVG']);
  const ATTRS = ['placeholder', 'title', 'aria-label', 'alt'];
  let phrases = {};
  let applying = false;

  /* ---------------------------------------------------------------- pattern rules
     A dictionary cannot hold every seat count. These rules cover the wording that carries numbers,
     dates and the names that come out of MySQL, and they re-translate the pieces they capture, so
     "130 min · Tamil" keeps the Tamil word already in the dictionary. */
  const DAYS = {
    si: { Sun: 'ඉරිදා', Mon: 'සඳුදා', Tue: 'අඟහරුවාදා', Wed: 'බදාදා', Thu: 'බ්‍රහස්පතින්දා', Fri: 'සිකුරාදා', Sat: 'සෙනසුරාදා' },
    ta: { Sun: 'ஞாயிறு', Mon: 'திங்கள்', Tue: 'செவ்வாய்', Wed: 'புதன்', Thu: 'வியாழன்', Fri: 'வெள்ளி', Sat: 'சனி' }
  };
  const MONTHS = {
    si: { Jan: 'ජන', Feb: 'පෙබ', Mar: 'මාර්', Apr: 'අප්‍රේ', May: 'මැයි', Jun: 'ජූනි', Jul: 'ජූලි',
          Aug: 'අගෝ', Sep: 'සැප්', Oct: 'ඔක්', Nov: 'නොවැ', Dec: 'දෙසැ' },
    ta: { Jan: 'ஜன', Feb: 'பிப்', Mar: 'மார்', Apr: 'ஏப்', May: 'மே', Jun: 'ஜூன்', Jul: 'ஜூலை',
          Aug: 'ஆக', Sep: 'செப்', Oct: 'அக்', Nov: 'நவ', Dec: 'டிச' }
  };
  const RULES = {
    si: [
      [/^(\d+) of (\d+) seats? left$/, (m, tr) => `ආසන ${m[2]}න් ${m[1]}ක් ඉතිරියි`],
      [/^(\d+) movies? found$/, (m) => `චිත්‍රපට ${m[1]}ක් හමු විය`],
      [/^(\d+) min$/, (m) => `${m[1]} මිනිත්තු`],
      [/^(\d+) min · (.+)$/, (m, tr) => `${m[1]} මිනිත්තු · ${tr(m[2])}`],
      [/^(.+) · (\d+) seats · ×([\d.]+)$/, (m, tr) => `${tr(m[1])} · ආසන ${m[2]} · ×${m[3]}`],
      [/^LKR ([\d,.]+) \/ seat · (.+)$/, (m, tr) => `LKR ${m[1]} / ආසනයකට · ${tr(m[2])}`],
      [/^Released (.+)$/, (m, tr) => `නිකුතුව ${tr(m[1])}`],
      [/^CineMax Negombo · (.+)$/, (m, tr) => `CineMax මීගමුව · ${tr(m[1])}`],
      [/^CineMax Colombo · (.+)$/, (m, tr) => `CineMax කොළඹ · ${tr(m[1])}`],
      [/^([A-Z][a-z]{2}) (\d{1,2}) ([A-Z][a-z]{2})$/, (m) =>
        `${DAYS.si[m[1]] || m[1]} ${m[2]} ${MONTHS.si[m[3]] || m[3]}`],
      [/^([A-Z][a-z]{2}) (\d{1,2}) ([A-Z][a-z]{2}), (\d{2}:\d{2})$/, (m) =>
        `${DAYS.si[m[1]] || m[1]} ${m[2]} ${MONTHS.si[m[3]] || m[3]}, ${m[4]}`],
      [/^(\d+) seats?$/, (m) => `ආසන ${m[1]}`]
    ],
    ta: [
      [/^(\d+) of (\d+) seats? left$/, (m) => `${m[2]} இருக்கைகளில் ${m[1]} மீதம்`],
      [/^(\d+) movies? found$/, (m) => `${m[1]} திரைப்படங்கள் கிடைத்தன`],
      [/^(\d+) min$/, (m) => `${m[1]} நிமிடம்`],
      [/^(\d+) min · (.+)$/, (m, tr) => `${m[1]} நிமிடம் · ${tr(m[2])}`],
      [/^(.+) · (\d+) seats · ×([\d.]+)$/, (m, tr) => `${tr(m[1])} · ${m[2]} இருக்கைகள் · ×${m[3]}`],
      [/^LKR ([\d,.]+) \/ seat · (.+)$/, (m, tr) => `LKR ${m[1]} / ஒரு இருக்கைக்கு · ${tr(m[2])}`],
      [/^Released (.+)$/, (m, tr) => `வெளியீடு ${tr(m[1])}`],
      [/^CineMax Negombo · (.+)$/, (m, tr) => `CineMax நீர்கொழும்பு · ${tr(m[1])}`],
      [/^CineMax Colombo · (.+)$/, (m, tr) => `CineMax கொழும்பு · ${tr(m[1])}`],
      [/^([A-Z][a-z]{2}) (\d{1,2}) ([A-Z][a-z]{2})$/, (m) =>
        `${DAYS.ta[m[1]] || m[1]} ${m[2]} ${MONTHS.ta[m[3]] || m[3]}`],
      [/^([A-Z][a-z]{2}) (\d{1,2}) ([A-Z][a-z]{2}), (\d{2}:\d{2})$/, (m) =>
        `${DAYS.ta[m[1]] || m[1]} ${m[2]} ${MONTHS.ta[m[3]] || m[3]}, ${m[4]}`],
      [/^(\d+) seats?$/, (m) => `${m[1]} இருக்கைகள்`]
    ]
  };

  /** One English string in the current language: dictionary first, then the pattern rules. */
  function phraseFor(english) {
    const direct = phrases[english];
    if (direct) return direct;
    const rules = RULES[current()];
    if (!rules) return null;
    for (const [re, make] of rules) {
      const m = english.match(re);
      if (m) return make(m, inner => phraseFor(inner) || inner);
    }
    return null;
  }

  /** Collapses runs of whitespace, the way the harvest script keys the dictionary. */
  function normalise(text) {
    return text.replace(/\s+/g, ' ').trim();
  }

  function loadPhrases(code) {
    if (PHRASES[code] || code === 'en') return Promise.resolve(PHRASES[code] || {});
    if (!/^https?:$/.test(window.location.protocol)) return Promise.resolve({});
    PHRASES[code] = {};   // claim it first, so two calls cannot fetch twice
    return fetch('lang/phrases-' + code + '.json', { cache: 'no-cache' })
      .then(r => (r.ok ? r.json() : {}))
      .then(data => {
        PHRASES[code] = data || {};
        if (current() === code) translatePage(document);
        return PHRASES[code];
      })
      .catch(() => (PHRASES[code] = {}));
  }

  /** One text node in or out of the current language. */
  function translateTextNode(node) {
    if (!node.parentNode || SKIP.has(node.parentNode.tagName)) return;
    if (!ORIGINALS.has(node)) ORIGINALS.set(node, node.nodeValue);
    const english = ORIGINALS.get(node);
    const key = normalise(english);
    const translated = key && phraseFor(key);
    if (!translated) {
      if (node.nodeValue !== english) node.nodeValue = english;
      return;
    }
    const lead = english.match(/^\s*/)[0];
    const trail = english.match(/\s*$/)[0];
    node.nodeValue = lead + translated + trail;
  }

  /** Puts the English back, then applies the dictionary of the new language. */
  function translatePage(root) {
    if (applying) return;
    applying = true;
    try {
      phrases = PHRASES[current()] || {};
      const scope = root || document.body;
      if (!scope) return;
      const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
          const parent = node.parentNode;
          if (!parent || SKIP.has(parent.tagName)) return NodeFilter.FILTER_REJECT;
          if (parent.closest && parent.closest('[data-no-i18n]')) return NodeFilter.FILTER_REJECT;
          return normalise(node.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
        }
      });
      const nodes = [];
      while (walker.nextNode()) nodes.push(walker.currentNode);
      nodes.forEach(translateTextNode);
      scope.querySelectorAll('*').forEach(el => {
        if (el.closest('[data-no-i18n]') || !ATTRS.some(a => el.hasAttribute(a))) return;
        let saved = ATTR_ORIGINALS.get(el);
        if (!saved) { saved = {}; ATTR_ORIGINALS.set(el, saved); }
        ATTRS.forEach(attr => {
          if (!el.hasAttribute(attr)) return;
          if (!(attr in saved)) saved[attr] = el.getAttribute(attr);
          const key = normalise(saved[attr]);
          const translated = key && phraseFor(key);
          if (translated) el.setAttribute(attr, translated);
          else if (el.getAttribute(attr) !== saved[attr]) el.setAttribute(attr, saved[attr]);
        });
      });
      document.documentElement.lang = current();
    } finally {
      applying = false;
    }
  }

  /** Everything below the fold that appears later - admin tables, the seat map, live lists - is
      translated as it is inserted. Only the new subtrees are walked: an admin table with a few
      thousand rows would make a full pass expensive, and it would run on every poll. */
  function watchDom() {
    if (typeof MutationObserver === 'undefined' || !document.body) return;
    let pending = [];
    let queued = false;
    const observer = new MutationObserver(records => {
      if (applying || current() === 'en') return;
      records.forEach(record => record.addedNodes.forEach(node => {
        if (node.nodeType === 1 || node.nodeType === 3) pending.push(node);
      }));
      if (!pending.length || queued) return;
      queued = true;
      setTimeout(() => {
        const batch = pending;
        pending = [];
        queued = false;
        if (current() === 'en') return;
        batch.forEach(node => {
          if (node.nodeType === 3) translateTextNode(node);
          else if (node.isConnected) translatePage(node);
        });
      }, 80);
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  /** The switcher itself: three small buttons, the current one marked. */
  function switcherHtml() {
    const lang = current();
    return `<div class="lang-switch" role="group" aria-label="${t('lang.label', 'Language')}">
      ${LANGS.map(l => `<button type="button" class="lang-btn ${l.code === lang ? 'active' : ''}"
        data-lang="${l.code}" aria-pressed="${l.code === lang}" title="${l.label}">${l.short}</button>`).join('')}
    </div>`;
  }

  function bindSwitcher(root) {
    (root || document).querySelectorAll('.lang-btn').forEach(btn =>
      btn.addEventListener('click', () => setLang(btn.dataset.lang)));
  }

  return { t, apply, setLang, switcherHtml, bindSwitcher, current, loadFile, loadPhrases,
           translatePage, watchDom, LANGS };
})();

document.addEventListener('DOMContentLoaded', () => {
  I18N.apply();
  const lang = I18N.current();
  if (lang !== 'en') {
    I18N.loadFile(lang);
    /* the phrase file arrives a tick after the page, so run the pass once it is here */
    I18N.loadPhrases(lang).then(() => I18N.translatePage(document.body));
  }
  I18N.bindSwitcher();
  I18N.watchDom();
});
