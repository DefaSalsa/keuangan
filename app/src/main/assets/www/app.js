/* Keuangan — tampilan HTML (Tailwind + Font Awesome). Data dan login ada di Kotlin lewat objek `native`. */
(function () {
  'use strict';

  var N = window.native;
  var state = { email: null, debts: [], goals: [], cash: [] };
  var ui = { tab: 0, filter: 0, enter: true };
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
  var EASE = 'cubic-bezier(.2,.8,.2,1)';

  // ------------------------------------------------------------ util

  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function dots(n) { return String(Math.abs(Math.round(n))).replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }
  function rp(n) { return (n < 0 ? '-' : '') + 'Rp ' + dots(n); }
  function dateId(ts) {
    var d = new Date(ts);
    return d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
  }
  function num(s) { var d = String(s || '').replace(/\D/g, ''); return d ? parseInt(d, 10) : 0; }
  function sum(a, f) { return a.reduce(function (t, x) { return t + f(x); }, 0); }
  function saved(g) { return sum(g.history, function (h) { return h.amount; }); }
  function pct(g) { return g.target <= 0 ? 0 : Math.max(0, Math.min(100, Math.floor(saved(g) * 100 / g.target))); }
  function reached(g) { return g.target > 0 && saved(g) >= g.target; }
  function eta(g) {
    if (reached(g) || g.daily <= 0) return null;
    var days = Math.ceil((g.target - saved(g)) / g.daily);
    return days + ' hari lagi • ' + dateId(Date.now() + days * 86400000);
  }
  function call(fn) {
    try { var r = fn(); if (typeof r === 'string' && r) { state = JSON.parse(r); } return true; }
    catch (e) { toast('Gagal menyimpan: ' + (e && e.message ? e.message : e), 'err'); return false; }
  }

  // ------------------------------------------------------------ toast, sheet, konfeti

  var layer = $('#layer');
  var sheets = [];

  function toast(msg, kind) {
    var t = document.createElement('div');
    t.className = 'animate-toast-in pointer-events-none absolute left-1/2 top-4 z-50 flex max-w-[86%] items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold shadow-xl backdrop-blur ' +
      (kind === 'err' ? 'border-neg/40 bg-neg/20 text-neg' : 'border-brand/40 bg-brand/25 text-white');
    t.innerHTML = '<i class="fa-solid ' + (kind === 'err' ? 'fa-circle-exclamation' : 'fa-circle-check') + '"></i><span>' + esc(msg) + '</span>';
    layer.appendChild(t);
    setTimeout(function () {
      t.style.transition = 'opacity .3s, transform .3s';
      t.style.opacity = '0';
      t.style.transform = 'translate(-50%,-10px)';
      setTimeout(function () { t.remove(); }, 320);
    }, 2400);
  }

  function confetti() {
    var colors = ['#2979FF', '#5C9DFF', '#2ECC71', '#FFFFFF', '#FFD166'];
    for (var i = 0; i < 36; i++) {
      var p = document.createElement('div');
      var size = 6 + Math.random() * 6;
      p.className = 'pointer-events-none absolute left-1/2 top-[55%] z-50';
      p.style.cssText = 'width:' + size + 'px;height:' + size * 0.6 + 'px;background:' + colors[i % colors.length] + ';border-radius:2px';
      layer.appendChild(p);
      var ang = (Math.random() * 140 + 20) * Math.PI / 180;
      var dist = 120 + Math.random() * 190;
      var dx = Math.cos(ang) * dist * (Math.random() < 0.5 ? -1 : 1);
      var dy = -Math.sin(ang) * dist;
      var a = p.animate([
        { transform: 'translate(0,0) rotate(0deg)', opacity: 1 },
        { transform: 'translate(' + dx + 'px,' + dy + 'px) rotate(' + (Math.random() * 540) + 'deg)', opacity: 1, offset: 0.55 },
        { transform: 'translate(' + dx * 1.15 + 'px,' + (dy + 240) + 'px) rotate(' + (Math.random() * 900) + 'deg)', opacity: 0 }
      ], { duration: 1300 + Math.random() * 500, easing: 'cubic-bezier(.2,.7,.3,1)' });
      a.onfinish = (function (el) { return function () { el.remove(); }; })(p);
    }
  }

  /**
   * Sheet dari bawah. buttons: [{label, kind: 'primary'|'ghost'|'danger', fn(sheet) -> false untuk tetap terbuka}]
   */
  function openSheet(title, bodyHtml, buttons, onMount) {
    var wrap = document.createElement('div');
    wrap.className = 'pointer-events-auto absolute inset-0 flex items-end';
    wrap.innerHTML =
      '<div class="bd absolute inset-0 animate-fade-in bg-black/65 backdrop-blur-sm"></div>' +
      '<div class="pn animate-sheet-in relative max-h-[88%] w-full overflow-y-auto rounded-t-[28px] border border-b-0 border-line bg-sheet px-5 pb-5 pt-3 shadow-2xl">' +
      '<div class="mx-auto mb-3 h-1 w-10 rounded-full bg-line"></div>' +
      (title ? '<h2 class="mb-3 text-[18px] font-bold">' + esc(title) + '</h2>' : '') +
      '<div class="body">' + bodyHtml + '</div>' +
      '<div class="ft mt-4 flex gap-3"></div></div>';
    layer.appendChild(wrap);

    var sh = { el: wrap, pn: $('.pn', wrap), body: $('.body', wrap), closed: false };
    sh.close = function (after) {
      if (sh.closed) return;
      sh.closed = true;
      sheets = sheets.filter(function (s) { return s !== sh; });
      var bd = $('.bd', wrap);
      bd.classList.remove('animate-fade-in'); bd.classList.add('animate-fade-out');
      sh.pn.classList.remove('animate-sheet-in'); sh.pn.classList.add('animate-sheet-out');
      var ae = document.activeElement; if (ae && ae.blur) ae.blur();
      setTimeout(function () { wrap.remove(); if (after) after(); }, 220);
    };
    sh.shake = function () {
      sh.pn.classList.remove('animate-shake'); void sh.pn.offsetWidth; sh.pn.classList.add('animate-shake');
    };
    $('.bd', wrap).addEventListener('click', function () { sh.close(); });

    var ft = $('.ft', wrap);
    (buttons || []).forEach(function (b) {
      var btn = document.createElement('button');
      var cls = b.kind === 'primary' ? 'bg-brand text-white shadow-lg shadow-brand/30'
        : b.kind === 'danger' ? 'bg-neg text-white shadow-lg shadow-neg/30'
          : 'border border-line text-note';
      btn.className = 'press flex-1 rounded-2xl py-3.5 text-[15px] font-bold ' + cls;
      btn.textContent = b.label;
      btn.addEventListener('click', function () {
        var r = b.fn ? b.fn(sh) : undefined;
        if (r !== false && b.keep !== true) sh.close();
      });
      ft.appendChild(btn);
    });
    if (!buttons || !buttons.length) ft.remove();

    sheets.push(sh);
    if (onMount) onMount(sh);
    return sh;
  }

  /** Daftar pilihan (ubah, hapus, dst.). Pilihan dijalankan setelah sheet menutup. */
  function optionsSheet(title, subtitle, opts) {
    var html = (subtitle ? '<p class="-mt-2 mb-3 text-[13px] text-mute">' + esc(subtitle) + '</p>' : '') +
      '<div class="flex flex-col gap-2">' + opts.map(function (o, i) {
        return '<button data-i="' + i + '" class="press flex items-center gap-3 rounded-2xl border border-line bg-surface px-4 py-3.5 text-left text-[15px] font-semibold ' + (o.danger ? 'text-neg' : 'text-white') + '">' +
          '<i class="fa-solid ' + o.icon + ' w-5 text-center ' + (o.danger ? 'text-neg' : 'text-brandlt') + '"></i>' + esc(o.label) + '</button>';
      }).join('') + '</div>';
    openSheet(title, html, [], function (sh) {
      $$('button[data-i]', sh.el).forEach(function (b) {
        b.addEventListener('click', function () {
          var o = opts[+b.getAttribute('data-i')];
          sh.close(function () { o.fn(); });
        });
      });
    });
  }

  function confirmSheet(title, msg, okLabel, onOk) {
    openSheet(title, '<p class="text-[14px] leading-relaxed text-note">' + esc(msg) + '</p>', [
      { label: 'Batal', kind: 'ghost' },
      { label: okLabel, kind: 'danger', fn: function () { onOk(); } }
    ]);
  }

  // ------------------------------------------------------------ komponen formulir

  function fieldHtml(id, icon, placeholder, opts) {
    opts = opts || {};
    return '<div class="mb-3"><div class="relative">' +
      '<i class="fa-solid ' + icon + ' pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[14px] text-mute"></i>' +
      '<input id="' + id + '" class="field" placeholder="' + esc(placeholder) + '"' +
      (opts.money ? ' inputmode="numeric" autocomplete="off"' : ' autocomplete="off"') +
      (opts.type ? ' type="' + opts.type + '"' : '') + '>' +
      '</div><p id="' + id + '_e" class="mt-1 hidden pl-1 text-[12px] font-semibold text-neg"></p></div>';
  }

  function segHtml(id, a, b, colorA, colorB) {
    return '<div id="' + id + '" class="relative mb-3 flex rounded-2xl border border-line bg-surface p-1" data-v="0">' +
      '<div class="sg absolute bottom-1 left-1 top-1 w-[calc((100%-8px)/2)] rounded-xl transition-all duration-300" style="background:' + colorA + '"></div>' +
      '<button type="button" data-n="0" class="press relative z-10 flex-1 rounded-xl py-2.5 text-[14px] font-bold text-white">' + a + '</button>' +
      '<button type="button" data-n="1" class="press relative z-10 flex-1 rounded-xl py-2.5 text-[14px] font-bold text-mute transition-colors duration-300">' + b + '</button>' +
      '<span class="hidden" data-ca="' + colorA + '" data-cb="' + colorB + '"></span></div>';
  }

  function segInit(root, id, onChange) {
    var el = $('#' + id, root);
    var meta = $('span', el);
    var pill = $('.sg', el);
    var btns = $$('button', el);
    function set(v) {
      el.setAttribute('data-v', v);
      pill.style.transform = v === 1 ? 'translateX(100%)' : 'translateX(0)';
      pill.style.background = v === 1 ? meta.getAttribute('data-cb') : meta.getAttribute('data-ca');
      btns[0].classList.toggle('text-white', v === 0); btns[0].classList.toggle('text-mute', v !== 0);
      btns[1].classList.toggle('text-white', v === 1); btns[1].classList.toggle('text-mute', v !== 1);
      if (onChange) onChange(v);
    }
    btns.forEach(function (b) { b.addEventListener('click', function () { set(+b.getAttribute('data-n')); }); });
    return { get: function () { return +el.getAttribute('data-v'); }, set: set };
  }

  function moneyBind(inp) {
    inp.addEventListener('input', function () {
      var d = inp.value.replace(/\D/g, '').slice(0, 13);
      inp.value = d ? dots(parseInt(d, 10)) : '';
    });
  }

  function fieldErr(sh, id, msg) {
    var e = $('#' + id + '_e', sh.el);
    e.textContent = msg; e.classList.remove('hidden');
    sh.shake();
    var i = $('#' + id, sh.el); if (i) i.focus();
    return false;
  }
  function clearErr(sh) { $$('p[id$=_e]', sh.el).forEach(function (p) { p.classList.add('hidden'); }); }
  function val(sh, id) { return $('#' + id, sh.el).value; }

  // ------------------------------------------------------------ angka & bilah yang mengalir

  function countTo(node, to) {
    var from = typeof node._v === 'number' ? node._v : 0;
    node._v = to;
    if (node._raf) cancelAnimationFrame(node._raf);
    if (from === to) { node.textContent = rp(to); return; }
    var t0 = performance.now(), dur = 700;
    (function step(t) {
      var k = Math.min(1, (t - t0) / dur);
      var e = 1 - Math.pow(1 - k, 3);
      node.textContent = rp(Math.round(from + (to - from) * e));
      if (k < 1) node._raf = requestAnimationFrame(step);
    })(t0);
  }

  var lastPct = {};
  function flowBars(root) {
    $$('[data-bar]', root).forEach(function (b) {
      var id = b.getAttribute('data-bar'), to = +b.getAttribute('data-to');
      var from = lastPct[id] == null ? 0 : lastPct[id];
      lastPct[id] = to;
      b.style.width = from + '%';
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          b.style.transition = 'width .9s ' + EASE;
          b.style.width = to + '%';
        });
      });
    });
  }

  /** Kartu muncul berurutan, hanya saat layar baru ditampilkan (bukan tiap data berubah). */
  function stagger(el) {
    if (!ui.enter) return;
    $$(':scope > *', el).forEach(function (c, i) {
      c.classList.add('animate-fade-up');
      c.style.animationDelay = Math.min(i, 9) * 55 + 'ms';
    });
  }

  function summaryCard(label, id, valueCls, icon, i) {
    return '<div class="card flex-1 animate-fade-up px-3.5 py-3" style="animation-delay:' + (i * 70) + 'ms">' +
      '<p class="flex items-center gap-1.5 text-[12px] text-mute"><i class="fa-solid ' + icon + ' text-[11px]"></i>' + label + '</p>' +
      '<p id="' + id + '" class="mt-1 text-[18px] font-bold ' + valueCls + '">Rp 0</p></div>';
  }

  function emptyHtml(icon, text) {
    return '<div class="animate-fade-up flex flex-col items-center gap-3 px-6 py-14 text-center text-mute">' +
      '<div class="flex h-16 w-16 items-center justify-center rounded-full border border-line bg-surface text-[24px] text-brandlt"><i class="fa-solid ' + icon + '"></i></div>' +
      '<p class="text-[14px] leading-relaxed">' + text + '</p></div>';
  }

  // ------------------------------------------------------------ rangka tiap tab

  var S = [$('#s0'), $('#s1'), $('#s2')];

  function buildScreens() {
    S[0].innerHTML =
      '<div class="flex gap-3 pt-1">' + summaryCard('Hutang', 'd_owe', 'text-neg', 'fa-arrow-up-right-from-square', 0) + summaryCard('Piutang', 'd_owed', 'text-pos', 'fa-arrow-down-to-bracket', 1) + '</div>' +
      '<div id="d_filter" class="relative mt-4 flex rounded-2xl border border-line bg-surface p-1">' +
      '<div id="d_fp" class="absolute bottom-1 left-1 top-1 w-[calc((100%-8px)/3)] rounded-xl bg-brand shadow-lg shadow-brand/30 transition-transform duration-300" style="transition-timing-function:cubic-bezier(.3,1.25,.45,1)"></div>' +
      ['Semua', 'Hutang', 'Piutang'].map(function (t, i) {
        return '<button data-f="' + i + '" class="press relative z-10 flex-1 rounded-xl py-2 text-[13px] font-bold transition-colors duration-300">' + t + '</button>';
      }).join('') + '</div>' +
      '<div id="d_list" class="mt-3 flex flex-col gap-2.5"></div>';

    S[1].innerHTML =
      '<div class="flex gap-3 pt-1">' + summaryCard('Terkumpul', 'g_saved', 'text-white', 'fa-sack-dollar', 0) + summaryCard('Target', 'g_target', 'text-white', 'fa-bullseye', 1) + '</div>' +
      '<div id="g_list" class="mt-4 flex flex-col gap-2.5"></div>';

    S[2].innerHTML =
      '<div class="flex gap-3 pt-1">' + summaryCard('Total tunai', 'c_total', 'text-white', 'fa-money-bill-wave', 0) + summaryCard('Tunai + tabungan', 'c_all', 'text-white', 'fa-layer-group', 1) + '</div>' +
      '<p class="mb-2 mt-5 flex items-center gap-2 text-[13px] font-bold text-mute"><i class="fa-solid fa-clock-rotate-left"></i>Riwayat</p>' +
      '<div id="c_list" class="flex flex-col gap-2.5"></div>';

    $$('#d_filter button').forEach(function (b) {
      b.addEventListener('click', function () { ui.filter = +b.getAttribute('data-f'); ui.enter = true; renderDebts(); settle(); });
    });
    $('#d_list').addEventListener('click', function (e) {
      var g = e.target.closest('[data-g]'); if (g) { toggleGroup(g); return; }
      var c = e.target.closest('[data-id]'); if (c) debtOptions(+c.getAttribute('data-id'));
    });
    $('#g_list').addEventListener('click', function (e) { var c = e.target.closest('[data-id]'); if (c) goalOptions(+c.getAttribute('data-id')); });
    $('#c_list').addEventListener('click', function (e) { var c = e.target.closest('[data-id]'); if (c) cashOptions(+c.getAttribute('data-id')); });
  }

  function settle() { setTimeout(function () { ui.enter = false; }, 900); }

  // ------------------------------------------------------------ render: hutang piutang

  function startOfDay(ts) { var d = new Date(ts); return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); }
  function dueDays(d) { return Math.round((startOfDay(d.dueAt) - startOfDay(Date.now())) / 86400000); }
  function dueChip(d) {
    if (d.paid || !d.dueAt) return null;
    var n = dueDays(d);
    if (n < 0) return { text: 'Terlambat ' + (-n) + ' hari', cls: 'bg-neg/15 text-neg' };
    if (n === 0) return { text: 'Hari ini', cls: 'bg-neg/15 text-neg' };
    return { text: n + ' hari lagi', cls: 'bg-brand/15 text-brandlt' };
  }
  function toInputDate(ts) {
    var d = new Date(ts), m = d.getMonth() + 1, day = d.getDate();
    return d.getFullYear() + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day;
  }
  function fromInputDate(v) {
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v || '');
    return m ? new Date(+m[1], +m[2] - 1, +m[3]).getTime() : 0;
  }
  function gkey(name) { return String(name).trim().toLowerCase().replace(/\s+/g, ' '); }

  ui.collapsed = {};

  /** Kelompokkan per nama orang (tanpa membedakan huruf besar/kecil). Yang jatuh tempo paling dekat di atas. */
  function groupDebts(list) {
    var map = {}, order = [];
    list.forEach(function (d) {
      var k = gkey(d.name);
      if (!map[k]) { map[k] = { key: k, name: d.name.trim(), items: [] }; order.push(k); }
      map[k].items.push(d);
    });
    var groups = order.map(function (k) {
      var g = map[k];
      var open = g.items.filter(function (d) { return !d.paid; });
      g.owe = sum(open.filter(function (d) { return d.iOwe; }), function (d) { return d.amount; });
      g.owed = sum(open.filter(function (d) { return !d.iOwe; }), function (d) { return d.amount; });
      g.allPaid = open.length === 0;
      var dues = open.filter(function (d) { return d.dueAt; }).map(function (d) { return startOfDay(d.dueAt); });
      g.nextDue = dues.length ? Math.min.apply(null, dues) : Infinity;
      g.late = open.some(function (d) { return d.dueAt && dueDays(d) < 0; });
      g.items.sort(function (a, b) {
        if (a.paid !== b.paid) return a.paid ? 1 : -1;
        var da = a.dueAt || Infinity, db = b.dueAt || Infinity;
        return da !== db ? da - db : b.createdAt - a.createdAt;
      });
      return g;
    });
    groups.sort(function (a, b) {
      if (a.allPaid !== b.allPaid) return a.allPaid ? 1 : -1;
      if (a.nextDue !== b.nextDue) return a.nextDue < b.nextDue ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    return groups;
  }

  function debtRow(d) {
    var col = d.paid ? 'text-mute' : (d.iOwe ? 'text-neg' : 'text-pos');
    var chip = dueChip(d);
    return '<div data-id="' + d.id + '" class="press flex cursor-pointer items-center gap-3 border-t border-line px-4 py-3" style="' + (d.paid ? 'opacity:.6' : '') + '">' +
      '<div class="min-w-0 flex-1">' +
      '<p class="flex items-center gap-2 text-[12px] text-mute"><span class="rounded-md px-1.5 py-0.5 text-[10px] font-bold ' + (d.iOwe ? 'bg-neg/15 text-neg' : 'bg-pos/15 text-pos') + '">' + (d.iOwe ? 'HUTANG' : 'PIUTANG') + '</span>' + dateId(d.createdAt) + '</p>' +
      (d.note ? '<p class="mt-1 truncate text-[13px] text-note' + (d.paid ? ' line-through' : '') + '">' + esc(d.note) + '</p>' : '') +
      (d.dueAt && !d.paid ? '<p class="mt-1 flex flex-wrap items-center gap-1.5 text-[12px] text-note"><i class="fa-regular fa-calendar-check text-brandlt"></i>Janji bayar ' + dateId(d.dueAt) +
        ' <span class="rounded-md px-1.5 py-0.5 text-[10px] font-bold ' + chip.cls + '">' + chip.text + '</span></p>' : '') +
      '</div><div class="text-right"><p class="text-[15px] font-bold ' + col + '">' + rp(d.amount) + '</p>' +
      '<span class="mt-1 inline-block rounded-md px-2 py-0.5 text-[10px] font-bold ' + (d.paid ? 'border border-line text-mute' : 'bg-brand text-white') + '">' + (d.paid ? 'LUNAS' : 'BELUM') + '</span></div></div>';
  }

  function renderDebts() {
    var open = state.debts.filter(function (d) { return !d.paid; });
    countTo($('#d_owe'), sum(open.filter(function (d) { return d.iOwe; }), function (d) { return d.amount; }));
    countTo($('#d_owed'), sum(open.filter(function (d) { return !d.iOwe; }), function (d) { return d.amount; }));

    var pill = $('#d_fp');
    pill.style.transform = 'translateX(' + ui.filter * 100 + '%)';
    $$('#d_filter button').forEach(function (b, i) {
      b.classList.toggle('text-white', i === ui.filter); b.classList.toggle('text-mute', i !== ui.filter);
    });

    var list = state.debts.filter(function (d) { return ui.filter === 0 || (ui.filter === 1 ? d.iOwe : !d.iOwe); });
    var el = $('#d_list');
    if (!list.length) {
      el.innerHTML = emptyHtml('fa-handshake-angle', 'Belum ada catatan.<br>Ketuk <b class="text-brandlt">+</b> untuk menambah hutang atau piutang.');
      return;
    }
    el.innerHTML = groupDebts(list).map(function (g) {
      var closed = !!ui.collapsed[g.key];
      var totals = g.allPaid ? '<p class="text-[12px] font-bold text-mute">Semua lunas</p>' :
        (g.owe ? '<p class="text-[15px] font-bold text-neg">' + rp(g.owe) + '</p>' : '') +
        (g.owed ? '<p class="text-[15px] font-bold text-pos">' + rp(g.owed) + '</p>' : '');
      return '<div class="card overflow-hidden" data-gk="' + esc(g.key) + '">' +
        '<button data-g="' + esc(g.key) + '" class="press flex w-full items-center gap-3 px-4 py-3.5 text-left">' +
        '<div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand to-branddim text-[17px] font-bold">' + esc(g.name.charAt(0).toUpperCase()) + '</div>' +
        '<div class="min-w-0 flex-1"><p class="truncate text-[16px] font-bold">' + esc(g.name) + '</p>' +
        '<p class="mt-0.5 flex items-center gap-1.5 text-[12px] text-mute">' + g.items.length + ' catatan' +
        (g.late ? '<span class="rounded-md bg-neg/15 px-1.5 py-0.5 text-[10px] font-bold text-neg">Terlambat</span>' : '') + '</p></div>' +
        '<div class="text-right">' + totals + '</div>' +
        '<i class="chev fa-solid fa-chevron-down text-[12px] text-mute transition-transform duration-300" style="transform:rotate(' + (closed ? -90 : 0) + 'deg)"></i></button>' +
        '<div class="gb grid transition-[grid-template-rows] duration-300 ' + (closed ? 'grid-rows-[0fr]' : 'grid-rows-[1fr]') + '">' +
        '<div class="overflow-hidden">' + g.items.map(debtRow).join('') + '</div></div></div>';
    }).join('');
    stagger(el);
  }

  function toggleGroup(btn) {
    var k = btn.getAttribute('data-g'), card = btn.parentNode;
    var closed = !ui.collapsed[k];
    ui.collapsed[k] = closed;
    var gb = $('.gb', card);
    gb.classList.toggle('grid-rows-[0fr]', closed); gb.classList.toggle('grid-rows-[1fr]', !closed);
    $('.chev', btn).style.transform = 'rotate(' + (closed ? -90 : 0) + 'deg)';
  }

  function debtOptions(id) {
    var d = state.debts.filter(function (x) { return x.id === id; })[0]; if (!d) return;
    optionsSheet(d.name, (d.iOwe ? 'Hutang ' : 'Piutang ') + rp(d.amount) + (d.dueAt && !d.paid ? ' • janji bayar ' + dateId(d.dueAt) : ''), [
      { icon: d.paid ? 'fa-rotate-left' : 'fa-circle-check', label: d.paid ? 'Tandai belum lunas' : 'Tandai lunas', fn: function () {
        if (!d.paid) { paySheet(d); return; }
        var n = Object.assign({}, d, { paid: false });
        if (call(function () { return N.upsertDebt(JSON.stringify(n)); })) { renderDebts(); toast('Ditandai belum lunas'); }
      } },
      { icon: 'fa-pen', label: 'Ubah', fn: function () { debtForm(d); } },
      { icon: 'fa-trash', label: 'Hapus', danger: true, fn: function () {
        confirmSheet('Hapus catatan?', d.name + ' • ' + rp(d.amount), 'Hapus', function () {
          removeCard('#d_list', id, function () { if (call(function () { return N.deleteDebt(String(id)); })) renderDebts(); });
        });
      } }
    ]);
  }

  // ---- catat otomatis ke uang tunai ----

  function pref(k, v) {
    try {
      if (v === undefined) return localStorage.getItem('pref_' + k) === '1';
      localStorage.setItem('pref_' + k, v ? '1' : '0');
    } catch (e) { return false; }
    return v;
  }

  function toggleHtml(id, title, sub) {
    return '<button type="button" id="' + id + '" data-on="0" class="press mb-3 flex w-full items-center gap-3 rounded-2xl border border-line bg-surface px-4 py-3 text-left">' +
      '<i class="fa-solid fa-wallet w-5 text-center text-brandlt"></i>' +
      '<div class="min-w-0 flex-1"><p class="text-[14px] font-bold">' + title + '</p><p id="' + id + '_s" class="mt-0.5 text-[12px] leading-snug text-mute">' + sub + '</p></div>' +
      '<span class="tk relative h-6 w-11 shrink-0 rounded-full bg-line transition-colors duration-300"><span class="kn absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white transition-transform duration-300"></span></span></button>';
  }

  function toggleInit(sh, id, initial, onChange) {
    var b = $('#' + id, sh.el), tk = $('.tk', b), kn = $('.kn', b);
    function set(on) {
      b.setAttribute('data-on', on ? '1' : '0');
      tk.classList.toggle('bg-brand', on); tk.classList.toggle('bg-line', !on);
      kn.classList.toggle('translate-x-5', on);
      if (onChange) onChange(on);
    }
    b.addEventListener('click', function () { set(b.getAttribute('data-on') !== '1'); });
    set(initial);
    return { get: function () { return b.getAttribute('data-on') === '1'; }, sub: function (t) { $('#' + id + '_s', sh.el).textContent = t; } };
  }

  /** Catatan otomatis di riwayat uang tunai. delta > 0 uang masuk, < 0 uang keluar. */
  function logCash(delta, note) {
    var now = Date.now();
    var e = { id: now, amount: delta, note: note, at: now };
    var ok = call(function () { return N.upsertCash(JSON.stringify(e)); });
    if (ok) renderCash();
    return ok;
  }
  function withNote(text, d) { return d.note ? text + ' • ' + d.note : text; }

  /** Tandai lunas, dengan pilihan mencatat otomatis ke uang tunai. */
  function paySheet(d) {
    var out = d.iOwe; // melunasi hutang = uang keluar; piutang dibayar = uang masuk
    var html = '<p class="-mt-2 mb-3 text-[13px] text-mute">' + (d.iOwe ? 'Hutang ke ' : 'Piutang dari ') + esc(d.name) + ' • ' + rp(d.amount) + '</p>' +
      toggleHtml('f_cash', 'Catat ke uang tunai', out ? 'Uang tunai berkurang ' + rp(d.amount) + ', riwayat dicatat otomatis.' : 'Uang tunai bertambah ' + rp(d.amount) + ', riwayat dicatat otomatis.') +
      '<p id="f_cash_e" class="hidden pl-1 text-[12px] font-semibold text-neg"></p>';
    var tg;
    openSheet('Tandai lunas', html, [
      { label: 'Batal', kind: 'ghost' },
      { label: 'Tandai lunas', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var useCash = tg.get();
        if (useCash && out && cashTotal() < d.amount) return fieldErr(sh, 'f_cash', 'Saldo tunai tidak cukup (' + rp(cashTotal()) + '). Matikan pilihan ini atau tambah tunai dulu.');
        pref('pay_cash', useCash);
        var n = Object.assign({}, d, { paid: true });
        if (!call(function () { return N.upsertDebt(JSON.stringify(n)); })) return false;
        renderDebts();
        if (useCash) {
          logCash(out ? -d.amount : d.amount, withNote((out ? 'Bayar hutang ke ' : 'Terima piutang dari ') + d.name, d));
          toast('Lunas, uang tunai ikut dicatat');
        } else toast('Ditandai lunas');
      } }
    ], function (sh) { tg = toggleInit(sh, 'f_cash', pref('pay_cash')); });
  }

  function debtForm(d) {
    var html = fieldHtml('f_name', 'fa-user', 'Nama orang') + fieldHtml('f_amt', 'fa-coins', 'Jumlah (Rp)', { money: true }) +
      fieldHtml('f_note', 'fa-note-sticky', 'Catatan (opsional)') +
      '<p class="mb-1 pl-1 text-[12px] font-semibold text-mute">Janji dibayar kapan (opsional)</p>' + fieldHtml('f_due', 'fa-calendar-day', '', { type: 'date' }) +
      segHtml('f_type', '<i class="fa-solid fa-arrow-trend-down mr-1.5"></i>Hutang', '<i class="fa-solid fa-arrow-trend-up mr-1.5"></i>Piutang', '#FF5252', '#2ECC71') +
      (d ? '' : toggleHtml('f_cash', 'Catat ke uang tunai', ''));
    var seg, tg;
    openSheet(d ? 'Ubah catatan' : 'Tambah hutang / piutang', html, [
      { label: 'Batal', kind: 'ghost' },
      { label: 'Simpan', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var name = val(sh, 'f_name').trim(), amt = num(val(sh, 'f_amt'));
        if (!name) return fieldErr(sh, 'f_name', 'Isi nama dulu');
        if (amt <= 0) return fieldErr(sh, 'f_amt', 'Masukkan jumlah yang valid');
        var now = Date.now();
        var useCash = !d && tg.get(), owes = seg.get() === 0;
        if (useCash && !owes && cashTotal() < amt) return fieldErr(sh, 'f_amt', 'Saldo tunai tidak cukup (' + rp(cashTotal()) + '). Matikan pilihan uang tunai atau tambah tunai dulu.');
        var n = { id: d ? d.id : now, name: name, amount: amt, note: val(sh, 'f_note').trim(), createdAt: d ? d.createdAt : now, paid: d ? d.paid : false, iOwe: seg.get() === 0, dueAt: fromInputDate(val(sh, 'f_due')) };
        if (!call(function () { return N.upsertDebt(JSON.stringify(n)); })) return false;
        ui.enter = false; renderDebts();
        if (useCash) {
          pref('new_cash', true);
          logCash(owes ? amt : -amt, withNote((owes ? 'Pinjam dari ' : 'Pinjamkan ke ') + name, n));
          toast('Catatan ditambah, uang tunai ikut dicatat');
        } else {
          if (!d) pref('new_cash', false);
          toast(d ? 'Perubahan disimpan' : 'Catatan ditambah');
        }
      } }
    ], function (sh) {
      moneyBind($('#f_amt', sh.el));
      function cashHint(v) {
        if (tg) tg.sub(v === 0 ? 'Saya menerima pinjaman: uang tunai bertambah, riwayat dicatat otomatis.' : 'Saya meminjamkan uang: uang tunai berkurang, riwayat dicatat otomatis.');
      }
      seg = segInit(sh.el, 'f_type', cashHint);
      if (!d) { tg = toggleInit(sh, 'f_cash', pref('new_cash')); cashHint(0); }
      if (d) { $('#f_name', sh.el).value = d.name; $('#f_amt', sh.el).value = dots(d.amount); $('#f_note', sh.el).value = d.note; if (d.dueAt) $('#f_due', sh.el).value = toInputDate(d.dueAt); seg.set(d.iOwe ? 0 : 1); }
      else if (ui.filter === 2) seg.set(1);
    });
  }

  // ------------------------------------------------------------ render: tabungan

  function renderGoals() {
    var t = sum(state.goals, saved), tg = sum(state.goals, function (g) { return g.target; });
    countTo($('#g_saved'), t); countTo($('#g_target'), tg);
    var el = $('#g_list');
    var list = state.goals.slice().sort(function (a, b) { return (reached(a) ? 1 : 0) - (reached(b) ? 1 : 0); });
    if (!list.length) { el.innerHTML = emptyHtml('fa-piggy-bank', 'Belum ada tujuan tabungan.<br>Ketuk <b class="text-brandlt">+</b> untuk menambah.'); return; }
    el.innerHTML = list.map(function (g, i) {
      var done = reached(g), p = pct(g), e = eta(g);
      var plan = done ? '' : (e
        ? '<p class="mt-1.5 flex items-center gap-1.5 text-[12px] text-brandlt"><i class="fa-regular fa-calendar-check"></i>' + rp(g.daily) + ' per hari • ' + e + '</p>'
        : '<p class="mt-1.5 text-[12px] text-mute">Atur nominal per hari untuk melihat estimasi</p>');
      return '<div data-id="' + g.id + '" class="card press cursor-pointer px-4 py-3.5" style="border-color:' + (done ? '#2ECC71' : '#1F4FB8') + '">' +
        '<div class="flex items-center gap-3"><div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[17px] ' + (done ? 'bg-pos/15 text-pos' : 'bg-brand/15 text-brandlt') + '"><i class="fa-solid fa-' + esc(g.icon || 'bullseye') + '"></i></div>' +
        '<p class="min-w-0 flex-1 truncate text-[16px] font-bold">' + esc(g.name) + '</p>' +
        '<p class="text-[15px] font-bold ' + (done ? 'text-pos' : 'text-brandlt') + '">' + p + '%</p></div>' +
        '<div class="mt-2.5 h-2 overflow-hidden rounded-full bg-line"><div data-bar="' + g.id + '" data-to="' + p + '" class="h-full rounded-full ' + (done ? 'bg-pos' : 'bg-gradient-to-r from-brand to-brandlt') + '" style="width:0"></div></div>' +
        '<div class="mt-2 flex items-center text-[12px]"><p class="flex-1 text-mute">' + rp(saved(g)) + ' dari ' + rp(g.target) + '</p>' +
        (done ? '<p class="font-bold text-pos">Tercapai</p>' : '<p class="text-note">Kurang ' + rp(g.target - saved(g)) + '</p>') + '</div>' + plan + '</div>';
    }).join('');
    stagger(el);
    flowBars(el);
  }

  function goalById(id) { return state.goals.filter(function (x) { return x.id === id; })[0]; }

  function saveGoal(n, celebrateFrom) {
    if (!call(function () { return N.upsertGoal(JSON.stringify(n)); })) return false;
    renderGoals(); renderCash();
    if (celebrateFrom === false && reached(n)) { confetti(); toast('Target "' + n.name + '" tercapai!'); }
    return true;
  }

  function goalOptions(id) {
    var g = goalById(id); if (!g) return;
    optionsSheet(g.name, rp(saved(g)) + ' dari ' + rp(g.target), [
      { icon: 'fa-circle-plus', label: 'Setor', fn: function () { amountForm(g, true); } },
      { icon: 'fa-circle-minus', label: 'Tarik', fn: function () { amountForm(g, false); } },
      { icon: 'fa-clock-rotate-left', label: 'Riwayat', fn: function () { historySheet(g.id); } },
      { icon: 'fa-pen', label: 'Ubah', fn: function () { goalForm(g); } },
      { icon: 'fa-trash', label: 'Hapus', danger: true, fn: function () {
        confirmSheet('Hapus tujuan?', g.name + ' • terkumpul ' + rp(saved(g)) + '. Riwayat setoran ikut terhapus.', 'Hapus', function () {
          removeCard('#g_list', id, function () { if (call(function () { return N.deleteGoal(String(id)); })) { renderGoals(); renderCash(); } });
        });
      } }
    ]);
  }

  function amountForm(g, deposit) {
    var html = '<p class="-mt-2 mb-3 text-[13px] text-mute">Terkumpul ' + rp(saved(g)) + ' dari ' + rp(g.target) + '</p>' +
      fieldHtml('f_amt', 'fa-coins', 'Jumlah (Rp)', { money: true }) + fieldHtml('f_note', 'fa-note-sticky', 'Catatan (opsional)');
    openSheet((deposit ? 'Setor ke ' : 'Tarik dari ') + g.name, html, [
      { label: 'Batal', kind: 'ghost' },
      { label: deposit ? 'Setor' : 'Tarik', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var a = num(val(sh, 'f_amt'));
        if (a <= 0) return fieldErr(sh, 'f_amt', 'Masukkan jumlah yang valid');
        if (!deposit && a > saved(g)) return fieldErr(sh, 'f_amt', 'Melebihi saldo tujuan ini');
        var was = reached(g);
        var n = Object.assign({}, g, { history: g.history.concat([{ amount: deposit ? a : -a, note: val(sh, 'f_note').trim(), at: Date.now() }]) });
        if (!saveGoal(n, was)) return false;
        if (!reached(n) || was) toast(deposit ? 'Setoran dicatat' : 'Penarikan dicatat');
      } }
    ], function (sh) { moneyBind($('#f_amt', sh.el)); });
  }

  var GOAL_ICONS = ['bullseye', 'piggy-bank', 'plane', 'umbrella-beach', 'house', 'car', 'motorcycle', 'mobile-screen', 'laptop', 'graduation-cap', 'ring', 'baby',
    'gift', 'shield-halved', 'kaaba', 'bag-shopping', 'camera', 'gamepad', 'bicycle', 'briefcase', 'heart-pulse', 'couch', 'shirt', 'utensils'];

  function iconPickerHtml() {
    return '<p class="mb-2 pl-1 text-[12px] font-semibold text-mute">Logo tujuan</p><div id="f_icons" class="mb-3 grid grid-cols-6 gap-2">' +
      GOAL_ICONS.map(function (n) {
        return '<button type="button" data-ic="' + n + '" class="press flex h-11 items-center justify-center rounded-xl border border-line bg-surface text-[17px] text-note transition-colors duration-200"><i class="fa-solid fa-' + n + '"></i></button>';
      }).join('') + '</div>';
  }

  function goalForm(g) {
    var chosen = (g && g.icon) || 'bullseye';
    var html = fieldHtml('f_name', 'fa-flag', 'Nama tujuan (mis. Liburan)') + iconPickerHtml() + fieldHtml('f_target', 'fa-bullseye', 'Target (Rp)', { money: true }) +
      fieldHtml('f_daily', 'fa-calendar-day', 'Nabung per hari (Rp, opsional)', { money: true }) +
      (g ? '' : fieldHtml('f_init', 'fa-coins', 'Setoran awal (Rp, opsional)', { money: true }));
    openSheet(g ? 'Ubah tujuan' : 'Tujuan tabungan baru', html, [
      { label: 'Batal', kind: 'ghost' },
      { label: 'Simpan', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var name = val(sh, 'f_name').trim(), target = num(val(sh, 'f_target')), daily = num(val(sh, 'f_daily'));
        if (!name) return fieldErr(sh, 'f_name', 'Isi nama tujuan');
        if (target <= 0) return fieldErr(sh, 'f_target', 'Masukkan target yang valid');
        var now = Date.now(), n;
        if (g) n = Object.assign({}, g, { name: name, target: target, daily: daily, icon: chosen });
        else {
          var init = num(val(sh, 'f_init'));
          n = { id: now, name: name, target: target, createdAt: now, daily: daily, icon: chosen, history: init > 0 ? [{ amount: init, note: 'Setoran awal', at: now }] : [] };
        }
        if (!saveGoal(n, g ? reached(g) : true)) return false;
        toast(g ? 'Perubahan disimpan' : 'Tujuan ditambah');
      } }
    ], function (sh) {
      $$('input[inputmode=numeric]', sh.el).forEach(moneyBind);
      function paint() {
        $$('#f_icons button', sh.el).forEach(function (b) {
          var on = b.getAttribute('data-ic') === chosen;
          b.classList.toggle('bg-brand', on); b.classList.toggle('text-white', on); b.classList.toggle('border-brand', on);
          b.classList.toggle('bg-surface', !on); b.classList.toggle('text-note', !on); b.classList.toggle('border-line', !on);
          if (on) b.animate([{ transform: 'scale(.85)' }, { transform: 'scale(1.1)' }, { transform: 'scale(1)' }], { duration: 300, easing: 'ease-out' });
        });
      }
      $$('#f_icons button', sh.el).forEach(function (b) { b.addEventListener('click', function () { chosen = b.getAttribute('data-ic'); paint(); }); });
      paint();
      if (g) { $('#f_name', sh.el).value = g.name; $('#f_target', sh.el).value = dots(g.target); if (g.daily) $('#f_daily', sh.el).value = dots(g.daily); }
    });
  }

  function historySheet(id) {
    var g = goalById(id); if (!g) return;
    var rows = g.history.map(function (h, i) { return { h: h, i: i }; }).sort(function (a, b) { return b.h.at - a.h.at; });
    var html = rows.length ? '<div class="flex flex-col gap-2">' + rows.map(function (r) {
      var inn = r.h.amount >= 0;
      return '<button data-i="' + r.i + '" class="press flex items-center gap-3 rounded-2xl border border-line bg-surface px-4 py-3 text-left">' +
        '<div class="min-w-0 flex-1"><p class="truncate text-[14px] font-bold">' + esc(r.h.note || (inn ? 'Setoran' : 'Penarikan')) + '</p><p class="text-[12px] text-mute">' + dateId(r.h.at) + '</p></div>' +
        '<p class="text-[14px] font-bold ' + (inn ? 'text-pos' : 'text-neg') + '">' + (inn ? '+ ' : '- ') + rp(Math.abs(r.h.amount)) + '</p></button>';
    }).join('') + '</div><p class="mt-3 text-center text-[12px] text-mute">Ketuk entri untuk mengubah atau menghapus</p>'
      : '<p class="py-6 text-center text-[14px] text-mute">Belum ada riwayat.</p>';
    openSheet('Riwayat • ' + g.name, html, [{ label: 'Tutup', kind: 'ghost' }], function (sh) {
      $$('button[data-i]', sh.el).forEach(function (b) {
        b.addEventListener('click', function () {
          var i = +b.getAttribute('data-i');
          sh.close(function () { entryOptions(id, i); });
        });
      });
    });
  }

  function applyHistory(g, hist) {
    var total = sum(hist, function (h) { return h.amount; });
    if (total < 0) { toast('Gagal: saldo tujuan jadi minus', 'err'); return false; }
    return saveGoal(Object.assign({}, g, { history: hist }), true);
  }

  function entryOptions(gid, i) {
    var g = goalById(gid); if (!g || !g.history[i]) return;
    var h = g.history[i];
    optionsSheet(h.note || (h.amount >= 0 ? 'Setoran' : 'Penarikan'), (h.amount >= 0 ? '+ ' : '- ') + rp(Math.abs(h.amount)) + ' • ' + dateId(h.at), [
      { icon: 'fa-pen', label: 'Ubah', fn: function () { entryForm(gid, i); } },
      { icon: 'fa-trash', label: 'Hapus', danger: true, fn: function () {
        confirmSheet('Hapus entri?', 'Saldo tujuan menyesuaikan.', 'Hapus', function () {
          var hist = g.history.filter(function (_, k) { return k !== i; });
          if (applyHistory(g, hist)) { toast('Entri dihapus'); setTimeout(function () { historySheet(gid); }, 240); }
        });
      } }
    ]);
  }

  function entryForm(gid, i) {
    var g = goalById(gid), h = g.history[i], seg;
    var html = fieldHtml('f_amt', 'fa-coins', 'Jumlah (Rp)', { money: true }) + fieldHtml('f_note', 'fa-note-sticky', 'Catatan') +
      segHtml('f_type', '<i class="fa-solid fa-circle-plus mr-1.5"></i>Setor', '<i class="fa-solid fa-circle-minus mr-1.5"></i>Tarik', '#2ECC71', '#FF5252');
    openSheet('Ubah entri', html, [
      { label: 'Batal', kind: 'ghost' },
      { label: 'Simpan', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var a = num(val(sh, 'f_amt'));
        if (a <= 0) return fieldErr(sh, 'f_amt', 'Masukkan jumlah yang valid');
        var hist = g.history.slice();
        hist[i] = { amount: seg.get() === 0 ? a : -a, note: val(sh, 'f_note').trim(), at: h.at };
        if (!applyHistory(g, hist)) return false;
        toast('Entri diperbarui'); setTimeout(function () { historySheet(gid); }, 240);
      } }
    ], function (sh) {
      moneyBind($('#f_amt', sh.el)); seg = segInit(sh.el, 'f_type');
      $('#f_amt', sh.el).value = dots(Math.abs(h.amount)); $('#f_note', sh.el).value = h.note; seg.set(h.amount >= 0 ? 0 : 1);
    });
  }

  // ------------------------------------------------------------ render: uang

  function cashTotal() { return sum(state.cash, function (e) { return e.amount; }); }

  function renderCash() {
    var total = cashTotal();
    countTo($('#c_total'), total); countTo($('#c_all'), total + sum(state.goals, saved));
    var el = $('#c_list');
    if (!state.cash.length) { el.innerHTML = emptyHtml('fa-wallet', 'Belum ada riwayat tunai.<br>Ketuk <b class="text-brandlt">+</b> untuk mencatat uang masuk atau keluar.'); return; }
    el.innerHTML = state.cash.map(function (e, i) {
      var inn = e.amount >= 0;
      return '<div data-id="' + e.id + '" class="card press flex cursor-pointer items-center gap-3 px-4 py-3.5">' +
        '<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[15px] ' + (inn ? 'bg-pos/15 text-pos' : 'bg-neg/15 text-neg') + '"><i class="fa-solid ' + (inn ? 'fa-arrow-down' : 'fa-arrow-up') + '"></i></div>' +
        '<div class="min-w-0 flex-1"><p class="truncate text-[15px] font-bold">' + esc(e.note || (inn ? 'Uang masuk' : 'Uang keluar')) + '</p><p class="mt-0.5 text-[12px] text-mute">' + dateId(e.at) + '</p></div>' +
        '<p class="text-[15px] font-bold ' + (inn ? 'text-pos' : 'text-neg') + '">' + (inn ? '+ ' : '- ') + rp(Math.abs(e.amount)) + '</p></div>';
    }).join('');
    stagger(el);
  }

  function cashForm(e) {
    var seg;
    var html = fieldHtml('f_amt', 'fa-coins', 'Jumlah (Rp)', { money: true }) + fieldHtml('f_note', 'fa-note-sticky', 'Dari mana (mis. gaji, saldo awal)') +
      segHtml('f_type', '<i class="fa-solid fa-arrow-down mr-1.5"></i>Uang masuk', '<i class="fa-solid fa-arrow-up mr-1.5"></i>Uang keluar', '#2ECC71', '#FF5252') +
      '<p class="mb-1 flex items-center gap-2 text-[12px] text-mute"><i class="fa-solid fa-wallet"></i>Saldo tunai: ' + rp(cashTotal()) + '</p>';
    openSheet(e ? 'Ubah catatan' : 'Catat tunai', html, [
      { label: 'Batal', kind: 'ghost' },
      { label: 'Simpan', kind: 'primary', fn: function (sh) {
        clearErr(sh);
        var out = seg.get() === 1, a = num(val(sh, 'f_amt')), note = val(sh, 'f_note').trim();
        if (a <= 0) return fieldErr(sh, 'f_amt', 'Masukkan jumlah yang valid');
        if (out && !note) return fieldErr(sh, 'f_note', 'Isi untuk apa uangnya dipakai');
        var now = Date.now();
        var n = e ? Object.assign({}, e, { amount: out ? -a : a, note: note }) : { id: now, amount: out ? -a : a, note: note, at: now };
        var after = e ? cashTotal() - e.amount + n.amount : cashTotal() + n.amount;
        if (after < 0) return fieldErr(sh, 'f_amt', e ? 'Saldo tunai jadi minus' : 'Melebihi saldo tunai');
        if (!call(function () { return N.upsertCash(JSON.stringify(n)); })) return false;
        ui.enter = false; renderCash(); toast(e ? 'Perubahan disimpan' : (out ? 'Uang keluar dicatat' : 'Uang masuk dicatat'));
      } }
    ], function (sh) {
      moneyBind($('#f_amt', sh.el));
      seg = segInit(sh.el, 'f_type', function (v) { $('#f_note', sh.el).placeholder = v === 0 ? 'Dari mana (mis. gaji, saldo awal)' : 'Dipakai untuk apa (mis. makan siang)'; });
      if (e) { $('#f_amt', sh.el).value = dots(Math.abs(e.amount)); $('#f_note', sh.el).value = e.note; seg.set(e.amount >= 0 ? 0 : 1); }
    });
  }

  function cashOptions(id) {
    var e = state.cash.filter(function (x) { return x.id === id; })[0]; if (!e) return;
    optionsSheet(e.note || (e.amount >= 0 ? 'Uang masuk' : 'Uang keluar'), (e.amount >= 0 ? '+ ' : '- ') + rp(Math.abs(e.amount)) + ' • ' + dateId(e.at), [
      { icon: 'fa-pen', label: 'Ubah', fn: function () { cashForm(e); } },
      { icon: 'fa-trash', label: 'Hapus', danger: true, fn: function () {
        confirmSheet('Hapus catatan?', 'Saldo tunai ikut menyesuaikan.', 'Hapus', function () {
          if (cashTotal() - e.amount < 0) { toast('Gagal: saldo tunai jadi minus', 'err'); return; }
          removeCard('#c_list', id, function () { if (call(function () { return N.deleteCash(String(id)); })) renderCash(); });
        });
      } }
    ]);
  }

  /** Kartu menciut dan memudar sebelum benar-benar dihapus. */
  function removeCard(listSel, id, done) {
    var c = $(listSel + ' [data-id="' + id + '"]');
    if (!c) { done(); return; }
    var h = c.offsetHeight;
    c.style.overflow = 'hidden';
    var a = c.animate([
      { height: h + 'px', opacity: 1, transform: 'none', marginBottom: '0px' },
      { height: '0px', opacity: 0, transform: 'translateX(40px)', marginBottom: '-10px', paddingTop: '0px', paddingBottom: '0px' }
    ], { duration: 320, easing: 'ease-in', fill: 'forwards' });
    a.onfinish = done;
  }

  // ------------------------------------------------------------ navigasi

  var navBtns = $$('.nav');
  var pill = $('#pill');

  function moveNav(animate) {
    var b = navBtns[ui.tab];
    if (!b.offsetWidth) { requestAnimationFrame(function () { moveNav(false); }); return; }
    if (animate) pill.classList.add('pill-anim'); else pill.classList.remove('pill-anim');
    pill.style.width = b.offsetWidth + 'px';
    pill.style.transform = 'translateX(' + (b.offsetLeft + $('#navrow').offsetLeft) + 'px)';
    navBtns.forEach(function (x, i) {
      x.classList.toggle('text-white', i === ui.tab); x.classList.toggle('text-note', i !== ui.tab);
    });
  }

  function showTab(i, animate) {
    var old = ui.tab;
    ui.tab = i;
    S.forEach(function (s) { s.getAnimations().forEach(function (a) { a.cancel(); }); });
    S.forEach(function (s, n) {
      if (n === i) {
        s.classList.remove('off');
        if (animate && old !== i) {
          s.animate([{ opacity: 0, transform: 'translateX(' + (i > old ? 30 : -30) + 'px)' }, { opacity: 1, transform: 'none' }],
            { duration: 380, delay: 90, easing: EASE, fill: 'backwards' });
        }
      } else if (animate && n === old) {
        var a = s.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateX(' + (i > old ? -26 : 26) + 'px)' }],
          { duration: 170, easing: 'ease-in', fill: 'forwards' });
        a.onfinish = function () { if (ui.tab !== n) s.classList.add('off'); a.cancel(); };
      } else s.classList.add('off');
    });
    S[i].scrollTop = 0;
    ui.enter = true;
    renderAll();
    settle();
    moveNav(animate);
    var fi = $('#fabicon');
    fi.style.transform = 'rotate(' + (i * 90) + 'deg) scale(1)';
    var f = $('#fab');
    f.animate([{ transform: 'scale(.8)' }, { transform: 'scale(1.12)' }, { transform: 'scale(1)' }], { duration: 420, easing: 'ease-out' });
  }

  function addCurrent() {
    if (ui.tab === 0) debtForm(null); else if (ui.tab === 1) goalForm(null); else cashForm(null);
  }

  function renderAll() { renderDebts(); renderGoals(); renderCash(); }

  window.openTab = function (t, add) {
    sheets.slice().forEach(function (s) { s.close(); });
    showTab(t, true);
    if (add) setTimeout(addCurrent, 250);
  };

  window.handleBack = function () {
    if (sheets.length) { sheets[sheets.length - 1].close(); return true; }
    if (ui.tab !== 0 && !$('#main').classList.contains('hidden')) { showTab(0, true); return true; }
    return false;
  };

  window.onState = function (s) {
    state = s;
    if (!state.email) return;
    if (!$('#main').classList.contains('hidden')) renderAll();
  };

  // ------------------------------------------------------------ layar masuk

  function showAuth() {
    $('#main').classList.add('hidden');
    var a = $('#auth');
    a.classList.remove('hidden');
    a.innerHTML =
      '<div class="flex min-h-full flex-col justify-center py-8">' +
      '<div class="animate-pop mb-6 flex h-16 w-16 items-center justify-center rounded-[20px] bg-brand text-[28px] shadow-xl shadow-brand/40"><i class="fa-solid fa-wallet"></i></div>' +
      '<h1 class="animate-fade-up text-[34px] font-bold tracking-tight" style="animation-delay:80ms">' + esc(N.appName()) + '</h1>' +
      '<p class="animate-fade-up mb-7 mt-1.5 text-[14px] text-mute" style="animation-delay:140ms">Masuk agar datamu tersimpan aman di cloud, bukan hanya di HP.</p>' +
      '<div class="animate-fade-up" style="animation-delay:200ms">' + fieldHtml('a_email', 'fa-envelope', 'Email', { type: 'email' }) + '</div>' +
      '<div class="animate-fade-up" style="animation-delay:260ms">' + fieldHtml('a_pass', 'fa-lock', 'Password (min. 6 karakter)', { type: 'password' }) + '</div>' +
      '<p id="a_msg" class="mb-2 min-h-[18px] text-[13px] font-semibold text-neg"></p>' +
      '<button id="a_login" class="press animate-fade-up flex items-center justify-center gap-2 rounded-2xl bg-brand py-4 text-[16px] font-bold shadow-lg shadow-brand/30" style="animation-delay:320ms">Masuk</button>' +
      '<button id="a_reg" class="press animate-fade-up mt-3 rounded-2xl border border-brand py-4 text-[16px] font-bold" style="animation-delay:380ms">Daftar akun baru</button>' +
      '<button id="a_forgot" class="press animate-fade-up mt-3 py-3 text-[14px] font-bold text-brandlt" style="animation-delay:440ms">Lupa password?</button></div>';
    var busy = false;
    function msg(t, ok) { var m = $('#a_msg'); m.textContent = t; m.className = 'mb-2 min-h-[18px] text-[13px] font-semibold ' + (ok ? 'text-brandlt' : 'text-neg'); }
    function setBusy(b) {
      busy = b;
      $('#a_login').style.opacity = $('#a_reg').style.opacity = b ? '.5' : '1';
      $('#a_login').innerHTML = b ? '<i class="fa-solid fa-spinner fa-spin"></i>' : 'Masuk';
    }
    function check(needPass) {
      var em = $('#a_email').value.trim(), pw = $('#a_pass').value;
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(em)) { msg(needPass ? 'Email tidak valid.' : 'Isi email dulu, lalu ketuk "Lupa password?".'); a.firstChild.classList.remove('animate-shake'); return null; }
      if (needPass && pw.length < 6) { msg('Password minimal 6 karakter.'); return null; }
      return { em: em, pw: pw };
    }
    $('#a_login').addEventListener('click', function () { if (busy) return; var c = check(true); if (!c) return; msg(''); setBusy(true); N.login(c.em, c.pw); });
    $('#a_reg').addEventListener('click', function () { if (busy) return; var c = check(true); if (!c) return; msg(''); setBusy(true); N.register(c.em, c.pw); });
    $('#a_forgot').addEventListener('click', function () { if (busy) return; var c = check(false); if (!c) return; msg(''); setBusy(true); N.reset(c.em); });
    window.onAuth = function (kind, ok, m) {
      setBusy(false);
      if (kind === 'reset') { msg(m, ok); return; }
      if (!ok) { msg(m); return; }
      state = JSON.parse(N.state());
      a.classList.add('hidden');
      showMain();
    };
  }

  // ------------------------------------------------------------ mulai

  var mainBuilt = false;
  function showMain() {
    $('#appname').textContent = N.appName();
    $('#email').textContent = state.email || '';
    $('#auth').classList.add('hidden');
    $('#main').classList.remove('hidden');
    if (!mainBuilt) { buildScreens(); mainBuilt = true; }
    lastPct = {};
    ui.tab = 0; ui.filter = 0;
    S.forEach(function (s, n) { s.classList.toggle('off', n !== 0); });
    ui.enter = true;
    renderAll();
    settle();
    moveNav(false);
    $('#fabicon').style.transform = 'rotate(0deg)';
    var p = N.takePending();
    if (p) { var parts = p.split(','); setTimeout(function () { window.openTab(+parts[0], parts[1] === 'true'); }, 350); }
  }

  $('#fab').addEventListener('click', addCurrent);
  navBtns.forEach(function (b, i) { b.addEventListener('click', function () { if (i !== ui.tab) showTab(i, true); }); });
  var syncTimer = null;
  function syncDone(ok) {
    clearTimeout(syncTimer);
    var ic = $('#syncicon'); ic.classList.remove('fa-spin');
    $('#sync').style.opacity = '1';
    toast(ok ? 'Tersinkron dari database' : 'Gagal sinkron. Cek koneksi internet.', ok ? undefined : 'err');
  }
  window.onSync = syncDone;
  $('#sync').addEventListener('click', function () {
    var ic = $('#syncicon');
    if (ic.classList.contains('fa-spin')) return;
    ic.classList.add('fa-spin');
    $('#sync').style.opacity = '.7';
    syncTimer = setTimeout(function () { syncDone(false); }, 30000);
    try { N.sync(); } catch (e) { syncDone(false); }
  });
  $('#logout').addEventListener('click', function () {
    confirmSheet('Keluar akun?', 'Data tetap aman di cloud dan muncul lagi saat kamu masuk.', 'Keluar', function () {
      N.logout();
      state = { email: null, debts: [], goals: [], cash: [] };
      showAuth();
    });
  });
  window.addEventListener('resize', function () { moveNav(false); });

  try {
    var s0 = JSON.parse(N.state());
    state = s0;
    if (s0.email) showMain(); else showAuth();
  } catch (err) {
    document.body.insertAdjacentHTML('beforeend', '<p style="color:#FF5252;padding:20px">Gagal memuat: ' + esc(err.message) + '</p>');
  }
})();
