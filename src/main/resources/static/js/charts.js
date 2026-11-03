/* ===== Dependency-free SVG charts for the admin dashboard =====
   No external chart library is needed, so the charts also work offline and in the preview.
   Every function returns an HTML/SVG string; the caller puts it into the page. */

const Charts = {
  /** Vertical bar chart. points: [{ label, value, title? }] */
  bars(points, opts = {}) {
    const W = opts.width || 760, H = opts.height || 260, L = 64, R = 12, T = 14, B = 34;
    const iw = W - L - R, ih = H - T - B;
    const max = niceMax(Math.max(0, ...points.map(p => Number(p.value) || 0)));
    const color = opts.color || '#7c3aed';
    const n = Math.max(points.length, 1), slot = iw / n, bw = Math.max(4, slot * 0.62);
    const grid = yGrid(max, W, L, T, ih);
    const step = Math.ceil(n / 10);
    const bars = points.map((p, i) => {
      const v = Number(p.value) || 0;
      const h = max ? (v / max) * ih : 0;
      const x = L + i * slot + (slot - bw) / 2, y = T + ih - h;
      const label = i % step === 0 ? `<text x="${x + bw / 2}" y="${H - 10}" text-anchor="middle" class="axis">${esc(p.label)}</text>` : '';
      return `<rect x="${x}" y="${y}" width="${bw}" height="${Math.max(h, 0)}" rx="4" fill="${color}" opacity=".9">
                <title>${esc(p.title || (p.label + ': ' + v))}</title></rect>${label}`;
    }).join('');
    return svgWrap(W, H, grid + bars, opts.ariaLabel);
  },

  /** Line chart with an area fill. points: [{ label, value, title? }] */
  line(points, opts = {}) {
    const W = opts.width || 760, H = opts.height || 220, L = 52, R = 12, T = 14, B = 34;
    const iw = W - L - R, ih = H - T - B;
    const max = niceMax(Math.max(0, ...points.map(p => Number(p.value) || 0)));
    const color = opts.color || '#22d3ee';
    const n = points.length, step = Math.ceil(n / 10);
    const xy = points.map((p, i) => {
      const x = n <= 1 ? L + iw / 2 : L + (iw * i) / (n - 1);
      const y = T + ih - (max ? ((Number(p.value) || 0) / max) * ih : 0);
      return [x, y];
    });
    const grid = yGrid(max, W, L, T, ih);
    const path = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
    const area = n ? `${path} L${xy[n - 1][0].toFixed(1)},${T + ih} L${xy[0][0].toFixed(1)},${T + ih} Z` : '';
    const dots = xy.map(([x, y], i) => `<circle cx="${x}" cy="${y}" r="3.5" fill="${color}">
                <title>${esc(points[i].title || (points[i].label + ': ' + points[i].value))}</title></circle>`).join('');
    const labels = points.map((p, i) => i % step === 0
      ? `<text x="${xy[i][0]}" y="${H - 10}" text-anchor="middle" class="axis">${esc(p.label)}</text>` : '').join('');
    return svgWrap(W, H, grid + `<path d="${area}" fill="${color}" opacity=".15"/>
                  <path d="${path}" fill="none" stroke="${color}" stroke-width="2.5" stroke-linejoin="round"/>` +
                  dots + labels, opts.ariaLabel);
  },

  /** Donut chart. segments: [{ label, value, color }] */
  donut(segments, opts = {}) {
    const total = segments.reduce((s, x) => s + (Number(x.value) || 0), 0);
    const r = 62, c = 2 * Math.PI * r;
    let offset = 0;
    const arcs = segments.map(seg => {
      const frac = total ? (Number(seg.value) || 0) / total : 0;
      const len = frac * c;
      const arc = `<circle r="${r}" cx="90" cy="90" fill="none" stroke="${seg.color}" stroke-width="24"
                   stroke-dasharray="${len} ${c - len}" stroke-dashoffset="${-offset}" transform="rotate(-90 90 90)">
                   <title>${esc(seg.label)}: ${compact(seg.value)} (${Math.round(frac * 100)}%)</title></circle>`;
      offset += len;
      return arc;
    }).join('');
    const legend = segments.map(seg => {
      const pct = total ? Math.round(((Number(seg.value) || 0) / total) * 100) : 0;
      return `<li><i style="background:${seg.color}"></i><span>${esc(seg.label)}</span>
              <strong>${pct}%</strong><small class="muted">${esc(seg.detail || '')}</small></li>`;
    }).join('');
    const centre = total ? `<text x="90" y="88" text-anchor="middle" class="donut-num">${compact(total)}</text>
                            <text x="90" y="106" text-anchor="middle" class="axis">${esc(opts.centreLabel || 'total')}</text>` : '';
    return `<div class="donut-wrap">
              <svg viewBox="0 0 180 180" class="donut" role="img" aria-label="${esc(opts.ariaLabel || 'Donut chart')}">
                <circle r="${r}" cx="90" cy="90" fill="none" class="donut-track"/>
                ${arcs}${centre}
              </svg>
              <ul class="donut-legend">${legend || '<li class="muted">No data for this period.</li>'}</ul>
            </div>`;
  },

  /** Horizontal bars as HTML (responsive, easy to read). rows: [{ label, value, sub }] */
  hbars(rows, opts = {}) {
    if (!rows.length) return '<p class="muted">No sales in this period.</p>';
    const max = Math.max(...rows.map(r => Number(r.value) || 0), 1);
    return `<div class="hbars">${rows.map((r, i) => `
      <div class="hbar">
        <div class="hbar-head"><span>${esc(r.label)}</span><strong>${esc(opts.format ? opts.format(r.value) : r.value)}</strong></div>
        <div class="hbar-track"><div class="hbar-fill" style="width:${((Number(r.value) || 0) / max * 100).toFixed(1)}%;
             background:${opts.colors ? opts.colors[i % opts.colors.length] : '#7c3aed'}"></div></div>
        ${r.sub ? `<small class="muted">${esc(r.sub)}</small>` : ''}
      </div>`).join('')}</div>`;
  }
};

