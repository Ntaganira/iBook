/* ==========================================================================
   Ebook Online — Application scripts
   ========================================================================== */
(function () {
  "use strict";

  var doc = document;
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("ebook:" + k); return v === null ? d : v; } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("ebook:" + k, v); } catch (e) {} }
  };

  var body = doc.body;
  var shell = doc.querySelector(".ebook-shell");

  function ready(fn) { if (doc.readyState !== "loading") fn(); else doc.addEventListener("DOMContentLoaded", fn); }

  /* ----------------------------------------------------------------------
     Theme
  ---------------------------------------------------------------------- */
  function applyTheme(theme) {
    doc.documentElement.setAttribute("data-theme", theme);
    store.set("theme", theme);
    var btns = doc.querySelectorAll("[data-theme-toggle]");
    btns.forEach(function (b) {
      b.classList.toggle("is-dark", theme === "dark");
      var sun = b.querySelector("[data-theme-icon='sun']");
      var moon = b.querySelector("[data-theme-icon='moon']");
      if (sun) sun.style.display = theme === "dark" ? "none" : "";
      if (moon) moon.style.display = theme === "dark" ? "" : "none";
    });
  }

  function initTheme() {
    var saved = store.get("theme", null);
    var prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    applyTheme(saved || (prefersDark ? "dark" : "light"));
  }

  /* ----------------------------------------------------------------------
     Sidebar
  ---------------------------------------------------------------------- */
  function initSidebar() {
    var toggleBtn = doc.querySelector("[data-sidebar-toggle]");
    var mobileBtn = doc.querySelector("[data-sidebar-mobile-toggle]");
    var backdropsToClose = doc.querySelectorAll("[data-overlay-close]");

    function isMobile() { return window.innerWidth <= 860; }

    if (toggleBtn) {
      toggleBtn.addEventListener("click", function () {
        if (shell) shell.classList.toggle("sidebar-collapsed");
      });
    }
    if (mobileBtn) {
      mobileBtn.addEventListener("click", function () {
        if (shell) {
          shell.classList.toggle("sidebar-mobile-open");
          var backdrop = doc.querySelector(".sidebar-overlay");
          if (backdrop) backdrop.classList.toggle("show", shell.classList.contains("sidebar-mobile-open"));
        }
      });
    }
    backdropsToClose.forEach(function (el) {
      el.addEventListener("click", function () {
        if (shell) shell.classList.remove("sidebar-mobile-open");
        var backdrop = doc.querySelector(".sidebar-overlay");
        if (backdrop) backdrop.classList.remove("show");
      });
    });

    // Submenu toggles
    doc.querySelectorAll(".sidebar-group[data-submenu]").forEach(function (group) {
      var toggle = group.querySelector(".sidebar-menu-item");
      if (!toggle) return;
      toggle.addEventListener("click", function () {
        if (isMobile() || !shell || !shell.classList.contains("sidebar-collapsed")) {
          group.classList.toggle("open");
          var expanded = group.classList.contains("open");
          toggle.setAttribute("aria-expanded", expanded ? "true" : "false");
        }
      });
    });

    // Default: open group containing active item
    doc.querySelectorAll(".sidebar-group").forEach(function (g) {
      if (g.querySelector(".sidebar-submenu-item.active, .sidebar-menu-item.active")) {
        g.classList.add("open");
      }
    });

    // Collapsed tooltip placement
    if (shell) {
      shell.addEventListener("mouseout", function () {
        var tip = doc.querySelector(".sidebar-tooltip");
        if (tip) { tip.style.opacity = "0"; tip.style.display = "none"; }
      });
    }
  }

  /* ----------------------------------------------------------------------
     Dropdowns
  ---------------------------------------------------------------------- */
  function closeAllDropdowns() {
    doc.querySelectorAll(".dropdown.open").forEach(function (d) { d.classList.remove("open"); });
  }

  function initDropdowns() {
    doc.addEventListener("click", function (e) {
      var trigger = e.target.closest("[data-dropdown-toggle]");
      if (trigger) {
        e.stopPropagation();
        var parent = trigger.closest(".dropdown") || trigger.parentElement;
        var wasOpen = parent.classList.contains("open");
        closeAllDropdowns();
        if (!wasOpen) parent.classList.add("open");
        return;
      }
      if (!e.target.closest(".dropdown")) {
        closeAllDropdowns();
        closeOverlays(".quick-create-menu");
        closeOverlays(".command-palette");
      }
    });
    doc.addEventListener("keydown", function (e) { if (e.key === "Escape") { closeAllDropdowns(); closeAllModals(); closeAllDrawers(); } });
  }

  /* ----------------------------------------------------------------------
     Modals
  ---------------------------------------------------------------------- */
  function openModal(modal) {
    if (!modal) return;
    modal.classList.add("show");
    doc.body.style.overflow = "hidden";
    var autoFocus = modal.querySelector("[data-autofocus]");
    if (autoFocus) setTimeout(function () { autoFocus.focus(); }, 150);
  }
  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove("show");
    if (!doc.querySelector(".modal-backdrop.show") && !doc.querySelector(".drawer-bg.show")) {
      doc.body.style.overflow = "";
    }
  }
  function closeAllModals() { doc.querySelectorAll(".modal-backdrop.show").forEach(closeModal); }

  function initModals() {
    doc.addEventListener("click", function (e) {
      var opener = e.target.closest("[data-open-modal]");
      if (opener) {
        e.preventDefault();
        var target = doc.getElementById(opener.getAttribute("data-open-modal"));
        openModal(target);
        return;
      }
      var closer = e.target.closest("[data-close-modal]");
      if (closer) {
        var modal = closer.closest(".modal-backdrop");
        closeModal(modal);
      }
      if (e.target.classList && e.target.classList.contains("modal-backdrop")) {
        closeModal(e.target);
      }
    });
  }

  /* ----------------------------------------------------------------------
     Drawers
  ---------------------------------------------------------------------- */
  function openDrawer(drawer) {
    if (!drawer) return;
    drawer.classList.add("show");
    var bg = doc.querySelector(".drawer-bg[data-drawer-for='" + drawer.id + "']");
    if (bg) bg.classList.add("show"); else doc.body.style.overflow = "hidden";
  }
  function closeDrawer(drawer) {
    if (!drawer) return;
    drawer.classList.remove("show");
    var bg = doc.querySelector(".drawer-bg[data-drawer-for='" + drawer.id + "']");
    if (bg) bg.classList.remove("show");
    if (!doc.querySelector(".modal-backdrop.show") && !doc.querySelector(".drawer-bg.show")) {
      doc.body.style.overflow = "";
    }
  }
  function closeAllDrawers() { doc.querySelectorAll(".drawer.show").forEach(closeDrawer); }

  function initDrawers() {
    doc.addEventListener("click", function (e) {
      var opener = e.target.closest("[data-open-drawer]");
      if (opener) {
        e.preventDefault();
        var target = doc.getElementById(opener.getAttribute("data-open-drawer"));
        openDrawer(target);
        return;
      }
      var closer = e.target.closest("[data-close-drawer]");
      if (closer) {
        var drawer = closer.closest(".drawer-bg") || closer.closest(".drawer") || closer.closest(".drawer-bg [data-drawer-for]");
        var d = closer.closest(".drawer");
        if (d) closeDrawer(d);
        else {
          var drawerEl = null;
          if (closer.classList.contains("drawer-bg")) { drawerEl = doc.getElementById(closer.getAttribute("data-drawer-for")); }
          if (drawerEl) closeDrawer(drawerEl);
        }
      }
      if (e.target.classList && e.target.classList.contains("drawer-bg")) {
        var drawerEl = doc.getElementById(e.target.getAttribute("data-drawer-for"));
        if (drawerEl) closeDrawer(drawerEl);
      }
    });
  }

  /* ----------------------------------------------------------------------
     Toasts
  ---------------------------------------------------------------------- */
  function showToast(title, desc, type) {
    var container = doc.getElementById("toastContainer") || buildToastContainer();
    type = type || "info";
    var icons = { success: "check-circle", error: "alert-circle", warning: "alert-triangle", info: "info" };
    var toast = doc.createElement("div");
    toast.className = "toast toast--" + type;
    toast.setAttribute("role", "status");
    toast.innerHTML =
      '<span class="toast-icon ebook-icon ebook-icon--lg">' + svgById(icons[type] || "info") + '</span>' +
      '<div class="toast-body flex-1">' +
      (title ? '<div class="toast-title"></div>' : "") +
      (desc ? '<div class="toast-desc"></div>' : "") +
      '</div>' +
      '<button class="toast-close" aria-label="Close"><svg viewBox="0 0 24 24" width="14" height="14"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg></button>';
    if (title) toast.querySelector(".toast-title").textContent = title;
    if (desc) toast.querySelector(".toast-desc").textContent = desc;
    container.appendChild(toast);
    var timer = setTimeout(function () { dismissToast(toast); }, 4500);
    toast.querySelector(".toast-close").addEventListener("click", function () { clearTimeout(timer); dismissToast(toast); });
    return toast;
  }
  function buildToastContainer() {
    var c = doc.createElement("div");
    c.className = "toast-container";
    c.id = "toastContainer";
    body.appendChild(c);
    return c;
  }
  function dismissToast(toast) {
    if (!toast || toast.classList.contains("is-leaving")) return;
    toast.classList.add("is-leaving");
    setTimeout(function () { toast.remove(); }, 250);
  }
  function svgById(id) {
    var use = doc.createElement("use");
    use.setAttributeNS("http://www.w3.org/1999/xlink", "href", "#icon-" + id);
    var svg = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.appendChild(use);
    svg.setAttribute("width", "100%");
    svg.setAttribute("height", "100%");
    return svg.outerHTML.replace("<use", '<use href="#icon-' + id + '"');
  }

  /* ----------------------------------------------------------------------
     Command palette (global search)
  ---------------------------------------------------------------------- */
  function openPalette() {
    var palette = doc.getElementById("commandPalette");
    if (!palette) return;
    palette.classList.add("show");
    doc.body.style.overflow = "hidden";
    var input = palette.querySelector(".command-input");
    if (input) setTimeout(function () { input.focus(); }, 150);
  }
  function closePalette() {
    var palette = doc.getElementById("commandPalette");
    if (!palette) return;
    palette.classList.remove("show");
    if (!doc.querySelector(".modal-backdrop.show") && !doc.querySelector(".drawer-bg.show")) doc.body.style.overflow = "";
  }

  function initPalette() {
    var searchBtns = doc.querySelectorAll("[data-open-search]");
    searchBtns.forEach(function (b) { b.addEventListener("click", openPalette); });
    var palette = doc.getElementById("commandPalette");
    if (!palette) return;
    palette.addEventListener("click", function (e) {
      if (e.target.classList.contains("command-palette")) closePalette();
      var close = e.target.closest("[data-close-search]");
      if (close) closePalette();
    });
    var input = palette.querySelector(".command-input");
    if (input) {
      var items = palette.querySelectorAll(".command-result-item");
      input.addEventListener("keydown", function (e) {
        if (e.key === "ArrowDown" || e.key === "ArrowUp") {
          e.preventDefault();
          var visible = Array.prototype.filter.call(items, function (el) { return el.style.display !== "none"; });
          if (!visible.length) return;
          var idx = visible.indexOf(palette.querySelector(".is-highlighted"));
          idx = e.key === "ArrowDown" ? (idx + 1) % visible.length : (idx - 1 + visible.length) % visible.length;
          visible.forEach(function (el) { el.classList.remove("is-highlighted"); });
          visible[idx].classList.add("is-highlighted");
          visible[idx].scrollIntoView({ block: "nearest" });
        } else if (e.key === "Enter") {
          var highlighted = palette.querySelector(".is-highlighted");
          if (highlighted && highlighted.href) window.location.href = highlighted.href;
        }
        filterPalette(input.value);
      });
      items.forEach(function (it) {
        it.addEventListener("mouseenter", function () {
          items.forEach(function (el) { el.classList.remove("is-highlighted"); });
          it.classList.add("is-highlighted");
        });
      });
    }
    function filterPalette(q) {
      var sections = palette.querySelectorAll(".command-sections");
      sections.forEach(function (section) {
        var anyVisible = false;
        section.querySelectorAll(".command-result-item").forEach(function (it) {
          var hay = (it.textContent || "").toLowerCase();
          var match = !q || hay.indexOf(q.toLowerCase()) !== -1;
          it.style.display = match ? "" : "none";
          if (match) anyVisible = true;
        });
        section.style.display = anyVisible ? "" : "none";
      });
      var empty = palette.querySelector(".command-empty");
      if (empty) empty.style.display = Array.from(sections).some(function (s) { return s.style.display !== "none"; }) ? "none" : "";
    }
  }

  /* ----------------------------------------------------------------------
     Quick create
  ---------------------------------------------------------------------- */
  function initQuickCreate() {
    var menu = doc.getElementById("quickCreateMenu");
    if (!menu) return;
    var openers = doc.querySelectorAll("[data-open-quick-create]");
    openers.forEach(function (b) {
      b.addEventListener("click", function () {
        var isOpen = menu.classList.contains("show");
        doc.querySelectorAll(".quick-create-menu.show").forEach(function (m) { m.classList.remove("show"); });
        if (!isOpen) menu.classList.add("show");
      });
    });
    var input = menu.querySelector("input");
    if (input) {
      input.addEventListener("input", function () {
        var q = input.value.toLowerCase();
        menu.querySelectorAll(".quick-create-item").forEach(function (it) {
          it.style.display = it.textContent.toLowerCase().indexOf(q) === -1 ? "none" : "";
        });
        menu.querySelectorAll(".quick-create-group").forEach(function (g) {
          var anyVisible = Array.prototype.some.call(g.nextElementSibling ? [g.nextElementSibling] : [], function () { return true; });
          g.style.display = anyVisible ? "" : "none";
        });
      });
      window.addEventListener("keydown", function (e) {
        if (e.key === "/" && document.activeElement && document.activeElement.tagName !== "INPUT" && document.activeElement.tagName !== "TEXTAREA") {
          return; // no-op guard
        }
      });
    }
  }

  function closeOverlays(cls) {
    doc.querySelectorAll(cls).forEach(function (el) { el.classList.remove("show"); });
  }

  /* ----------------------------------------------------------------------
     Tabs
  ---------------------------------------------------------------------- */
  function initTabs() {
    doc.querySelectorAll("[data-tab]").forEach(function (tab) {
      tab.addEventListener("click", function () {
        var container = tab.closest("[data-tabs]") || doc;
        container.querySelectorAll("[data-tab]").forEach(function (t) { t.classList.remove("active"); });
        tab.classList.add("active");
        var targetSelector = tab.getAttribute("data-tab");
        doc.querySelectorAll("[data-tab-panel]").forEach(function (p) {
          p.classList.toggle("d-none", p.getAttribute("data-tab-panel") !== targetSelector);
        });
      });
    });
  }

  /* ----------------------------------------------------------------------
     Alert dismiss
  ---------------------------------------------------------------------- */
  function initAlerts() {
    doc.addEventListener("click", function (e) {
      var close = e.target.closest("[data-alert-dismiss]");
      if (close) {
        var alert = close.closest(".alert");
        if (alert) alert.style.display = "none";
      }
    });
  }

  /* ----------------------------------------------------------------------
     Table interactions (select all)
  ---------------------------------------------------------------------- */
  function initTables() {
    doc.addEventListener("change", function (e) {
      if (e.target.hasAttribute("data-select-all")) {
        var table = e.target.closest("table");
        if (!table) return;
        var checked = e.target.checked;
        table.querySelectorAll("tbody input[type='checkbox']").forEach(function (cb) { cb.checked = checked; });
      }
    });
  }

  /* ----------------------------------------------------------------------
     Number formatting (helper for local count-up)
  ---------------------------------------------------------------------- */
  function formatNumber(n) {
    return new Intl.NumberFormat(navigator.language || "en-US", { maximumFractionDigits: 0 }).format(n);
  }

  /* ----------------------------------------------------------------------
     Charts (Chart.js)
  ---------------------------------------------------------------------- */
  var defaultChartColors = function () {
    var cs = getComputedStyle(doc.documentElement);
    return {
      text: cs.getPropertyValue("--text-secondary").trim() || "#607084",
      grid: cs.getPropertyValue("--border").trim() || "#e0e5ec",
      c1: cs.getPropertyValue("--chart-1").trim() || "#0b5679",
      c2: cs.getPropertyValue("--chart-2").trim() || "#1f9d61",
      c3: cs.getPropertyValue("--chart-3").trim() || "#e89a1c",
      c4: cs.getPropertyValue("--chart-4").trim() || "#d94233",
      c5: cs.getPropertyValue("--chart-5").trim() || "#6d6ff0",
      c6: cs.getPropertyValue("--chart-6").trim() || "#16a0b8"
    };
  };

  function buildBaseOptions(scales, extra) {
    var colors = defaultChartColors();
    var o = {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: { legend: { display: false } },
      scales: scales || {
        x: { grid: { color: colors.grid, drawBorder: false }, ticks: { color: colors.text, font: { size: 11 } } },
        y: { grid: { color: colors.grid, drawBorder: false }, ticks: { color: colors.text, font: { size: 11 }, maxTicksLimit: 6 } }
      }
    };
    return Object.assign({}, extra, o);
  }

  function initCharts() {
    if (typeof Chart === "undefined") return;
    var colors = defaultChartColors();
    var ctx;

    // Revenue vs Expenses (line/area)
    ctx = doc.getElementById("chartRevenueExpenses");
    if (ctx) {
      new Chart(ctx, {
        type: "line",
        data: {
          labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
          datasets: [
            { label: "Revenue", data: [18.2, 22.5, 19.8, 26.4, 29.1, 24.6, 31.2, 28.9, 33.4, 30.1, 36.8, 41.6], borderColor: colors.c1, backgroundColor: hexToRgba(colors.c1, 0.12), fill: true, tension: 0.4, borderWidth: 2.5, pointRadius: 0 },
            { label: "Expenses", data: [11.4, 13.2, 12.1, 15.8, 14.2, 16.5, 15.9, 18.1, 17.3, 19.4, 20.2, 21.8], borderColor: colors.c3, backgroundColor: hexToRgba(colors.c3, 0.08), fill: true, tension: 0.4, borderWidth: 2.5, pointRadius: 0 }
          ]
        },
        options: buildBaseOptions(null, {})
      });
    }

    // Cash flow
    ctx = doc.getElementById("chartCashFlow");
    if (ctx) {
      new Chart(ctx, {
        type: "bar",
        data: {
          labels: ["Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
          datasets: [
            { label: "Inflow", data: [42.8, 45.1, 47.6, 49.2, 52.4, 55.9], backgroundColor: colors.c2, borderRadius: 6, yAxisID: "y" },
            { label: "Outflow", data: [36.4, 38.2, 40.5, 41.9, 43.7, 45.1], backgroundColor: colors.c4, borderRadius: 6, yAxisID: "y" }
          ]
        },
        options: buildBaseOptions({
          x: { grid: { display: false }, ticks: { color: colors.text } },
          y: { grid: { color: colors.grid }, ticks: { color: colors.text, callback: function (v) { return "RWF " + v + "M"; } } }
        }, {})
      });
    }

    // Receivables aging (doughnut)
    ctx = doc.getElementById("chartAging");
    if (ctx) {
      new Chart(ctx, {
        type: "doughnut",
        data: {
          labels: ["Current", "1–30 days", "31–60 days", "61–90 days", "90+ days"],
          datasets: [{ data: [12.4, 6.8, 3.2, 1.9, 1.1], backgroundColor: [colors.c2, colors.c6, colors.c3, colors.c5, colors.c4], borderWidth: 2, borderColor: "transparent" }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: "68%", plugins: { legend: { position: "bottom", labels: { color: colors.text, usePointStyle: true, boxWidth: 8, font: { size: 11 } } } } }
      });
    }

    // Profit trend
    ctx = doc.getElementById("chartProfit");
    if (ctx) {
      new Chart(ctx, {
        type: "line",
        data: {
          labels: ["Q1", "Q2", "Q3", "Q4"],
          datasets: [{ label: "Net profit", data: [4.2, 6.1, 7.8, 10.4], borderColor: colors.c2, backgroundColor: hexToRgba(colors.c2, 0.14), fill: true, tension: 0.4, borderWidth: 2.5, pointBackgroundColor: colors.c2, pointRadius: 3 }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { x: { grid: { display: false } }, y: { grid: { color: colors.grid } } } }
      });
    }

    // Expense breakdown (pie)
    ctx = doc.getElementById("chartExpenses");
    if (ctx) {
      new Chart(ctx, {
        type: "pie",
        data: {
          labels: ["COGS", "Salaries", "Rent", "Transport", "Marketing", "Utilities"],
          datasets: [{ data: [34, 28, 12, 9, 8, 9], backgroundColor: [colors.c1, colors.c2, colors.c3, colors.c5, colors.c6, colors.c4], borderWidth: 2, borderColor: "transparent" }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: "bottom", labels: { color: colors.text, usePointStyle: true, boxWidth: 8, font: { size: 11 } } } } }
      });
    }

    // KPI sparklines
    var sparkConfigs = {
      sparkRevenue: { c: colors.c1, data: [18, 20, 19, 24, 27, 25, 29, 31, 30, 32, 31, 31] },
      sparkExpenses: { c: colors.c3, data: [14, 15, 13, 16, 15, 17, 16, 18, 19, 18, 19, 18] },
      sparkProfit: { c: colors.c2, data: [4, 5, 6, 8, 12, 9, 11, 13, 11, 13, 12, 13] },
      sparkCash: { c: colors.c6, data: [20, 19, 21, 20, 22, 21, 23, 22, 22, 21, 22, 21] },
      sparkAR: { c: colors.c5, data: [21, 22, 21, 23, 24, 25, 24, 26, 25, 27, 26, 25] },
      sparkAP: { c: colors.c4, data: [16, 15, 14, 15, 13, 14, 13, 12, 13, 12, 11, 13] },
      sparkInventory: { c: colors.secondary, data: [8, 8, 9, 8, 9, 10, 9, 10, 9, 10, 9, 10] }
    };
    Object.keys(sparkConfigs).forEach(function (id) {
      var el = doc.getElementById(id);
      if (!el) return;
      var cfg = sparkConfigs[id];
      new Chart(el, {
        type: "line",
        data: { labels: Array.apply(null, new Array(cfg.data.length)).map(function (_, i) { return i + 1; }), datasets: [{ data: cfg.data, borderColor: cfg.c, backgroundColor: hexToRgba(cfg.c, 0.08), fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { enabled: false } }, elements: { point: { radius: 0 } }, scales: { x: { display: false }, y: { display: false } } }
      });
    });
  }

  function hexToRgba(hex, alpha) {
    var m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex || "");
    if (!m) return "rgba(0,0,0," + alpha + ")";
    var r = parseInt(m[1], 16), g = parseInt(m[2], 16), b = parseInt(m[3], 16);
    return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
  }

  /* ----------------------------------------------------------------------
     Date range preset chips (report forms)
  ---------------------------------------------------------------------- */
  function initDatePresets() {
    doc.querySelectorAll("[data-date-preset]").forEach(function (chip) {
      chip.addEventListener("click", function () {
        doc.querySelectorAll("[data-date-preset]").forEach(function (c) { c.classList.remove("active"); });
        chip.classList.add("active");
      });
    });
  }

  /* ----------------------------------------------------------------------
     Autosize textareas
  ---------------------------------------------------------------------- */
  function initAutosize() {
    doc.querySelectorAll("[data-autosize]").forEach(function (ta) {
      ta.addEventListener("input", function () {
        ta.style.height = "auto";
        ta.style.height = ta.scrollHeight + "px";
      });
    });
  }

  /* ----------------------------------------------------------------------
     Copy to clipboard
  ---------------------------------------------------------------------- */
  function initCopy() {
    doc.addEventListener("click", function (e) {
      var btn = e.target.closest("[data-copy]");
      if (!btn) return;
      var text = btn.getAttribute("data-copy");
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () { showToast("Copied", text + " copied to clipboard", "success"); });
      }
    });
  }

  /* ----------------------------------------------------------------------
     Global keyboard shortcuts
  ---------------------------------------------------------------------- */
  function initShortcuts() {
    var paletteOpen = false;
    doc.addEventListener("keydown", function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        var palette = doc.getElementById("commandPalette");
        if (palette) { palette.classList.contains("show") ? closePalette() : openPalette(); }
      }
});
    }

  /* ----------------------------------------------------------------------
     Boot
  ---------------------------------------------------------------------- */
    ready(function () {
      initTheme();
      initSidebar();
      initDropdowns();
      initModals();
      initDrawers();
      initPalette();
      initQuickCreate();
      initTabs();
      initAlerts();
      initTables();
      initCharts();
      initDatePresets();
      initAutosize();
      initCopy();
      initShortcuts();

      // Theme toggle delegation
      doc.addEventListener("click", function (e) {
        var t = e.target.closest("[data-theme-toggle]");
        if (t) {
          var current = doc.documentElement.getAttribute("data-theme") || "light";
          applyTheme(current === "dark" ? "light" : "dark");
        }
      });

      // Expose toast API
      window.Ebook = {
        toast: showToast,
        openModal: openModal,
        closeModal: closeModal,
        openDrawer: openDrawer,
        closeDrawer: closeDrawer
      };
    });
})();