/** Y-axis grid lines and labels. Small whole-number maxima (e.g. ticket counts) get whole-number ticks,
    so the axis never shows repeated labels such as 2, 2, 1, 1. */
function yGrid(max, W, L, T, ih) {
  const ticks = Number.isInteger(max) && max <= 4
    ? Array.from({ length: max + 1 }, (_, i) => i)
    : [0, 1, 2, 3, 4].map(i => (max / 4) * i);
  return ticks.map(v => {
    const y = T + ih - (ih * v / max);
    return `<line x1="${L}" x2="${W - 12}" y1="${y}" y2="${y}" class="grid-line"/>
               <text x="${L - 8}" y="${y + 4}" text-anchor="end" class="axis">${compact(v)}</text>`;
  }).join('');
}

function svgWrap(W, H, inner, label) {
  return `<svg viewBox="0 0 ${W} ${H}" class="chart" role="img" aria-label="${esc(label || 'chart')}"
            preserveAspectRatio="xMidYMid meet">${inner}</svg>`;
}

/** Rounds an axis maximum up to a friendly number (1, 2, 2.5, 5 x 10^n). */
function niceMax(value) {
  if (value <= 0) return 1;
  const exp = Math.pow(10, Math.floor(Math.log10(value)));
  const f = value / exp;
  const nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nice * exp;
}

/** Short axis label: 1500 -> "1.5k", 2000000 -> "2M". */
function compact(v) {
  const n = Number(v) || 0;
  if (Math.abs(n) >= 1e6) return (n / 1e6).toFixed(n % 1e6 === 0 ? 0 : 1) + 'M';
  if (Math.abs(n) >= 1e3) return (n / 1e3).toFixed(n % 1e3 === 0 ? 0 : 1) + 'k';
  return String(Math.round(n));
}
